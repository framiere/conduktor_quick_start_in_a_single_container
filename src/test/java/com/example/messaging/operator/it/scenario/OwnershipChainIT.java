package com.example.messaging.operator.it.scenario;

import static org.junit.jupiter.api.Assertions.*;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.it.base.ScenarioITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.validation.ValidationResult;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for ownership chain validation.
 * Tests multi-resource ownership chains and cross-tenant rejection.
 */
public class OwnershipChainIT extends ScenarioITBase {

    @Test
    void testCrossOwnershipRejection() {
        // Create ApplicationService 1
        ApplicationService app1 = TestDataBuilder.applicationService()
                .namespace("default")
                .name("app1")
                .appName("app1")
                .createIn(k8sClient);
        syncToStore(app1);

        // Create ApplicationService 2
        ApplicationService app2 = TestDataBuilder.applicationService()
                .namespace("default")
                .name("app2")
                .appName("app2")
                .createIn(k8sClient);
        syncToStore(app2);

        // Create VirtualCluster owned by app1
        VirtualCluster vc1 = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("cluster1")
                .clusterId("cluster1")
                .applicationServiceRef("app1")
                .ownedBy(app1)
                .createIn(k8sClient);
        syncToStore(vc1);

        // Attempt to create ServiceAccount that references app2 but cluster owned by app1
        // This should be rejected because the cluster belongs to app1, not app2
        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("cross-tenant-sa")
                .saName("cross-tenant")
                .dn("CN=test,OU=TEST,O=EXAMPLE,L=CITY,C=US")
                .clusterRef("cluster1")
                .applicationServiceRef("app2") // Different owner than cluster's owner
                .build();

        // Validate - should REJECT due to cross-tenant ownership
        ValidationResult result = ownershipValidator.validateCreate(sa, "default");
        assertFalse(result.isValid(), "Cross-tenant ServiceAccount should be rejected");
        assertTrue(
                result.getMessage().contains("owned by 'app1', not 'app2'"),
                "Error message should indicate ownership mismatch");
    }

    @Test
    void testTopicRejectsWrongServiceAccountOwner() {
        // Create ApplicationService 1
        ApplicationService app1 = TestDataBuilder.applicationService()
                .namespace("default")
                .name("orders-app")
                .appName("orders-app")
                .createIn(k8sClient);
        syncToStore(app1);

        // Create ApplicationService 2
        ApplicationService app2 = TestDataBuilder.applicationService()
                .namespace("default")
                .name("payments-app")
                .appName("payments-app")
                .createIn(k8sClient);
        syncToStore(app2);

        // Create VirtualCluster for app1
        VirtualCluster vc1 = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("orders-cluster")
                .clusterId("orders-cluster")
                .applicationServiceRef("orders-app")
                .ownedBy(app1)
                .createIn(k8sClient);
        syncToStore(vc1);

        // Create ServiceAccount owned by app1
        ServiceAccount sa1 = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("orders-sa")
                .saName("orders")
                .dn("CN=orders,OU=TEST,O=EXAMPLE,L=CITY,C=US")
                .clusterRef("orders-cluster")
                .applicationServiceRef("orders-app")
                .ownedBy(vc1)
                .createIn(k8sClient);
        syncToStore(sa1);

        // Attempt to create Topic that references app2 but SA owned by app1
        Topic topic = TestDataBuilder.topic()
                .namespace("default")
                .name("cross-tenant-topic")
                .topicName("cross.tenant.topic")
                .partitions(6)
                .replicationFactor(3)
                .serviceRef("orders-sa")
                .applicationServiceRef("payments-app") // Different owner than SA's owner
                .build();

        // Validate - should REJECT due to cross-tenant ownership
        ValidationResult result = ownershipValidator.validateCreate(topic, "default");
        assertFalse(result.isValid(), "Cross-tenant Topic should be rejected");
        assertTrue(
                result.getMessage().contains("owned by 'orders-app', not 'payments-app'"),
                "Error message should indicate ownership mismatch");
    }

