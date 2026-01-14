package com.example.messaging.operator.it.scenario;

import static org.junit.jupiter.api.Assertions.*;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.it.base.ScenarioITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for complete CRD lifecycle scenarios.
 * Tests creating complete ownership chains, updates, and deletions.
 */
public class CRDLifecycleIT extends ScenarioITBase {

    @Test
    void testCompleteLifecycle() {
        // Step 1: Create complete ownership chain: app → vc → sa → topic → acl

        // Create ApplicationService
        ApplicationService app = TestDataBuilder.applicationService()
                .namespace("default")
                .name("lifecycle-app")
                .appName("lifecycle-app")
                .createIn(k8sClient);
        assertNotNull(app.getMetadata().getUid());
        syncToStore(app);

        // Create VirtualCluster owned by app
        VirtualCluster vc = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("lifecycle-cluster")
                .clusterId("lifecycle-cluster-id")
                .applicationServiceRef("lifecycle-app")
                .ownedBy(app)
                .createIn(k8sClient);
        assertNotNull(vc.getMetadata().getUid());
        syncToStore(vc);

        // Verify ownership
        List<OwnerReference> vcOwners = vc.getMetadata().getOwnerReferences();
        assertNotNull(vcOwners);
        assertEquals(1, vcOwners.size());
        assertEquals("lifecycle-app", vcOwners.get(0).getName());
        assertEquals("ApplicationService", vcOwners.get(0).getKind());

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
        assertNotNull(sa.getMetadata().getUid());
        syncToStore(sa);

        // Verify ownership
        List<OwnerReference> saOwners = sa.getMetadata().getOwnerReferences();
        assertNotNull(saOwners);
        assertEquals(1, saOwners.size());
        assertEquals("lifecycle-cluster", saOwners.get(0).getName());
        assertEquals("VirtualCluster", saOwners.get(0).getKind());

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
        assertNotNull(topic.getMetadata().getUid());
        syncToStore(topic);

        // Verify ownership
        List<OwnerReference> topicOwners = topic.getMetadata().getOwnerReferences();
        assertNotNull(topicOwners);
        assertEquals(1, topicOwners.size());
        assertEquals("lifecycle-sa", topicOwners.get(0).getName());
        assertEquals("ServiceAccount", topicOwners.get(0).getKind());

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
        assertNotNull(acl.getMetadata().getUid());
        syncToStore(acl);

        // Verify ownership
        List<OwnerReference> aclOwners = acl.getMetadata().getOwnerReferences();
        assertNotNull(aclOwners);
        assertEquals(1, aclOwners.size());
        assertEquals("lifecycle-sa", aclOwners.get(0).getName());
        assertEquals("ServiceAccount", aclOwners.get(0).getKind());

        // Step 2: Update resources

        // Update topic partitions
        topic.getSpec().setPartitions(12);
        Topic updatedTopic = k8sClient.resource(topic).update();
        assertEquals(12, updatedTopic.getSpec().getPartitions());
        store.update("Topic", "default", topic.getMetadata().getName(), updatedTopic);

        // Update ACL operations
        acl.getSpec()
                .setOperations(
                        List.of(AclCRSpec.Operation.READ, AclCRSpec.Operation.WRITE, AclCRSpec.Operation.DESCRIBE));
        ACL updatedAcl = k8sClient.resource(acl).update();
        assertEquals(3, updatedAcl.getSpec().getOperations().size());
        assertTrue(updatedAcl.getSpec().getOperations().contains(AclCRSpec.Operation.DESCRIBE));
        store.update("ACL", "default", acl.getMetadata().getName(), updatedAcl);

        // Step 3: Verify all resources exist in store
        assertEquals(1, store.list("ApplicationService", "default").size());
        assertEquals(1, store.list("VirtualCluster", "default").size());
        assertEquals(1, store.list("ServiceAccount", "default").size());
        assertEquals(1, store.list("Topic", "default").size());
        assertEquals(1, store.list("ACL", "default").size());

        // Step 4: Delete in reverse order (leaf to root)

        // Delete ACL
        k8sClient.resource(updatedAcl).delete();
        store.delete("ACL", "default", updatedAcl.getMetadata().getName());
        assertEquals(0, store.list("ACL", "default").size());

        // Delete Topic
        k8sClient.resource(updatedTopic).delete();
        store.delete("Topic", "default", updatedTopic.getMetadata().getName());
        assertEquals(0, store.list("Topic", "default").size());

        // Delete ServiceAccount
        k8sClient.resource(sa).delete();
        store.delete("ServiceAccount", "default", sa.getMetadata().getName());
        assertEquals(0, store.list("ServiceAccount", "default").size());

        // Delete VirtualCluster
        k8sClient.resource(vc).delete();
        store.delete("VirtualCluster", "default", vc.getMetadata().getName());
        assertEquals(0, store.list("VirtualCluster", "default").size());

        // Delete ApplicationService
        k8sClient.resource(app).delete();
        store.delete("ApplicationService", "default", app.getMetadata().getName());
        assertEquals(0, store.list("ApplicationService", "default").size());
    }

