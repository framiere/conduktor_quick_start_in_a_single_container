package com.example.messaging.operator.webhook;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.store.CRDStore;
import com.example.messaging.operator.validation.OwnershipValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        newSpec.setApplicationServiceRef("app-service-1");  // SAME owner
        newSpec.setServiceRef("sa-1");
        newSpec.setName("test.topic");
        newSpec.setPartitions(6);  // Changed partitions
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
        newSpec.setApplicationServiceRef("hacker-service");  // DIFFERENT owner
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
        assertThat(response.getStatus().getMessage())
                .contains("Cannot change applicationServiceRef")
                .contains("app-service-1")
                .contains("hacker-service");
    }
}
