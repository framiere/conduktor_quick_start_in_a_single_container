package com.example.messaging.operator.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.store.CRDStore;
import com.example.messaging.operator.validation.OwnershipValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.ArrayList;
import okhttp3.*;
import org.junit.jupiter.api.*;

/**
 * End-to-end integration test simulating K8s API server requests
 */
@DisplayName("Webhook Integration Tests")
class WebhookIntegrationTest {

    private static WebhookServer server;
    private static CRDStore store;
    private static OkHttpClient httpClient;
    private static ObjectMapper mapper;
    private static final int PORT = 18443; // Different port for integration tests

    @BeforeAll
    static void setUpAll() throws Exception {
        store = new CRDStore();
        OwnershipValidator ownershipValidator = new OwnershipValidator(store);
        mapper = new ObjectMapper();
        WebhookValidator validator = new WebhookValidator(ownershipValidator, mapper);

        server = new WebhookServer(validator, PORT);
        server.start();

        httpClient = new OkHttpClient();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    @Test
    @DisplayName("Integration: Block Topic ownership transfer")
    void testBlockTopicOwnershipTransfer() throws Exception {
        // Simulate UPDATE request from K8s API server
        Topic oldTopic = createTopic("my-topic", "app-service-1");
        Topic newTopic = createTopic("my-topic", "hacker-service"); // Ownership change

        AdmissionReview review = new AdmissionReview();
        AdmissionRequest request = new AdmissionRequest();
        request.setUid("integration-test-001");
        request.setOperation("UPDATE");
        request.setNamespace("production");
        request.setName("my-topic");
        request.setObject(mapper.convertValue(newTopic, java.util.Map.class));
        request.setOldObject(mapper.convertValue(oldTopic, java.util.Map.class));

        UserInfo userInfo = new UserInfo();
        userInfo.setUsername("system:serviceaccount:production:hacker");
        request.setUserInfo(userInfo);

        review.setRequest(request);

        // Send to webhook
        String json = mapper.writeValueAsString(review);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        Request httpRequest = new Request.Builder().url("http://localhost:" + PORT + "/validate/topic").post(body).build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).isNotNull();

            String responseBody = response.body().string();
            AdmissionReview responseReview = mapper.readValue(responseBody, AdmissionReview.class);

            assertThat(responseReview.getResponse()).isNotNull();
            assertThat(responseReview.getResponse().getUid()).isEqualTo("integration-test-001");
            assertThat(responseReview.getResponse().isAllowed()).isFalse();
            assertThat(responseReview.getResponse().getStatus().getMessage()).contains("Cannot change applicationServiceRef")
                    .contains("app-service-1")
                    .contains("hacker-service");
        }
    }

    @Test
    @DisplayName("Integration: Allow Topic partition change by same owner")
    void testAllowTopicPartitionChange() throws Exception {
        Topic oldTopic = createTopic("my-topic", "app-service-1");
        oldTopic.getSpec().setPartitions(3);

        Topic newTopic = createTopic("my-topic", "app-service-1"); // Same owner
        newTopic.getSpec().setPartitions(6); // Just changing partitions

        AdmissionReview review = new AdmissionReview();
        AdmissionRequest request = new AdmissionRequest();
        request.setUid("integration-test-002");
        request.setOperation("UPDATE");
        request.setNamespace("production");
        request.setName("my-topic");
        request.setObject(mapper.convertValue(newTopic, java.util.Map.class));
        request.setOldObject(mapper.convertValue(oldTopic, java.util.Map.class));

        review.setRequest(request);

        String json = mapper.writeValueAsString(review);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        Request httpRequest = new Request.Builder().url("http://localhost:" + PORT + "/validate/topic").post(body).build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).isNotNull();

            String responseBody = response.body().string();
            AdmissionReview responseReview = mapper.readValue(responseBody, AdmissionReview.class);

            assertThat(responseReview.getResponse().isAllowed()).isTrue();
        }
    }

    @Test
    @DisplayName("Integration: Block ACL ownership transfer")
    void testBlockACLOwnershipTransfer() throws Exception {
        ACL oldAcl = createACL("my-acl", "app-service-1");
        ACL newAcl = createACL("my-acl", "hacker-service");

        AdmissionReview review = new AdmissionReview();
        AdmissionRequest request = new AdmissionRequest();
        request.setUid("integration-test-003");
        request.setOperation("UPDATE");
        request.setNamespace("production");
        request.setName("my-acl");
        request.setObject(mapper.convertValue(newAcl, java.util.Map.class));
        request.setOldObject(mapper.convertValue(oldAcl, java.util.Map.class));

        review.setRequest(request);

        String json = mapper.writeValueAsString(review);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        Request httpRequest = new Request.Builder().url("http://localhost:" + PORT + "/validate/acl").post(body).build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body()).isNotNull();

            String responseBody = response.body().string();
            AdmissionReview responseReview = mapper.readValue(responseBody, AdmissionReview.class);

            assertThat(responseReview.getResponse().isAllowed()).isFalse();
        }
    }

    private static Topic createTopic(String name, String appServiceRef) {
        Topic topic = new Topic();
        topic.setMetadata(new ObjectMeta());
        topic.getMetadata().setName(name);
        topic.getMetadata().setNamespace("production");

        TopicCRSpec spec = new TopicCRSpec();
        spec.setApplicationServiceRef(appServiceRef);
        spec.setServiceRef("sa-1");
        spec.setName(name.replace("-", "."));
        spec.setPartitions(3);
        spec.setReplicationFactor(3);
        topic.setSpec(spec);

        return topic;
    }

    private static ACL createACL(String name, String appServiceRef) {
        ACL acl = new ACL();
        acl.setMetadata(new ObjectMeta());
        acl.getMetadata().setName(name);
        acl.getMetadata().setNamespace("production");

        AclCRSpec spec = new AclCRSpec();
        spec.setApplicationServiceRef(appServiceRef);
        spec.setServiceRef("sa-1");
        spec.setTopicRef("topic-1");
        spec.setOperations(new ArrayList<>());
        acl.setSpec(spec);

        return acl;
    }
}
