package com.example.messaging.operator.it.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.it.base.ScenarioITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for complete CRD lifecycle scenarios. Tests creating complete ownership chains, updates, and deletions.
 */
public class CRDLifecycleIT extends ScenarioITBase {

    @Test
    void testCompleteLifecycle() {
        // Step 1: Create complete ownership chain: app → vc → sa → topic → acl

        // Create ApplicationService
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("lifecycle-app").appName("lifecycle-app").createIn(k8sClient);
        assertThat(app.getMetadata().getUid())
                .isNotNull();
        syncToStore(app);

        // Create VirtualCluster owned by app
        VirtualCluster vc = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("lifecycle-cluster")
                .clusterId("lifecycle-cluster-id")
                .applicationServiceRef("lifecycle-app")
                .ownedBy(app)
                .createIn(k8sClient);
        assertThat(vc.getMetadata().getUid())
                .isNotNull();
        syncToStore(vc);

        // Verify ownership
        List<OwnerReference> vcOwners = vc.getMetadata().getOwnerReferences();
        assertThat(vcOwners)
                .isNotNull()
                .hasSize(1);
        assertThat(vcOwners)
                .first()
                .extracting(OwnerReference::getName)
                .isEqualTo("lifecycle-app");
        assertThat(vcOwners)
                .first()
                .extracting(OwnerReference::getKind)
                .isEqualTo("ApplicationService");

        // Create ServiceAccount owned by vc
        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("lifecycle-sa")
                .saName("lifecycle-sa")
                .dn("CN=lifecycle-sa,OU=TEST,O=EXAMPLE,L=CITY,C=US")
                .clusterRef("lifecycle-cluster")
                .applicationServiceRef("lifecycle-app")
                .ownedBy(vc)
                .createIn(k8sClient);
        assertThat(sa.getMetadata().getUid())
                .isNotNull();
        syncToStore(sa);

        // Verify ownership
        List<OwnerReference> saOwners = sa.getMetadata().getOwnerReferences();
        assertThat(saOwners)
                .isNotNull()
                .hasSize(1);
        assertThat(saOwners)
                .first()
                .extracting(OwnerReference::getName)
                .isEqualTo("lifecycle-cluster");
        assertThat(saOwners)
                .first()
                .extracting(OwnerReference::getKind)
                .isEqualTo("VirtualCluster");

        // Create Topic owned by sa
        Topic topic = TestDataBuilder.topic()
                .namespace("default")
                .name("lifecycle-topic")
                .topicName("lifecycle.topic")
                .partitions(6)
                .replicationFactor(3)
                .serviceRef("lifecycle-sa")
                .applicationServiceRef("lifecycle-app")
                .ownedBy(sa)
                .createIn(k8sClient);
        assertThat(topic.getMetadata().getUid())
                .isNotNull();
        syncToStore(topic);

        // Verify ownership
        List<OwnerReference> topicOwners = topic.getMetadata().getOwnerReferences();
        assertThat(topicOwners)
                .isNotNull()
                .hasSize(1);
        assertThat(topicOwners)
                .first()
                .extracting(OwnerReference::getName)
                .isEqualTo("lifecycle-sa");
        assertThat(topicOwners)
                .first()
                .extracting(OwnerReference::getKind)
                .isEqualTo("ServiceAccount");

        // Create ACL owned by sa
        ACL acl = TestDataBuilder.acl()
                .namespace("default")
                .name("lifecycle-acl")
                .serviceRef("lifecycle-sa")
                .topicRef("lifecycle-topic")
                .operations(AclCRSpec.Operation.READ, AclCRSpec.Operation.WRITE)
                .applicationServiceRef("lifecycle-app")
                .ownedBy(sa)
                .createIn(k8sClient);
        assertThat(acl.getMetadata().getUid())
                .isNotNull();
        syncToStore(acl);

        // Verify ownership
        List<OwnerReference> aclOwners = acl.getMetadata().getOwnerReferences();
        assertThat(aclOwners)
                .isNotNull()
                .hasSize(1);
        assertThat(aclOwners)
                .first()
                .extracting(OwnerReference::getName)
                .isEqualTo("lifecycle-sa");
        assertThat(aclOwners)
                .first()
                .extracting(OwnerReference::getKind)
                .isEqualTo("ServiceAccount");

        // Step 2: Update resources

        // Update topic partitions
        topic.getSpec().setPartitions(12);
        Topic updatedTopic = k8sClient.resource(topic).update();
        assertThat(updatedTopic.getSpec().getPartitions())
                .isEqualTo(12);
        store.update("Topic", "default", topic.getMetadata().getName(), updatedTopic);

        // Update ACL operations
        acl.getSpec().setOperations(List.of(AclCRSpec.Operation.READ, AclCRSpec.Operation.WRITE, AclCRSpec.Operation.DESCRIBE));
        ACL updatedAcl = k8sClient.resource(acl).update();
        assertThat(updatedAcl.getSpec().getOperations())
                .hasSize(3);
        assertThat(updatedAcl.getSpec().getOperations())
                .contains(AclCRSpec.Operation.DESCRIBE);
        store.update("ACL", "default", acl.getMetadata().getName(), updatedAcl);

        // Step 3: Verify all resources exist in store
        assertThat(store.list("ApplicationService", "default"))
                .hasSize(1);
        assertThat(store.list("VirtualCluster", "default"))
                .hasSize(1);
        assertThat(store.list("ServiceAccount", "default"))
                .hasSize(1);
        assertThat(store.list("Topic", "default"))
                .hasSize(1);
        assertThat(store.list("ACL", "default"))
                .hasSize(1);

        // Step 4: Delete in reverse order (leaf to root)

        // Delete ACL
        k8sClient.resource(updatedAcl).delete();
        store.delete("ACL", "default", updatedAcl.getMetadata().getName());
        assertThat(store.list("ACL", "default"))
                .hasSize(0);

        // Delete Topic
        k8sClient.resource(updatedTopic).delete();
        store.delete("Topic", "default", updatedTopic.getMetadata().getName());
        assertThat(store.list("Topic", "default"))
                .hasSize(0);

        // Delete ServiceAccount
        k8sClient.resource(sa).delete();
        store.delete("ServiceAccount", "default", sa.getMetadata().getName());
        assertThat(store.list("ServiceAccount", "default"))
                .hasSize(0);

        // Delete VirtualCluster
        k8sClient.resource(vc).delete();
        store.delete("VirtualCluster", "default", vc.getMetadata().getName());
        assertThat(store.list("VirtualCluster", "default"))
                .hasSize(0);

        // Delete ApplicationService
        k8sClient.resource(app).delete();
        store.delete("ApplicationService", "default", app.getMetadata().getName());
        assertThat(store.list("ApplicationService", "default"))
                .hasSize(0);
    }

