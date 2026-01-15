package com.example.messaging.operator.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E2E tests for validating the ownership chain hierarchy: ApplicationService -> KafkaCluster -> ServiceAccount -> Topic/ACL/ConsumerGroup
 */
@E2ETest
class OwnershipChainE2ETest extends E2ETestBase {

    @BeforeEach
    void setup() {
        cleanupTestResources();
    }

    @AfterEach
    void cleanup() {
        cleanupTestResources();
    }

    @Test
    @DisplayName("Full ownership chain with valid hierarchy is accepted")
    void fullOwnershipChain_validHierarchy_accepted() {
        // Create full hierarchy: App -> VC -> SA -> Topic
        ApplicationService app = createApplicationService("e2e-chain-app");
        assertThat(resourceExists(ApplicationService.class, "e2e-chain-app")).isTrue();

        KafkaCluster vc = createKafkaCluster("e2e-chain-vc", "e2e-chain-app");
        assertThat(resourceExists(KafkaCluster.class, "e2e-chain-vc")).isTrue();

        ServiceAccount sa = createServiceAccount("e2e-chain-sa", "e2e-chain-vc", "e2e-chain-app");
        assertThat(resourceExists(ServiceAccount.class, "e2e-chain-sa")).isTrue();

        Topic topic = createTopic("e2e-chain-topic", "e2e-chain-sa", "e2e-chain-app");
        assertThat(resourceExists(Topic.class, "e2e-chain-topic")).isTrue();
    }

    @Test
    @DisplayName("KafkaCluster with non-existent ApplicationService is rejected")
    void kafkaCluster_withNonExistentApplicationService_rejected() {
        assertRejectedWith(() -> createKafkaCluster("orphan-vc", "non-existent-app"), "ApplicationService");
    }

    @Test
    @DisplayName("ServiceAccount with non-existent KafkaCluster is rejected")
    void serviceAccount_withNonExistentKafkaCluster_rejected() {
        // Create app first
        createApplicationService("e2e-sa-app");

        assertRejectedWith(() -> createServiceAccount("orphan-sa", "non-existent-vc", "e2e-sa-app"), "KafkaCluster");
    }

    @Test
    @DisplayName("Topic with non-existent ServiceAccount is rejected")
    void topic_withNonExistentServiceAccount_rejected() {
        // Create app and vc first
        createApplicationService("e2e-topic-app");
        createKafkaCluster("e2e-topic-vc", "e2e-topic-app");

        assertRejectedWith(() -> createTopic("orphan-topic", "non-existent-sa", "e2e-topic-app"), "ServiceAccount");
    }

    @Test
    @DisplayName("ServiceAccount must belong to same ApplicationService as its KafkaCluster")
    void serviceAccount_mustBelongToSameApplicationService() {
        // Create two separate apps
        createApplicationService("e2e-app-a");
        createApplicationService("e2e-app-b");

        // Create VC under app-a
        createKafkaCluster("e2e-vc-a", "e2e-app-a");

        // Try to create SA under app-b but referencing app-a's VC
        assertRejectedWith(() -> createServiceAccount("cross-app-sa", "e2e-vc-a", "e2e-app-b"), "does not belong");
    }

    @Test
    @DisplayName("Topic must belong to same ApplicationService as its ServiceAccount")
    void topic_mustBelongToSameApplicationService() {
        // Create two separate apps with full chains
        createApplicationService("e2e-topic-app-a");
        createKafkaCluster("e2e-topic-vc-a", "e2e-topic-app-a");
        createServiceAccount("e2e-topic-sa-a", "e2e-topic-vc-a", "e2e-topic-app-a");

        createApplicationService("e2e-topic-app-b");

        // Try to create topic under app-b but referencing app-a's SA
        assertRejectedWith(() -> createTopic("cross-app-topic", "e2e-topic-sa-a", "e2e-topic-app-b"), "does not belong");
    }
}
