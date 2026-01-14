package com.example.messaging.operator.it.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.it.base.ComponentITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Component integration tests for CRDStore with Kubernetes mock server. Tests the synchronization between K8s API and in-memory CRDStore.
 */
public class CRDStoreIT extends ComponentITBase {

    @Test
    void testK8sCreateSyncsStore() {
        // Create ApplicationService in K8s
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("test-app").appName("test-app").createIn(k8sClient);

        // Verify resource exists in K8s
        ApplicationService fromK8s = k8sClient.resources(ApplicationService.class).inNamespace("default").withName("test-app").get();
        assertThat(fromK8s)
                .isNotNull();
        assertThat(fromK8s.getSpec().getName())
                .isEqualTo("test-app");

        // Sync to store
        syncToStore(app);

        // Verify resource exists in store
        ApplicationService fromStore = store.get("ApplicationService", "default", "test-app");
        assertThat(fromStore)
                .isNotNull();
        assertThat(fromStore.getMetadata().getName())
                .isEqualTo("test-app");
        assertThat(fromStore.getMetadata().getResourceVersion())
                .isNotNull();
        assertThat(fromStore.getMetadata().getUid())
                .isNotNull();
    }

    @Test
    void testListResourcesFromStore() {
        // Create full ownership chain: ApplicationService -> VirtualCluster -> ServiceAccount -> Topics
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("test-app").appName("test-app").createIn(k8sClient);
        syncToStore(app);

        VirtualCluster cluster = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("test-cluster")
                .clusterId("test-cluster-id")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);
        syncToStore(cluster);

        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("test-sa")
                .saName("test-sa")
                .clusterRef("test-cluster")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);
        syncToStore(sa);

        // Create multiple Topics in K8s
        Topic topic1 = TestDataBuilder.topic()
                .namespace("default")
                .name("topic-1")
                .topicName("topic-1")
                .partitions(3)
                .replicationFactor(1)
                .serviceRef("test-sa")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);

        Topic topic2 = TestDataBuilder.topic()
                .namespace("default")
                .name("topic-2")
                .topicName("topic-2")
                .partitions(6)
                .replicationFactor(1)
                .serviceRef("test-sa")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);

        Topic topic3 = TestDataBuilder.topic()
                .namespace("default")
                .name("topic-3")
                .topicName("topic-3")
                .partitions(9)
                .replicationFactor(1)
                .serviceRef("test-sa")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);

        // Sync all to store
        syncToStore(topic1);
        syncToStore(topic2);
        syncToStore(topic3);

        // List all topics from store
        List<Topic> topics = store.list("Topic", "default");

        // Verify all topics are present
        assertThat(topics)
                .hasSize(3);
        assertThat(topics).extracting(t -> t.getMetadata().getName()).containsExactlyInAnyOrder("topic-1", "topic-2", "topic-3");
        assertThat(topics).extracting(t -> t.getSpec().getPartitions()).containsExactlyInAnyOrder(3, 6, 9);
    }

    @Test
    void testUpdateResourceInStore() {
        // Create ApplicationService first (required for ownership)
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("test-app").appName("test-app").createIn(k8sClient);
        syncToStore(app);

        // Create VirtualCluster in K8s
        VirtualCluster cluster = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("test-cluster")
                .clusterId("original-cluster-id")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);

        // Sync to store
        syncToStore(cluster);

        // Verify initial state
        VirtualCluster fromStore = store.get("VirtualCluster", "default", "test-cluster");
        assertThat(fromStore)
                .isNotNull();
        assertThat(fromStore.getSpec().getClusterId())
                .isEqualTo("original-cluster-id");
        String originalVersion = fromStore.getMetadata().getResourceVersion();

        // Update cluster spec
        fromStore.getSpec().setClusterId("updated-cluster-id");

        // Update in store
        VirtualCluster updated = store.update("VirtualCluster", "default", "test-cluster", fromStore);

        // Verify update
        assertThat(updated)
                .isNotNull();
        assertThat(updated.getSpec().getClusterId())
                .isEqualTo("updated-cluster-id");
        assertThat(updated.getMetadata().getResourceVersion()).isNotEqualTo(originalVersion);

        // Verify persisted in store
        VirtualCluster fromStoreAfterUpdate = store.get("VirtualCluster", "default", "test-cluster");
        assertThat(fromStoreAfterUpdate.getSpec().getClusterId()).isEqualTo("updated-cluster-id");
    }

    @Test
    void testDeleteResourceFromStore() {
        // Create full ownership chain: ApplicationService -> VirtualCluster -> ServiceAccount
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("test-app").appName("test-app").createIn(k8sClient);
        syncToStore(app);

        VirtualCluster cluster = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("test-cluster")
                .clusterId("test-cluster-id")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);
        syncToStore(cluster);

        // Create ServiceAccount in K8s
        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("test-sa")
                .saName("test-sa")
                .dn("CN=test-user")
                .clusterRef("test-cluster")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);

        // Sync to store
        syncToStore(sa);

        // Verify resource exists
        ServiceAccount fromStore = store.get("ServiceAccount", "default", "test-sa");
        assertThat(fromStore).isNotNull();

        // Delete from store
        boolean deleted = store.delete("ServiceAccount", "default", "test-sa");
        assertThat(deleted)
                .isTrue();

        // Verify resource no longer exists
        ServiceAccount afterDelete = store.get("ServiceAccount", "default", "test-sa");
        assertThat(afterDelete)
                .isNull();

        // Verify delete of non-existent resource returns false
        boolean deletedAgain = store.delete("ServiceAccount", "default", "test-sa");
        assertThat(deletedAgain)
                .isFalse();
    }
}
