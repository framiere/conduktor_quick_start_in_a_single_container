package com.example.messaging.operator.e2e;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.messaging.operator.crd.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E2E tests for High Availability and failover scenarios. Verifies that the webhook remains available during pod failures and restarts.
 */
@E2ETest
class HAFailoverE2ETest extends E2ETestBase {

    private int originalReplicas;

    @BeforeEach
    void setup() {
        cleanupTestResources();
        originalReplicas = getWebhookReadyReplicas();
    }

    @AfterEach
    void cleanup() {
        cleanupTestResources();
        // Restore original replica count
        if (getWebhookReadyReplicas() != 1) {
            scaleWebhook(1);
        }
    }

    @Test
    @DisplayName("Webhook remains available during single pod failure")
    void webhookRemainsAvailable_duringSinglePodFailure() {
        // Scale to 2 replicas
        scaleWebhook(2);
        assertThat(getWebhookReadyReplicas()).isEqualTo(2);

        // Setup test resources
        createApplicationService("e2e-ha-app");
        createKafkaCluster("e2e-ha-vc", "e2e-ha-app");
        createServiceAccount("e2e-ha-sa", "e2e-ha-vc", "e2e-ha-app");

        // Delete one pod
        List<String> pods = getWebhookPods();
        assertThat(pods).hasSizeGreaterThanOrEqualTo(2);
        deletePod(pods.get(0));

        // Webhook should still work immediately (other pod handles requests)
        Topic topic = createTopic("e2e-ha-topic", "e2e-ha-sa", "e2e-ha-app");
        assertThat(resourceExists(Topic.class, "e2e-ha-topic")).isTrue();

        // Wait for pod to be recreated
        await().atMost(60, SECONDS).pollInterval(2, SECONDS).untilAsserted(() -> {
            assertThat(getWebhookReadyReplicas()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("Webhook recovers after complete pod restart")
    void webhookRecovers_afterPodRestart() {
        // Ensure we have at least 1 replica
        assertThat(getWebhookReadyReplicas()).isGreaterThanOrEqualTo(1);

        // Setup test resources before restart
        createApplicationService("e2e-restart-app");
        assertThat(resourceExists(ApplicationService.class, "e2e-restart-app")).isTrue();

        // Delete the pod (Kubernetes will recreate it)
        List<String> pods = getWebhookPods();
        assertThat(pods).isNotEmpty();
        deletePod(pods.get(0));

        // Wait for recovery
        await().atMost(90, SECONDS).pollInterval(2, SECONDS).untilAsserted(() -> {
            assertThat(getWebhookReadyReplicas()).isGreaterThanOrEqualTo(1);
        });

        // Webhook should work again
        KafkaCluster vc = createKafkaCluster("e2e-restart-vc", "e2e-restart-app");
        assertThat(resourceExists(KafkaCluster.class, "e2e-restart-vc")).isTrue();
    }

    @Test
    @DisplayName("Multiple operations succeed during HA mode")
    void multipleOperations_succeedDuringHAMode() {
        // Scale to 2 replicas for HA
        scaleWebhook(2);

        // Create base resources
        createApplicationService("e2e-ha-multi-app");
        createKafkaCluster("e2e-ha-multi-vc", "e2e-ha-multi-app");
        createServiceAccount("e2e-ha-multi-sa", "e2e-ha-multi-vc", "e2e-ha-multi-app");

        // Create multiple topics rapidly
        for (int i = 1; i <= 5; i++) {
            createTopic("e2e-ha-topic-" + i, "e2e-ha-multi-sa", "e2e-ha-multi-app");
        }

        // Verify all were created
        for (int i = 1; i <= 5; i++) {
            assertThat(resourceExists(Topic.class, "e2e-ha-topic-" + i)).as("Topic e2e-ha-topic-%d should exist", i).isTrue();
        }
    }

    @Test
    @DisplayName("Operations continue during rolling restart")
    void operations_continueDuringRollingRestart() {
        // Scale to 2 replicas
        scaleWebhook(2);

        // Create base resources
        createApplicationService("e2e-rolling-app");
        createKafkaCluster("e2e-rolling-vc", "e2e-rolling-app");
        createServiceAccount("e2e-rolling-sa", "e2e-rolling-vc", "e2e-rolling-app");

        // Delete first pod and immediately create a topic
        List<String> pods = getWebhookPods();
        deletePod(pods.get(0));

        // Should still be able to create resources
        createTopic("e2e-rolling-topic-1", "e2e-rolling-sa", "e2e-rolling-app");
        assertThat(resourceExists(Topic.class, "e2e-rolling-topic-1")).isTrue();

        // Wait for pod recovery
        await().atMost(60, SECONDS).pollInterval(2, SECONDS).untilAsserted(() -> {
            assertThat(getWebhookReadyReplicas()).isEqualTo(2);
        });

        // Delete second pod and create another topic
        pods = getWebhookPods();
        deletePod(pods.get(0));

        createTopic("e2e-rolling-topic-2", "e2e-rolling-sa", "e2e-rolling-app");
        assertThat(resourceExists(Topic.class, "e2e-rolling-topic-2")).isTrue();
    }
}
