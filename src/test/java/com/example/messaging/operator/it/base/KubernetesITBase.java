package com.example.messaging.operator.it.base;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import com.example.messaging.operator.validation.OwnershipValidator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for Kubernetes integration tests. Provides mock K8s server, client, CRDStore, and OwnershipValidator.
 */
public abstract class KubernetesITBase {

    protected static KubernetesServer k8sServer;
    protected static KubernetesClient k8sClient;
    protected static CRDStore store;
    protected static OwnershipValidator ownershipValidator;

    @BeforeAll
    static void setupKubernetes() {
        // Start Fabric8 mock server
        k8sServer = new KubernetesServer(false, true);
        k8sServer.before();

        // Get client connected to mock server
        k8sClient = k8sServer.getClient();

        // Initialize store and validator
        store = new CRDStore();
        ownershipValidator = new OwnershipValidator(store);
    }

    @AfterAll
    static void teardownKubernetes() {
        if (k8sServer != null) {
            k8sServer.after();
        }
    }

    @AfterEach
    void cleanupResources() {
        // Clear CRDStore between tests
        store.clear();

        // Clean up K8s mock server state
        k8sClient.resources(ApplicationService.class).inAnyNamespace().delete();
        k8sClient.resources(VirtualCluster.class).inAnyNamespace().delete();
        k8sClient.resources(ServiceAccount.class).inAnyNamespace().delete();
        k8sClient.resources(Topic.class).inAnyNamespace().delete();
        k8sClient.resources(ACL.class).inAnyNamespace().delete();
        k8sClient.resources(ConsumerGroup.class).inAnyNamespace().delete();
    }

    /**
     * Sync a resource to CRDStore (simulates watch event)
     */
    protected void syncToStore(HasMetadata resource) {
        CRDKind kind = CRDKind.fromValue(resource.getKind());
        String namespace = resource.getMetadata().getNamespace();
        store.create(kind, namespace, resource);
    }

    /**
     * Sync all resources from K8s to store
     */
    protected void syncAllToStore() {
        k8sClient.resources(ApplicationService.class).inAnyNamespace().list().getItems().forEach(this::syncToStore);
        k8sClient.resources(VirtualCluster.class).inAnyNamespace().list().getItems().forEach(this::syncToStore);
        k8sClient.resources(ServiceAccount.class).inAnyNamespace().list().getItems().forEach(this::syncToStore);
        k8sClient.resources(Topic.class).inAnyNamespace().list().getItems().forEach(this::syncToStore);
        k8sClient.resources(ACL.class).inAnyNamespace().list().getItems().forEach(this::syncToStore);
        k8sClient.resources(ConsumerGroup.class).inAnyNamespace().list().getItems().forEach(this::syncToStore);
    }
}
