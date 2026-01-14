package com.example.messaging.operator.it.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.it.base.ScenarioITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.validation.ValidationResult;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for ownership chain validation. Tests multi-resource ownership chains and cross-tenant rejection.
 */
public class OwnershipChainIT extends ScenarioITBase {

    @Test
    void testCrossOwnershipRejection() {
        // Create ApplicationService 1
        ApplicationService app1 = TestDataBuilder.applicationService().namespace("default").name("app1").appName("app1").createIn(k8sClient);
        syncToStore(app1);

        // Create ApplicationService 2
        ApplicationService app2 = TestDataBuilder.applicationService().namespace("default").name("app2").appName("app2").createIn(k8sClient);
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
        assertThat(result.isValid())
                .as("Cross-tenant ServiceAccount should be rejected")
                .isFalse();
        assertThat(result.getMessage())
                .as("Error message should indicate ownership mismatch")
                .contains("owned by 'app1', not 'app2'");
    }

    @Test
    void testTopicRejectsWrongServiceAccountOwner() {
        // Create ApplicationService 1
        ApplicationService app1 = TestDataBuilder.applicationService().namespace("default").name("orders-app").appName("orders-app").createIn(k8sClient);
        syncToStore(app1);

        // Create ApplicationService 2
        ApplicationService app2 = TestDataBuilder.applicationService().namespace("default").name("payments-app").appName("payments-app").createIn(k8sClient);
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
        assertThat(result.isValid())
                .as("Cross-tenant Topic should be rejected")
                .isFalse();
        assertThat(result.getMessage())
                .as("Error message should indicate ownership mismatch")
                .contains("owned by 'orders-app', not 'payments-app'");
    }

    @Test
    void testValidChainFromFixture() {
        // Create complete valid ownership chain matching ownership-chain-valid.yaml fixture

        // Create ApplicationService
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("orders-app").appName("orders-app").createIn(k8sClient);
        assertThat(app.getMetadata().getUid())
                .isNotNull();
        syncToStore(app);

        // Create VirtualCluster owned by app
        VirtualCluster vc = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("prod-cluster")
                .clusterId("prod-cluster")
                .applicationServiceRef("orders-app")
                .ownedBy(app)
                .createIn(k8sClient);
        assertThat(vc.getMetadata().getUid())
                .isNotNull();
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
        assertThat(sa.getMetadata().getUid())
                .isNotNull();
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
        assertThat(topic1.getMetadata().getUid())
                .isNotNull();
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
        assertThat(topic2.getMetadata().getUid())
                .isNotNull();
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
        assertThat(acl.getMetadata().getUid())
                .isNotNull();
        syncToStore(acl);

        // Validate all creations should succeed
        ValidationResult vcResult = ownershipValidator.validateCreate(vc, "default");
        assertThat(vcResult.isValid())
                .as("VirtualCluster creation should be valid")
                .isTrue();

        ValidationResult saResult = ownershipValidator.validateCreate(sa, "default");
        assertThat(saResult.isValid())
                .as("ServiceAccount creation should be valid")
                .isTrue();

        ValidationResult topic1Result = ownershipValidator.validateCreate(topic1, "default");
        assertThat(topic1Result.isValid())
                .as("Topic1 creation should be valid")
                .isTrue();

        ValidationResult topic2Result = ownershipValidator.validateCreate(topic2, "default");
        assertThat(topic2Result.isValid())
                .as("Topic2 creation should be valid")
                .isTrue();

        ValidationResult aclResult = ownershipValidator.validateCreate(acl, "default");
        assertThat(aclResult.isValid())
                .as("ACL creation should be valid")
                .isTrue();

        // Verify all resources exist in store
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

        // Verify ownership chain integrity
        ApplicationService appFromStore = (ApplicationService) store.get("ApplicationService", "default", "orders-app");
        assertThat(appFromStore)
                .isNotNull();
        assertThat(appFromStore.getSpec().getName())
                .isEqualTo("orders-app");

        VirtualCluster vcFromStore = (VirtualCluster) store.get("VirtualCluster", "default", "prod-cluster");
        assertThat(vcFromStore)
                .isNotNull();
        assertThat(vcFromStore.getSpec().getApplicationServiceRef())
                .isEqualTo("orders-app");

        ServiceAccount saFromStore = (ServiceAccount) store.get("ServiceAccount", "default", "orders-sa");
        assertThat(saFromStore)
                .isNotNull();
        assertThat(saFromStore.getSpec().getApplicationServiceRef())
                .isEqualTo("orders-app");

        Topic topic1FromStore = (Topic) store.get("Topic", "default", "orders-events");
        assertThat(topic1FromStore)
                .isNotNull();
        assertThat(topic1FromStore.getSpec().getApplicationServiceRef())
                .isEqualTo("orders-app");

        ACL aclFromStore = (ACL) store.get("ACL", "default", "orders-read");
        assertThat(aclFromStore)
                .isNotNull();
        assertThat(aclFromStore.getSpec().getApplicationServiceRef())
                .isEqualTo("orders-app");
    }
}
