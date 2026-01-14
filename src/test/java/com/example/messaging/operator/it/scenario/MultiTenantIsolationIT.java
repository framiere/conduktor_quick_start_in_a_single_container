package com.example.messaging.operator.it.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.it.base.ScenarioITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.validation.ValidationResult;
import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for multi-tenant isolation and cross-tenant protection. Tests that tenants cannot interfere with each other's resources.
 */
public class MultiTenantIsolationIT extends ScenarioITBase {

    /**
     * Helper method to setup multi-tenant environment matching multi-tenant-scenario.yaml Creates two independent application tenants (app1 and app2) with their own
     * resources.
     */
    private void setupMultiTenantEnvironment() {
        // Create ApplicationService app1
        ApplicationService app1 = TestDataBuilder.applicationService().namespace("default").name("app1").appName("app1").createIn(k8sClient);
        syncToStore(app1);

        // Create VirtualCluster for app1
        VirtualCluster vc1 = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("vc1")
                .clusterId("vc1")
                .applicationServiceRef("app1")
                .ownedBy(app1)
                .createIn(k8sClient);
        syncToStore(vc1);

        // Create ServiceAccount for app1
        ServiceAccount sa1 = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("sa-app1")
                .saName("app1-sa")
                .dn("CN=sa-app1,OU=TEST,O=EXAMPLE")
                .clusterRef("vc1")
                .applicationServiceRef("app1")
                .ownedBy(vc1)
                .createIn(k8sClient);
        syncToStore(sa1);

        // Create Topic for app1
        Topic topic1 = TestDataBuilder.topic()
                .namespace("default")
                .name("orders-events")
                .topicName("orders.events")
                .partitions(3)
                .replicationFactor(3)
                .serviceRef("sa-app1")
                .applicationServiceRef("app1")
                .ownedBy(sa1)
                .createIn(k8sClient);
        syncToStore(topic1);

        // Create ApplicationService app2
        ApplicationService app2 = TestDataBuilder.applicationService().namespace("default").name("app2").appName("app2").createIn(k8sClient);
        syncToStore(app2);

        // Create VirtualCluster for app2
        VirtualCluster vc2 = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("vc2")
                .clusterId("vc2")
                .applicationServiceRef("app2")
                .ownedBy(app2)
                .createIn(k8sClient);
        syncToStore(vc2);

        // Create ServiceAccount for app2
        ServiceAccount sa2 = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("sa-app2")
                .saName("app2-sa")
                .dn("CN=sa-app2,OU=TEST,O=EXAMPLE")
                .clusterRef("vc2")
                .applicationServiceRef("app2")
                .ownedBy(vc2)
                .createIn(k8sClient);
        syncToStore(sa2);

        // Create Topic for app2
        Topic topic2 = TestDataBuilder.topic()
                .namespace("default")
                .name("payments-events")
                .topicName("payments.events")
                .partitions(3)
                .replicationFactor(3)
                .serviceRef("sa-app2")
                .applicationServiceRef("app2")
                .ownedBy(sa2)
                .createIn(k8sClient);
        syncToStore(topic2);
    }

