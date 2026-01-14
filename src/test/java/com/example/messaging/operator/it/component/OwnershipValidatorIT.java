package com.example.messaging.operator.it.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.it.base.ComponentITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.validation.ValidationResult;
import org.junit.jupiter.api.Test;

/**
 * Component integration tests for OwnershipValidator with Kubernetes mock server. Tests ownership validation with resources from K8s.
 */
public class OwnershipValidatorIT extends ComponentITBase {

    @Test
    void testValidateOwnershipChainFromK8s() {
        // Create full ownership chain in K8s: ApplicationService -> VirtualCluster -> ServiceAccount -> Topic
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("test-app").appName("test-app").createIn(k8sClient);

        VirtualCluster cluster = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("test-cluster")
                .clusterId("test-cluster-id")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);

        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("test-sa")
                .saName("test-sa")
                .clusterRef("test-cluster")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);

        // Sync all to store
        syncAllToStore();

        // Validate CREATE for Topic with complete ownership chain
        Topic topic = TestDataBuilder.topic()
                .namespace("default")
                .name("test-topic")
                .topicName("test-topic")
                .serviceRef("test-sa")
                .applicationServiceRef("test-app")
                .build();

        ValidationResult result = ownershipValidator.validateCreate(topic, "default");

        // Verify validation succeeds
        assertThat(result.isValid()).isTrue();
        assertThat(result.getMessage()).isNull();
    }

    @Test
    void testRejectMissingServiceAccount() {
        // Create ApplicationService in K8s
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("test-app").appName("test-app").createIn(k8sClient);

        // Sync to store
        syncAllToStore();

        // Try to create Topic referencing non-existent ServiceAccount
        Topic topic = TestDataBuilder.topic()
                .namespace("default")
                .name("test-topic")
                .topicName("test-topic")
                .serviceRef("missing-sa")
                .applicationServiceRef("test-app")
                .build();

        ValidationResult result = ownershipValidator.validateCreate(topic, "default");

        // Verify validation fails
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("Referenced ServiceAccount 'missing-sa' does not exist");
    }

    @Test
    void testRejectWrongVirtualClusterOwner() {
        // Create two ApplicationServices in K8s
        ApplicationService app1 = TestDataBuilder.applicationService().namespace("default").name("app1").appName("app1").createIn(k8sClient);

        ApplicationService app2 = TestDataBuilder.applicationService().namespace("default").name("app2").appName("app2").createIn(k8sClient);

        // Create VirtualCluster owned by app1
        VirtualCluster cluster = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("test-cluster")
                .clusterId("test-cluster-id")
                .applicationServiceRef("app1")
                .createIn(k8sClient);

        // Sync all to store
        syncAllToStore();

        // Try to create ServiceAccount referencing cluster but with different owner (app2)
        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .namespace("default")
                .name("test-sa")
                .saName("test-sa")
                .clusterRef("test-cluster")
                .applicationServiceRef("app2") // Different owner!
                .build();

        ValidationResult result = ownershipValidator.validateCreate(sa, "default");

        // Verify validation fails
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("VirtualCluster 'test-cluster' is owned by 'app1', not 'app2'");
    }

    @Test
    void testRejectOwnershipChange() {
        // Create two ApplicationServices in K8s
        ApplicationService app1 = TestDataBuilder.applicationService().namespace("default").name("app1").appName("app1").createIn(k8sClient);

        ApplicationService app2 = TestDataBuilder.applicationService().namespace("default").name("app2").appName("app2").createIn(k8sClient);

        // Create VirtualCluster owned by app1 in K8s
        VirtualCluster cluster = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("test-cluster")
                .clusterId("test-cluster-id")
                .applicationServiceRef("app1")
                .createIn(k8sClient);

        // Sync to store
        syncAllToStore();

        // Get existing cluster from store
        VirtualCluster existingCluster = store.get("VirtualCluster", "default", "test-cluster");
        assertThat(existingCluster).isNotNull();

        // Try to change ownership to app2
        VirtualCluster updatedCluster = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("test-cluster")
                .clusterId("test-cluster-id")
                .applicationServiceRef("app2") // Attempting ownership change
                .build();

        ValidationResult result = ownershipValidator.validateUpdate(existingCluster, updatedCluster);

        // Verify validation fails
        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("Cannot change applicationServiceRef from 'app1' to 'app2'")
                .contains("Only the original owner can modify this resource");
    }

    @Test
    void testAllowUpdateSameOwner() {
        // Create ApplicationService in K8s
        ApplicationService app = TestDataBuilder.applicationService().namespace("default").name("test-app").appName("test-app").createIn(k8sClient);

        // Create VirtualCluster in K8s
        VirtualCluster cluster = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("test-cluster")
                .clusterId("original-cluster-id")
                .applicationServiceRef("test-app")
                .createIn(k8sClient);

        // Sync to store
        syncAllToStore();

        // Get existing cluster from store
        VirtualCluster existingCluster = store.get("VirtualCluster", "default", "test-cluster");
        assertThat(existingCluster).isNotNull();
        assertThat(existingCluster.getSpec().getClusterId()).isEqualTo("original-cluster-id");

        // Update cluster spec but keep same owner
        VirtualCluster updatedCluster = TestDataBuilder.virtualCluster()
                .namespace("default")
                .name("test-cluster")
                .clusterId("updated-cluster-id") // Changed spec
                .applicationServiceRef("test-app") // Same owner
                .build();

        ValidationResult result = ownershipValidator.validateUpdate(existingCluster, updatedCluster);

        // Verify validation succeeds
        assertThat(result.isValid()).isTrue();
        assertThat(result.getMessage()).isNull();
    }
}
