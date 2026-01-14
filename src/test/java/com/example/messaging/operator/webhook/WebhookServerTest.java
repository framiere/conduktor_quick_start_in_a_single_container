package com.example.messaging.operator.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.store.CRDStore;
import com.example.messaging.operator.validation.OwnershipValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.*;

@DisplayName("WebhookServer Integration Tests")
class WebhookServerTest {

    private WebhookServer server;
    private OkHttpClient httpClient;
    private ObjectMapper mapper;
    private int port = 8443;

    @BeforeEach
    void setUp() throws Exception {
        CRDStore store = new CRDStore();
        OwnershipValidator ownershipValidator = new OwnershipValidator(store);
        mapper = new ObjectMapper();
        WebhookValidator validator = new WebhookValidator(ownershipValidator, mapper);

        server = new WebhookServer(validator, port);
        server.start();

        httpClient = new OkHttpClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("should respond to health check")
    void testHealthCheck() throws Exception {
        Request request = new Request.Builder()
                .url("http://localhost:" + port + "/health")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).isEqualTo("OK");
        }
    }

    @Test
    @DisplayName("should validate Topic UPDATE and deny ownership change")
    void testValidateTopicUpdate() throws Exception {
        String admissionReviewJson =
                """
        {
          "apiVersion": "admission.k8s.io/v1",
          "kind": "AdmissionReview",
          "request": {
            "uid": "test-uid-123",
            "operation": "UPDATE",
            "namespace": "default",
            "name": "test-topic",
            "kind": {"group": "example.com", "version": "v1", "kind": "Topic"},
            "object": {
              "apiVersion": "example.com/v1",
              "kind": "Topic",
              "metadata": {"name": "test-topic", "namespace": "default"},
              "spec": {
                "applicationServiceRef": "hacker-app",
                "serviceRef": "sa-1",
                "name": "test.topic",
                "partitions": 3,
                "replicationFactor": 3
              }
            },
            "oldObject": {
              "apiVersion": "example.com/v1",
              "kind": "Topic",
              "metadata": {"name": "test-topic", "namespace": "default"},
              "spec": {
                "applicationServiceRef": "app-1",
                "serviceRef": "sa-1",
                "name": "test.topic",
                "partitions": 3,
                "replicationFactor": 3
              }
            }
          }
        }
        """;

        RequestBody body = RequestBody.create(admissionReviewJson, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url("http://localhost:" + port + "/validate/topic")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);

            String responseBody = response.body().string();
            AdmissionReview review = mapper.readValue(responseBody, AdmissionReview.class);

            assertThat(review.getResponse()).isNotNull();
            assertThat(review.getResponse().isAllowed()).isFalse();
            assertThat(review.getResponse().getStatus().getMessage()).contains("Cannot change applicationServiceRef");
        }
    }
}