    @Test
    void testMultiTenantFixtureIsolation() {
        // Setup multi-tenant environment
        setupMultiTenantEnvironment();

        // Verify both tenants exist in store
        List<HasMetadata> apps = store.list("ApplicationService", "default");
        assertThat(apps)
                .as("Should have 2 ApplicationServices")
                .hasSize(2);

        List<HasMetadata> vcs = store.list("VirtualCluster", "default");
        assertThat(vcs)
                .as("Should have 2 VirtualClusters")
                .hasSize(2);

        List<HasMetadata> sas = store.list("ServiceAccount", "default");
        assertThat(sas)
                .as("Should have 2 ServiceAccounts")
                .hasSize(2);

        List<HasMetadata> topics = store.list("Topic", "default");
        assertThat(topics)
                .as("Should have 2 Topics")
                .hasSize(2);

        // Verify app1 resources
        ApplicationService app1 = (ApplicationService) store.get("ApplicationService", "default", "app1");
        assertThat(app1)
                .isNotNull();
        assertThat(app1.getSpec().getName())
                .isEqualTo("app1");

        VirtualCluster vc1 = (VirtualCluster) store.get("VirtualCluster", "default", "vc1");
        assertThat(vc1)
                .isNotNull();
        assertThat(vc1.getSpec().getApplicationServiceRef())
                .isEqualTo("app1");

        ServiceAccount sa1 = (ServiceAccount) store.get("ServiceAccount", "default", "sa-app1");
        assertThat(sa1)
                .isNotNull();
        assertThat(sa1.getSpec().getApplicationServiceRef())
                .isEqualTo("app1");

        Topic topic1 = (Topic) store.get("Topic", "default", "orders-events");
        assertThat(topic1)
                .isNotNull();
        assertThat(topic1.getSpec().getApplicationServiceRef())
                .isEqualTo("app1");

        // Verify app2 resources
        ApplicationService app2 = (ApplicationService) store.get("ApplicationService", "default", "app2");
        assertThat(app2)
                .isNotNull();
        assertThat(app2.getSpec().getName())
                .isEqualTo("app2");

        VirtualCluster vc2 = (VirtualCluster) store.get("VirtualCluster", "default", "vc2");
        assertThat(vc2)
                .isNotNull();
        assertThat(vc2.getSpec().getApplicationServiceRef())
                .isEqualTo("app2");

        ServiceAccount sa2 = (ServiceAccount) store.get("ServiceAccount", "default", "sa-app2");
        assertThat(sa2)
                .isNotNull();
        assertThat(sa2.getSpec().getApplicationServiceRef())
                .isEqualTo("app2");

        Topic topic2 = (Topic) store.get("Topic", "default", "payments-events");
        assertThat(topic2)
                .isNotNull();
        assertThat(topic2.getSpec().getApplicationServiceRef())
                .isEqualTo("app2");
    }

    @Test
    void testCrossTenantUpdateDenied() {
        // Setup multi-tenant environment
        setupMultiTenantEnvironment();

        // Get app2's topic from k8s (to ensure we have the latest version)
        Topic app2Topic = k8sClient.resources(Topic.class).inNamespace("default").withName("payments-events").get();
        assertThat(app2Topic)
                .isNotNull();
        assertThat(app2Topic.getSpec().getApplicationServiceRef())
                .isEqualTo("app2");

        // Attempt to update app2's topic to reference app1 (ownership change)
        Topic updatedTopic = new Topic();
        updatedTopic.setMetadata(app2Topic.getMetadata());
        updatedTopic.setSpec(new TopicCRSpec());
        updatedTopic.getSpec().setName(app2Topic.getSpec().getName());
        updatedTopic.getSpec().setPartitions(app2Topic.getSpec().getPartitions());
        updatedTopic.getSpec().setReplicationFactor(app2Topic.getSpec().getReplicationFactor());
        updatedTopic.getSpec().setServiceRef(app2Topic.getSpec().getServiceRef());
        updatedTopic.getSpec().setApplicationServiceRef("app1"); // Try to change ownership to app1

        // Validate update - should REJECT due to ownership change attempt
        ValidationResult result = ownershipValidator.validateUpdate(updatedTopic, "default");
        assertThat(result.isValid())
                .as("Cross-tenant ownership change should be rejected: " + result.getMessage())
                .isFalse();
        assertThat(result.getMessage().contains("applicationServiceRef") || result.getMessage().contains("ownership"))
                .as("Error message should indicate ownership change is not allowed. Got: " + result.getMessage())
                .isTrue();
    }

    @Test
    void testCrossTenantReferenceDenied() {
        // Setup multi-tenant environment
        setupMultiTenantEnvironment();

        // Attempt to create Topic for app1 that references app2's ServiceAccount
        Topic crossTenantTopic = TestDataBuilder.topic()
                .namespace("default")
                .name("cross-tenant-topic")
                .topicName("cross.tenant.topic")
                .partitions(3)
                .replicationFactor(3)
                .serviceRef("sa-app2") // App2's ServiceAccount
                .applicationServiceRef("app1") // But belongs to app1
                .build();

        // Validate - should REJECT due to cross-tenant ServiceAccount reference
        ValidationResult result = ownershipValidator.validateCreate(crossTenantTopic, "default");
        assertThat(result.isValid())
                .as("Cross-tenant ServiceAccount reference should be rejected")
                .isFalse();
        assertThat(result.getMessage().contains("owned by 'app2', not 'app1'"))
                .as("Error message should indicate ServiceAccount ownership mismatch")
                .isTrue();
    }