    @Test
    void testValidChainFromFixture() {
        // Create complete valid ownership chain matching ownership-chain-valid.yaml fixture

        // Create ApplicationService
        ApplicationService app = TestDataBuilder.applicationService()
                .namespace("default")
                .name("orders-app")
                .appName("orders-app")
                .createIn(k8sClient);
        assertNotNull(app.getMetadata().getUid());
        syncToStore(app);

        // Create VirtualCluster owned by app
        VirtualCluster vc = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("prod-cluster")
                .clusterId("prod-cluster")
                .applicationServiceRef("orders-app")
                .ownedBy(app)
                .createIn(k8sClient);
        assertNotNull(vc.getMetadata().getUid());
        syncToStore(vc);

        // Create ServiceAccount owned by vc
        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("orders-sa")
                .saName("orders")
                .dn("CN=orders-sa,OU=TEST,O=EXAMPLE,L=CITY,C=US")
                .clusterRef("prod-cluster")
                .applicationServiceRef("orders-app")
                .ownedBy(vc)
                .createIn(k8sClient);
        assertNotNull(sa.getMetadata().getUid());
        syncToStore(sa);

        // Create Topic owned by sa
        Topic topic1 = TestDataBuilder.topic()
                .namespace("default")
                .name("orders-events")
                .topicName("orders.events")
                .partitions(6)
                .replicationFactor(3)
                .serviceRef("orders-sa")
                .applicationServiceRef("orders-app")
                .ownedBy(sa)
                .createIn(k8sClient);
        assertNotNull(topic1.getMetadata().getUid());
        syncToStore(topic1);

        // Create second Topic owned by sa
        Topic topic2 = TestDataBuilder.topic()
                .namespace("default")
                .name("orders-dlq")
                .topicName("orders.dlq")
                .partitions(3)
                .replicationFactor(3)
                .serviceRef("orders-sa")
                .applicationServiceRef("orders-app")
                .ownedBy(sa)
                .createIn(k8sClient);
        assertNotNull(topic2.getMetadata().getUid());
        syncToStore(topic2);

        // Create ACL owned by sa
        ACL acl = TestDataBuilder.acl()
                .namespace("default")
                .name("orders-read")
                .serviceRef("orders-sa")
                .topicRef("orders-events")
                .operations(AclCRSpec.Operation.READ, AclCRSpec.Operation.DESCRIBE)
                .applicationServiceRef("orders-app")
                .ownedBy(sa)
                .createIn(k8sClient);
        assertNotNull(acl.getMetadata().getUid());
        syncToStore(acl);

        // Validate all creations should succeed
        ValidationResult vcResult = ownershipValidator.validateCreate(vc, "default");
        assertTrue(vcResult.isValid(), "VirtualCluster creation should be valid");

        ValidationResult saResult = ownershipValidator.validateCreate(sa, "default");
        assertTrue(saResult.isValid(), "ServiceAccount creation should be valid");

        ValidationResult topic1Result = ownershipValidator.validateCreate(topic1, "default");
        assertTrue(topic1Result.isValid(), "Topic1 creation should be valid");

        ValidationResult topic2Result = ownershipValidator.validateCreate(topic2, "default");
        assertTrue(topic2Result.isValid(), "Topic2 creation should be valid");

        ValidationResult aclResult = ownershipValidator.validateCreate(acl, "default");
        assertTrue(aclResult.isValid(), "ACL creation should be valid");

        // Verify all resources exist in store
        assertEquals(1, store.list("ApplicationService", "default").size());
        assertEquals(1, store.list("VirtualCluster", "default").size());
        assertEquals(1, store.list("ServiceAccount", "default").size());
        assertEquals(2, store.list("Topic", "default").size());
        assertEquals(1, store.list("ACL", "default").size());

        // Verify ownership chain integrity
        ApplicationService appFromStore = (ApplicationService) store.get("ApplicationService", "default", "orders-app");
        assertNotNull(appFromStore);
        assertEquals("orders-app", appFromStore.getSpec().getName());

        VirtualCluster vcFromStore = (VirtualCluster) store.get("VirtualCluster", "default", "prod-cluster");
        assertNotNull(vcFromStore);
        assertEquals("orders-app", vcFromStore.getSpec().getApplicationServiceRef());

        ServiceAccount saFromStore = (ServiceAccount) store.get("ServiceAccount", "default", "orders-sa");
        assertNotNull(saFromStore);
        assertEquals("orders-app", saFromStore.getSpec().getApplicationServiceRef());

        Topic topic1FromStore = (Topic) store.get("Topic", "default", "orders-events");
        assertNotNull(topic1FromStore);
        assertEquals("orders-app", topic1FromStore.getSpec().getApplicationServiceRef());

        ACL aclFromStore = (ACL) store.get("ACL", "default", "orders-read");
        assertNotNull(aclFromStore);
        assertEquals("orders-app", aclFromStore.getSpec().getApplicationServiceRef());
    }
}