    @Test
    void testCreateFromYAMLFixture() {
        // Create resources matching ownership-chain-valid.yaml fixture
        // Note: Due to Fabric8 mock server limitations with custom CRDs, we create
        // resources manually using TestDataBuilder, matching the YAML fixture exactly

        // Create ApplicationService (as defined in fixture)
        ApplicationService app = TestDataBuilder.applicationService()
                .namespace("default")
                .name("orders-app")
                .appName("orders-app")
                .createIn(k8sClient);
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
        assertEquals(1, store.list("ApplicationService", "default").size());
        assertEquals(1, store.list("VirtualCluster", "default").size());
        assertEquals(1, store.list("ServiceAccount", "default").size());
        assertEquals(2, store.list("Topic", "default").size());
        assertEquals(1, store.list("ACL", "default").size());

        // Verify specific resources by name
        ApplicationService appFromStore = (ApplicationService) store.get("ApplicationService", "default", "orders-app");
        assertNotNull(appFromStore);
        assertEquals("orders-app", appFromStore.getSpec().getName());

        VirtualCluster vcFromStore = (VirtualCluster) store.get("VirtualCluster", "default", "prod-cluster");
        assertNotNull(vcFromStore);
        assertEquals("prod-cluster", vcFromStore.getSpec().getClusterId());
        assertEquals("orders-app", vcFromStore.getSpec().getApplicationServiceRef());

        ServiceAccount saFromStore = (ServiceAccount) store.get("ServiceAccount", "default", "orders-sa");
        assertNotNull(saFromStore);
        assertEquals("orders", saFromStore.getSpec().getName());
        assertEquals("prod-cluster", saFromStore.getSpec().getClusterRef());
        assertEquals("orders-app", saFromStore.getSpec().getApplicationServiceRef());

        Topic topic1 = (Topic) store.get("Topic", "default", "orders-events");
        assertNotNull(topic1);
        assertEquals("orders.events", topic1.getSpec().getName());
        assertEquals(6, topic1.getSpec().getPartitions());
        assertEquals(3, topic1.getSpec().getReplicationFactor());
        assertEquals("orders-sa", topic1.getSpec().getServiceRef());
        assertEquals("orders-app", topic1.getSpec().getApplicationServiceRef());

        Topic topic2 = (Topic) store.get("Topic", "default", "orders-dlq");
        assertNotNull(topic2);
        assertEquals("orders.dlq", topic2.getSpec().getName());
        assertEquals(3, topic2.getSpec().getPartitions());
        assertEquals(3, topic2.getSpec().getReplicationFactor());
        assertEquals("orders-sa", topic2.getSpec().getServiceRef());
        assertEquals("orders-app", topic2.getSpec().getApplicationServiceRef());

        ACL aclFromStore = (ACL) store.get("ACL", "default", "orders-read");
        assertNotNull(aclFromStore);
        assertEquals("orders-sa", aclFromStore.getSpec().getServiceRef());
        assertEquals("orders-events", aclFromStore.getSpec().getTopicRef());
        assertEquals(2, aclFromStore.getSpec().getOperations().size());
        assertTrue(aclFromStore.getSpec().getOperations().contains(AclCRSpec.Operation.READ));
        assertTrue(aclFromStore.getSpec().getOperations().contains(AclCRSpec.Operation.DESCRIBE));
        assertEquals("orders-app", aclFromStore.getSpec().getApplicationServiceRef());
    }
}
