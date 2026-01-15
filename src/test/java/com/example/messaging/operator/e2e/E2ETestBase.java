package com.example.messaging.operator.e2e;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.messaging.operator.crd.*;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.function.Executable;

/**
 * Base class for E2E tests that run against a real Kubernetes cluster. Uses the current kubectl context to connect to the cluster.
 */
public abstract class E2ETestBase {

    protected static KubernetesClient k8sClient;
    protected static String namespace;

    private static final String WEBHOOK_SERVICE_NAME = "messaging-operator-webhook";
    private static final String WEBHOOK_DEPLOYMENT_NAME = "messaging-operator-webhook";

    @BeforeAll
    static void setupCluster() {
        Config config = Config.autoConfigure(null);
        k8sClient = new KubernetesClientBuilder().withConfig(config).build();

        namespace = resolveNamespace();
        assertThat(namespace).as("Namespace must be configured").isNotNull();

        waitForWebhookReady();
    }

    @AfterAll
    static void teardownCluster() {
        if (k8sClient != null) {
            k8sClient.close();
        }
    }

    /**
     * Resolves namespace from (in order): TEST_NAMESPACE env var, test.namespace system property, .test-namespace file
     */
    protected static String resolveNamespace() {
        String fromEnv = System.getenv("TEST_NAMESPACE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        String fromProperty = System.getProperty("test.namespace");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }

        Path namespaceFile = Path.of("functional-tests/.test-namespace");
        if (Files.exists(namespaceFile)) {
            try {
                String content = Files.readString(namespaceFile).trim();
                if (!content.isBlank()) {
                    return content;
                }
            } catch (IOException e) {
                // Fall through
            }
        }

        return "operator-system";
    }

    /**
     * Waits for the webhook deployment to be ready with at least 1 replica
     */
    protected static void waitForWebhookReady() {
        await().atMost(60, SECONDS).pollInterval(2, SECONDS).untilAsserted(() -> {
            assertThat(webhookHasEndpoints()).isTrue();
        });
    }

    /**
     * Checks if the webhook service has ready endpoints
     */
    protected static boolean webhookHasEndpoints() {
        Endpoints endpoints = k8sClient.endpoints().inNamespace(namespace).withName(WEBHOOK_SERVICE_NAME).get();

        if (endpoints == null || endpoints.getSubsets() == null) {
            return false;
        }

        return endpoints.getSubsets().stream().anyMatch(subset -> subset.getAddresses() != null && !subset.getAddresses().isEmpty());
    }

    /**
     * Gets the ready replica count for the webhook deployment
     */
    protected static int getWebhookReadyReplicas() {
        Deployment deployment = k8sClient.apps().deployments().inNamespace(namespace).withName(WEBHOOK_DEPLOYMENT_NAME).get();

        if (deployment == null || deployment.getStatus() == null) {
            return 0;
        }

        Integer ready = deployment.getStatus().getReadyReplicas();
        return ready != null ? ready : 0;
    }

    /**
     * Scales the webhook deployment to the specified number of replicas
     */
    protected static void scaleWebhook(int replicas) {
        k8sClient.apps().deployments().inNamespace(namespace).withName(WEBHOOK_DEPLOYMENT_NAME).scale(replicas);

        await().atMost(120, SECONDS).pollInterval(2, SECONDS).untilAsserted(() -> {
            assertThat(getWebhookReadyReplicas()).isEqualTo(replicas);
        });
    }

    /**
     * Gets the names of all webhook pods
     */
    protected static List<String> getWebhookPods() {
        return k8sClient.pods()
                .inNamespace(namespace)
                .withLabel("app.kubernetes.io/name", "messaging-operator")
                .list()
                .getItems()
                .stream()
                .map(pod -> pod.getMetadata().getName())
                .collect(Collectors.toList());
    }

    /**
     * Deletes a specific pod by name
     */
    protected static void deletePod(String podName) {
        k8sClient.pods().inNamespace(namespace).withName(podName).delete();
    }

    /**
     * Cleans up all test CRD instances in the namespace
     */
    protected void cleanupTestResources() {
        safeDelete(() -> k8sClient.resources(ACL.class).inNamespace(namespace).delete());
        safeDelete(() -> k8sClient.resources(ConsumerGroup.class).inNamespace(namespace).delete());
        safeDelete(() -> k8sClient.resources(Topic.class).inNamespace(namespace).delete());
        safeDelete(() -> k8sClient.resources(ServiceAccount.class).inNamespace(namespace).delete());
        safeDelete(() -> k8sClient.resources(VirtualCluster.class).inNamespace(namespace).delete());
        safeDelete(() -> k8sClient.resources(ApplicationService.class).inNamespace(namespace).delete());

        await().atMost(30, SECONDS).pollInterval(1, SECONDS).untilAsserted(() -> {
            assertThat(k8sClient.resources(ApplicationService.class).inNamespace(namespace).list().getItems()).isEmpty();
        });
    }

    private void safeDelete(Runnable deleteAction) {
        try {
            deleteAction.run();
        } catch (KubernetesClientException e) {
            // Ignore errors during cleanup (e.g., resource not found)
        }
    }

    /**
     * Asserts that the given executable throws a KubernetesClientException containing the expected message
     */
    protected static void assertRejectedWith(Executable executable, String expectedMessage) {
        try {
            executable.execute();
            throw new AssertionError("Expected rejection but operation succeeded");
        } catch (KubernetesClientException e) {
            assertThat(e.getMessage()).contains(expectedMessage);
        } catch (Throwable t) {
            throw new AssertionError("Expected KubernetesClientException but got: " + t.getClass().getName(), t);
        }
    }

    /**
     * Creates an ApplicationService and waits for it to be available
     */
    protected ApplicationService createApplicationService(String name) {
        ApplicationService as = new ApplicationService();
        as.getMetadata().setNamespace(namespace);
        as.getMetadata().setName(name);
        as.getSpec().setName(name);
        return k8sClient.resource(as).create();
    }

    /**
     * Creates a VirtualCluster and waits for it to be available
     */
    protected VirtualCluster createVirtualCluster(String name, String applicationServiceRef) {
        VirtualCluster vc = new VirtualCluster();
        vc.getMetadata().setNamespace(namespace);
        vc.getMetadata().setName(name);
        vc.getSpec().setClusterId(name);
        vc.getSpec().setApplicationServiceRef(applicationServiceRef);
        return k8sClient.resource(vc).create();
    }

    /**
     * Creates a ServiceAccount and waits for it to be available
     */
    protected ServiceAccount createServiceAccount(String name, String clusterRef, String applicationServiceRef) {
        ServiceAccount sa = new ServiceAccount();
        sa.getMetadata().setNamespace(namespace);
        sa.getMetadata().setName(name);
        sa.getSpec().setName(name);
        sa.getSpec().setDn(List.of("CN=" + name));
        sa.getSpec().setClusterRef(clusterRef);
        sa.getSpec().setApplicationServiceRef(applicationServiceRef);
        return k8sClient.resource(sa).create();
    }

    /**
     * Creates a Topic and waits for it to be available
     */
    protected Topic createTopic(String name, String serviceRef, String applicationServiceRef) {
        Topic topic = new Topic();
        topic.getMetadata().setNamespace(namespace);
        topic.getMetadata().setName(name);
        topic.getSpec().setName(name);
        topic.getSpec().setServiceRef(serviceRef);
        topic.getSpec().setApplicationServiceRef(applicationServiceRef);
        topic.getSpec().setPartitions(3);
        topic.getSpec().setReplicationFactor(1);
        return k8sClient.resource(topic).create();
    }

    /**
     * Checks if a resource exists in the cluster
     */
    protected <T extends io.fabric8.kubernetes.api.model.HasMetadata> boolean resourceExists(Class<T> resourceClass, String name) {
        try {
            T result = k8sClient.resources(resourceClass).inNamespace(namespace).withName(name).get();
            return result != null;
        } catch (KubernetesClientException e) {
            return false;
        }
    }
}