    @Test
    void testCreateFromYAMLFixture() {
        // Create resources matching ownership-chain-valid.yaml fixture
        // Note: Due to Fabric8 mock server limitations with custom CRDs, we create
        // resources manually using TestDataBuilder, matching the YAML fixture exactly

        // Create ApplicationService (as defined in fixture)
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("orders-app").appName("orders-app").createIn(k8sClient);
        syncToStore(app);

        // Create VirtualCluster (as defined in fixture)
        VirtualCluster vc = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("prod-cluster")
                .clusterId("prod-cluster")
                .applicationServiceRef("orders-app")
                .createIn(k8sClient);
        syncToStore(vc);

        // Create ServiceAccount (as defined in fixture)
        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("orders-sa")
                .saName("orders")
                .dn("CN=orders-sa,OU=TEST,O=EXAMPLE,L=CITY,C=US")
                .clusterRef("prod-cluster")
                .applicationServiceRef("orders-app")
                .createIn(k8sClient);
        syncToStore(sa);

        // Create first Topic (orders-events as defined in fixture)
        Topic createdTopic1 = TestDataBuilder.topic()
                .namespace("default")
                .name("orders-events")
                .topicName("orders.events")
                .partitions(6)
                .replicationFactor(3)
                .serviceRef("orders-sa")
                .applicationServiceRef("orders-app")
                .createIn(k8sClient);
        syncToStore(createdTopic1);

        // Create second Topic (orders-dlq as defined in fixture)
        Topic createdTopic2 = TestDataBuilder.topic()
                .namespace("default")
                .name("orders-dlq")
                .topicName("orders.dlq")
                .partitions(3)
                .replicationFactor(3)
                .serviceRef("orders-sa")
                .applicationServiceRef("orders-app")
                .createIn(k8sClient);
        syncToStore(createdTopic2);

        // Create ACL (as defined in fixture)
        ACL acl = TestDataBuilder.acl()
                .namespace("default")
                .name("orders-read")
                .serviceRef("orders-sa")
                .topicRef("orders-events")
                .operations(AclCRSpec.Operation.READ, AclCRSpec.Operation.DESCRIBE)
                .applicationServiceRef("orders-app")
                .createIn(k8sClient);
        syncToStore(acl);

        // Verify all resources created successfully
        assertThat(store.list("ApplicationService", "default"))
                .hasSize(1);
        assertThat(store.list("VirtualCluster", "default"))
                .hasSize(1);
        assertThat(store.list("ServiceAccount", "default"))
                .hasSize(1);
        assertThat(store.list("Topic", "default"))
                .hasSize(2);
        assertThat(store.list("ACL", "default"))
                .hasSize(1);

        // Verify specific resources by name
        ApplicationService appFromStore = (ApplicationService) store.get("ApplicationService", "default", "orders-app");
        assertThat(appFromStore)
                .isNotNull();
        assertThat(appFromStore.getSpec().getName())
                .isEqualTo("orders-app");

        VirtualCluster vcFromStore = (VirtualCluster) store.get("VirtualCluster", "default", "prod-cluster");
        assertThat(vcFromStore)
                .isNotNull();
        assertThat(vcFromStore.getSpec().getClusterId())
                .isEqualTo("prod-cluster");
        assertThat(vcFromStore.getSpec().getApplicationServiceRef())
                .isEqualTo("orders-app");

        ServiceAccount saFromStore = (ServiceAccount) store.get("ServiceAccount", "default", "orders-sa");
        assertThat(saFromStore)
                .isNotNull();
        assertThat(saFromStore.getSpec().getName())
                .isEqualTo("orders");
        assertThat(saFromStore.getSpec().getClusterRef())
                .isEqualTo("prod-cluster");
        assertThat(saFromStore.getSpec().getApplicationServiceRef())
                .isEqualTo("orders-app");

        Topic topic1 = (Topic) store.get("Topic", "default", "orders-events");
        assertThat(topic1)
                .isNotNull();
        assertThat(topic1.getSpec().getName())
                .isEqualTo("orders.events");
        assertThat(topic1.getSpec().getPartitions())
                .isEqualTo(6);
        assertThat(topic1.getSpec().getReplicationFactor())
                .isEqualTo(3);
        assertThat(topic1.getSpec().getServiceRef())
                .isEqualTo("orders-sa");
        assertThat(topic1.getSpec().getApplicationServiceRef())
                .isEqualTo("orders-app");

        Topic topic2 = (Topic) store.get("Topic", "default", "orders-dlq");
        assertThat(topic2)
                .isNotNull();
        assertThat(topic2.getSpec().getName())
                .isEqualTo("orders.dlq");
        assertThat(topic2.getSpec().getPartitions())
                .isEqualTo(3);
        assertThat(topic2.getSpec().getReplicationFactor())
                .isEqualTo(3);
        assertThat(topic2.getSpec().getServiceRef())
                .isEqualTo("orders-sa");
        assertThat(topic2.getSpec().getApplicationServiceRef())
                .isEqualTo("orders-app");

        ACL aclFromStore = (ACL) store.get("ACL", "default", "orders-read");
        assertThat(aclFromStore)
                .isNotNull();
        assertThat(aclFromStore.getSpec().getServiceRef())
                .isEqualTo("orders-sa");
        assertThat(aclFromStore.getSpec().getTopicRef())
                .isEqualTo("orders-events");
        assertThat(aclFromStore.getSpec().getOperations())
                .hasSize(2)
                .contains(AclCRSpec.Operation.READ, AclCRSpec.Operation.DESCRIBE);
        assertThat(aclFromStore.getSpec().getApplicationServiceRef())
                .isEqualTo("orders-app");
    }
}