    @Test
    void testIndependentTenantOperations() {
        // Setup multi-tenant environment
        setupMultiTenantEnvironment();

        // App1 creates a new topic - should succeed
        Topic app1NewTopic = TestDataBuilder.topic()
                .namespace("default")
                .name("orders-dlq")
                .topicName("orders.dlq")
                .partitions(1)
                .replicationFactor(3)
                .serviceRef("sa-app1")
                .applicationServiceRef("app1")
                .build();

        ValidationResult app1Result = ownershipValidator.validateCreate(app1NewTopic, "default");
        assertThat(app1Result.isValid())
                .as("App1 should be able to create its own topic")
                .isTrue();

        // Create the topic in k8s and store
        ServiceAccount sa1 = (ServiceAccount) store.get("ServiceAccount", "default", "sa-app1");
        Topic createdApp1Topic = TestDataBuilder.topic()
                .namespace("default")
                .name("orders-dlq")
                .topicName("orders.dlq")
                .partitions(1)
                .replicationFactor(3)
                .serviceRef("sa-app1")
                .applicationServiceRef("app1")
                .ownedBy(sa1)
                .createIn(k8sClient);
        syncToStore(createdApp1Topic);

        // App2 creates a new topic - should succeed
        Topic app2NewTopic = TestDataBuilder.topic()
                .namespace("default")
                .name("payments-dlq")
                .topicName("payments.dlq")
                .partitions(1)
                .replicationFactor(3)
                .serviceRef("sa-app2")
                .applicationServiceRef("app2")
                .build();

        ValidationResult app2Result = ownershipValidator.validateCreate(app2NewTopic, "default");
        assertThat(app2Result.isValid())
                .as("App2 should be able to create its own topic")
                .isTrue();

        // Create the topic in k8s and store
        ServiceAccount sa2 = (ServiceAccount) store.get("ServiceAccount", "default", "sa-app2");
        Topic createdApp2Topic = TestDataBuilder.topic()
                .namespace("default")
                .name("payments-dlq")
                .topicName("payments.dlq")
                .partitions(1)
                .replicationFactor(3)
                .serviceRef("sa-app2")
                .applicationServiceRef("app2")
                .ownedBy(sa2)
                .createIn(k8sClient);
        syncToStore(createdApp2Topic);

        // Verify both tenants have 2 topics each
        List<HasMetadata> allTopics = store.list("Topic", "default");
        assertThat(allTopics)
                .as("Should have 4 topics total (2 per tenant)")
                .hasSize(4);

        // Verify app1 topics
        long app1TopicCount = allTopics.stream().map(t -> (Topic) t).filter(t -> "app1".equals(t.getSpec().getApplicationServiceRef())).count();
        assertThat(app1TopicCount)
                .as("App1 should have 2 topics")
                .isEqualTo(2);

        // Verify app2 topics
        long app2TopicCount = allTopics.stream().map(t -> (Topic) t).filter(t -> "app2".equals(t.getSpec().getApplicationServiceRef())).count();
        assertThat(app2TopicCount)
                .as("App2 should have 2 topics")
                .isEqualTo(2);

        // App1 updates its own topic - should succeed
        Topic app1Topic = k8sClient.resources(Topic.class).inNamespace("default").withName("orders-events").get();
        app1Topic.getSpec().setPartitions(6);
        Topic updatedApp1Topic = k8sClient.resource(app1Topic).update();
        assertThat(updatedApp1Topic.getSpec().getPartitions())
                .isEqualTo(6);
        store.update("Topic", "default", app1Topic.getMetadata().getName(), updatedApp1Topic);

        // App2 updates its own topic - should succeed
        Topic app2Topic = k8sClient.resources(Topic.class).inNamespace("default").withName("payments-events").get();
        app2Topic.getSpec().setPartitions(6);
        Topic updatedApp2Topic = k8sClient.resource(app2Topic).update();
        assertThat(updatedApp2Topic.getSpec().getPartitions())
                .isEqualTo(6);
        store.update("Topic", "default", app2Topic.getMetadata().getName(), updatedApp2Topic);

        // Verify both updates succeeded independently
        Topic verifyApp1 = (Topic) store.get("Topic", "default", "orders-events");
        assertThat(verifyApp1.getSpec().getPartitions())
                .isEqualTo(6);

        Topic verifyApp2 = (Topic) store.get("Topic", "default", "payments-events");
        assertThat(verifyApp2.getSpec().getPartitions())
                .isEqualTo(6);
    }
}
