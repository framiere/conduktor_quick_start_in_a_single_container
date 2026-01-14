package com.example.messaging.operator.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.store.CRDStore;
import com.example.messaging.operator.validation.OwnershipValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WebhookValidator Tests")
class WebhookValidatorTest {

    private WebhookValidator validator;
    private CRDStore store;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        store = new CRDStore();
        OwnershipValidator ownershipValidator = new OwnershipValidator(store);
        mapper = new ObjectMapper();
        validator = new WebhookValidator(ownershipValidator, mapper);
    }

    @Test
    @DisplayName("should allow UPDATE when applicationServiceRef unchanged")
    void testAllowUpdateSameOwner() {
        // Create old Topic
        Topic oldTopic = new Topic();
        oldTopic.setMetadata(new ObjectMeta());
        oldTopic.getMetadata().setName("test-topic");
        oldTopic.getMetadata().setNamespace("default");
        TopicCRSpec oldSpec = new TopicCRSpec();
        oldSpec.setApplicationServiceRef("app-service-1");
        oldSpec.setServiceRef("sa-1");
        oldSpec.setName("test.topic");
        oldSpec.setPartitions(3);
        oldSpec.setReplicationFactor(3);
        oldTopic.setSpec(oldSpec);

        // Create new Topic with same owner
        Topic newTopic = new Topic();
        newTopic.setMetadata(new ObjectMeta());
        newTopic.getMetadata().setName("test-topic");
        newTopic.getMetadata().setNamespace("default");
        TopicCRSpec newSpec = new TopicCRSpec();
        newSpec.setApplicationServiceRef("app-service-1"); // SAME owner
        newSpec.setServiceRef("sa-1");
        newSpec.setName("test.topic");
        newSpec.setPartitions(6); // Changed partitions
        newSpec.setReplicationFactor(3);
        newTopic.setSpec(newSpec);

        AdmissionRequest request = new AdmissionRequest();
        request.setUid("test-uid");
        request.setOperation("UPDATE");
        request.setNamespace("default");
        request.setName("test-topic");
        request.setObject(mapper.convertValue(newTopic, Map.class));
        request.setOldObject(mapper.convertValue(oldTopic, Map.class));

        AdmissionResponse response = validator.validate(request, Topic.class);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getUid()).isEqualTo("test-uid");
    }

    @Test
    @DisplayName("should deny UPDATE when applicationServiceRef changed")
    void testDenyUpdateDifferentOwner() {
        // Create old Topic
        Topic oldTopic = new Topic();
        oldTopic.setMetadata(new ObjectMeta());
        oldTopic.getMetadata().setName("test-topic");
        TopicCRSpec oldSpec = new TopicCRSpec();
        oldSpec.setApplicationServiceRef("app-service-1");
        oldSpec.setServiceRef("sa-1");
        oldSpec.setName("test.topic");
        oldSpec.setPartitions(3);
        oldSpec.setReplicationFactor(3);
        oldTopic.setSpec(oldSpec);

        // Create new Topic with DIFFERENT owner
        Topic newTopic = new Topic();
        newTopic.setMetadata(new ObjectMeta());
        newTopic.getMetadata().setName("test-topic");
        TopicCRSpec newSpec = new TopicCRSpec();
        newSpec.setApplicationServiceRef("hacker-service"); // DIFFERENT owner
        newSpec.setServiceRef("sa-1");
        newSpec.setName("test.topic");
        newSpec.setPartitions(3);
        newSpec.setReplicationFactor(3);
        newTopic.setSpec(newSpec);

        AdmissionRequest request = new AdmissionRequest();
        request.setUid("test-uid");
        request.setOperation("UPDATE");
        request.setNamespace("default");
        request.setName("test-topic");
        request.setObject(mapper.convertValue(newTopic, Map.class));
        request.setOldObject(mapper.convertValue(oldTopic, Map.class));

        AdmissionResponse response = validator.validate(request, Topic.class);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getStatus().getMessage()).contains("Cannot change applicationServiceRef").contains("app-service-1").contains("hacker-service");
    }

    @Test
    @DisplayName("should deny VirtualCluster UPDATE when applicationServiceRef changed")
    void testDenyVirtualClusterUpdate() {
        VirtualCluster oldVc = new VirtualCluster();
        oldVc.setMetadata(new ObjectMeta());
        oldVc.getMetadata().setName("vc-1");
        VirtualClusterSpec oldSpec = new VirtualClusterSpec();
        oldSpec.setClusterId("cluster-1");
        oldSpec.setApplicationServiceRef("app-1");
        oldVc.setSpec(oldSpec);

        VirtualCluster newVc = new VirtualCluster();
        newVc.setMetadata(new ObjectMeta());
        newVc.getMetadata().setName("vc-1");
        VirtualClusterSpec newSpec = new VirtualClusterSpec();
        newSpec.setClusterId("cluster-1");
        newSpec.setApplicationServiceRef("hacker-app"); // Changed!
        newVc.setSpec(newSpec);

        AdmissionRequest request = new AdmissionRequest();
        request.setUid("test-uid-vc");
        request.setOperation("UPDATE");
        request.setObject(mapper.convertValue(newVc, Map.class));
        request.setOldObject(mapper.convertValue(oldVc, Map.class));

        AdmissionResponse response = validator.validate(request, VirtualCluster.class);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getStatus().getMessage()).contains("Cannot change applicationServiceRef");
    }

    @Test
    @DisplayName("should deny ServiceAccount UPDATE when applicationServiceRef changed")
    void testDenyServiceAccountUpdate() {
        ServiceAccount oldSa = new ServiceAccount();
        oldSa.setMetadata(new ObjectMeta());
        oldSa.getMetadata().setName("sa-1");
        ServiceAccountSpec oldSpec = new ServiceAccountSpec();
        oldSpec.setName("service-1");
        oldSpec.setApplicationServiceRef("app-1");
        oldSpec.setClusterRef("cluster-1");
        oldSa.setSpec(oldSpec);

        ServiceAccount newSa = new ServiceAccount();
        newSa.setMetadata(new ObjectMeta());
        newSa.getMetadata().setName("sa-1");
        ServiceAccountSpec newSpec = new ServiceAccountSpec();
        newSpec.setName("service-1");
        newSpec.setApplicationServiceRef("hacker-app"); // Changed!
        newSpec.setClusterRef("cluster-1");
        newSa.setSpec(newSpec);

        AdmissionRequest request = new AdmissionRequest();
        request.setUid("test-uid-sa");
        request.setOperation("UPDATE");
        request.setObject(mapper.convertValue(newSa, Map.class));
        request.setOldObject(mapper.convertValue(oldSa, Map.class));

        AdmissionResponse response = validator.validate(request, ServiceAccount.class);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getStatus().getMessage()).contains("Cannot change applicationServiceRef");
    }

    @Test
    @DisplayName("should deny ACL UPDATE when applicationServiceRef changed")
    void testDenyACLUpdate() {
        ACL oldAcl = new ACL();
        oldAcl.setMetadata(new ObjectMeta());
        oldAcl.getMetadata().setName("acl-1");
        AclCRSpec oldSpec = new AclCRSpec();
        oldSpec.setApplicationServiceRef("app-1");
        oldSpec.setServiceRef("sa-1");
        oldSpec.setTopicRef("topic-1");
        oldAcl.setSpec(oldSpec);

        ACL newAcl = new ACL();
        newAcl.setMetadata(new ObjectMeta());
        newAcl.getMetadata().setName("acl-1");
        AclCRSpec newSpec = new AclCRSpec();
        newSpec.setApplicationServiceRef("hacker-app"); // Changed!
        newSpec.setServiceRef("sa-1");
        newSpec.setTopicRef("topic-1");
        newAcl.setSpec(newSpec);

        AdmissionRequest request = new AdmissionRequest();
        request.setUid("test-uid-acl");
        request.setOperation("UPDATE");
        request.setObject(mapper.convertValue(newAcl, Map.class));
        request.setOldObject(mapper.convertValue(oldAcl, Map.class));

        AdmissionResponse response = validator.validate(request, ACL.class);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getStatus().getMessage()).contains("Cannot change applicationServiceRef");
    }
}
