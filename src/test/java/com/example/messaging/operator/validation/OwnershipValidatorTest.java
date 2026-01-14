package com.example.messaging.operator.validation;

import static org.assertj.core.api.Assertions.*;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.store.CRDStore;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;

/**
 * Unit tests for OwnershipValidator. Tests validation logic for CREATE, UPDATE, DELETE operations. Validates ownership chains and authorization rules.
 */
@DisplayName("OwnershipValidator Unit Tests")
class OwnershipValidatorTest {

    private CRDStore store;
    private OwnershipValidator validator;
    private static final String NAMESPACE = "test-namespace";
    private static final String APP_SERVICE = "orders-service";
    private static final String OTHER_APP_SERVICE = "payments-service";
    private static final String CLUSTER_ID = "prod-cluster";
    private static final String SERVICE_ACCOUNT = "orders-sa";

    @BeforeEach
    void setup() {
        store = new CRDStore();
        validator = new OwnershipValidator(store);
    }

    @AfterEach
    void cleanup() {
        store.clear();
    }

    // ==================== CREATE VALIDATION TESTS ====================

    @Nested
    @DisplayName("CREATE Validation Tests")
    class CreateValidationTests {

        @Test
        @DisplayName("should allow ApplicationService creation without validation")
        void testApplicationServiceCreateAlwaysValid() {
            ApplicationService appService = buildApplicationService(APP_SERVICE);

            ValidationResult result = validator.validateCreate(appService, NAMESPACE);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).isNull();
        }

        @Test
        @DisplayName("should allow VirtualCluster when ApplicationService exists")
        void testVirtualClusterCreateWithExistingAppService() {
            // Setup
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));

            // Test
            VirtualCluster vCluster = buildVirtualCluster(CLUSTER_ID, APP_SERVICE);
            ValidationResult result = validator.validateCreate(vCluster, NAMESPACE);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should reject VirtualCluster when ApplicationService does not exist")
        void testVirtualClusterCreateWithMissingAppService() {
            VirtualCluster vCluster = buildVirtualCluster(CLUSTER_ID, "nonexistent-app-service");

            ValidationResult result = validator.validateCreate(vCluster, NAMESPACE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("ApplicationService").contains("nonexistent-app-service").contains("does not exist");
        }

        @Test
        @DisplayName("should allow ServiceAccount when ApplicationService and VirtualCluster exist")
        void testServiceAccountCreateWithValidChain() {
            // Setup ownership chain
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
            store.create("VirtualCluster", NAMESPACE, buildVirtualCluster(CLUSTER_ID, APP_SERVICE));

            // Test
            ServiceAccount sa = buildServiceAccount(SERVICE_ACCOUNT, CLUSTER_ID, APP_SERVICE);
            ValidationResult result = validator.validateCreate(sa, NAMESPACE);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should reject ServiceAccount when ApplicationService does not exist")
        void testServiceAccountCreateWithMissingAppService() {
            ServiceAccount sa = buildServiceAccount(SERVICE_ACCOUNT, CLUSTER_ID, "nonexistent-app");

            ValidationResult result = validator.validateCreate(sa, NAMESPACE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("ApplicationService").contains("does not exist");
        }

        @Test
        @DisplayName("should reject ServiceAccount when VirtualCluster does not exist")
        void testServiceAccountCreateWithMissingVirtualCluster() {
            // Setup only ApplicationService
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));

            // Test
            ServiceAccount sa = buildServiceAccount(SERVICE_ACCOUNT, "nonexistent-cluster", APP_SERVICE);
            ValidationResult result = validator.validateCreate(sa, NAMESPACE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("VirtualCluster").contains("does not exist");
        }

        @Test
        @DisplayName("should reject ServiceAccount when VirtualCluster is owned by different ApplicationService")
        void testServiceAccountCreateWithWrongOwner() {
            // Setup: VirtualCluster owned by OTHER_APP_SERVICE
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
            store.create("ApplicationService", NAMESPACE, buildApplicationService(OTHER_APP_SERVICE));
            store.create("VirtualCluster", NAMESPACE, buildVirtualCluster(CLUSTER_ID, OTHER_APP_SERVICE));

            // Test: Try to create ServiceAccount referencing APP_SERVICE
            ServiceAccount sa = buildServiceAccount(SERVICE_ACCOUNT, CLUSTER_ID, APP_SERVICE);
            ValidationResult result = validator.validateCreate(sa, NAMESPACE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("VirtualCluster").contains("is owned by").contains(OTHER_APP_SERVICE).contains(APP_SERVICE);
        }

        @Test
        @DisplayName("should allow Topic when ServiceAccount exists with same owner")
        void testTopicCreateWithValidChain() {
            // Setup complete ownership chain
            setupCompleteOwnershipChain();

            // Test
            Topic topic = buildTopic("orders-events", SERVICE_ACCOUNT, APP_SERVICE);
            ValidationResult result = validator.validateCreate(topic, NAMESPACE);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should reject Topic when ServiceAccount does not exist")
        void testTopicCreateWithMissingServiceAccount() {
            Topic topic = buildTopic("orders-events", "nonexistent-sa", APP_SERVICE);

            ValidationResult result = validator.validateCreate(topic, NAMESPACE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("ServiceAccount").contains("does not exist");
        }

        @Test
        @DisplayName("should reject Topic when ServiceAccount is owned by different ApplicationService")
        void testTopicCreateWithWrongServiceAccountOwner() {
            // Setup: ServiceAccount owned by OTHER_APP_SERVICE
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
            store.create("ApplicationService", NAMESPACE, buildApplicationService(OTHER_APP_SERVICE));
            store.create("VirtualCluster", NAMESPACE, buildVirtualCluster(CLUSTER_ID, OTHER_APP_SERVICE));
            store.create("ServiceAccount", NAMESPACE, buildServiceAccount(SERVICE_ACCOUNT, CLUSTER_ID, OTHER_APP_SERVICE));

            // Test: Try to create Topic referencing APP_SERVICE
            Topic topic = buildTopic("orders-events", SERVICE_ACCOUNT, APP_SERVICE);
            ValidationResult result = validator.validateCreate(topic, NAMESPACE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("ServiceAccount").contains("is owned by").contains(OTHER_APP_SERVICE);
        }

        @Test
        @DisplayName("should allow ACL when ServiceAccount exists with same owner")
        void testACLCreateWithValidChain() {
            // Setup complete ownership chain
            setupCompleteOwnershipChain();
            store.create("Topic", NAMESPACE, buildTopic("orders-events", SERVICE_ACCOUNT, APP_SERVICE));

            // Test
            ACL acl = buildACL("orders-rw", SERVICE_ACCOUNT, "orders-events", APP_SERVICE);
            ValidationResult result = validator.validateCreate(acl, NAMESPACE);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should reject ACL when ServiceAccount does not exist")
        void testACLCreateWithMissingServiceAccount() {
            ACL acl = buildACL("orders-rw", "nonexistent-sa", "orders-events", APP_SERVICE);

            ValidationResult result = validator.validateCreate(acl, NAMESPACE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("ServiceAccount").contains("does not exist");
        }
    }

    // ==================== UPDATE VALIDATION TESTS ====================

    @Nested
    @DisplayName("UPDATE Validation Tests")
    class UpdateValidationTests {

        @Test
        @DisplayName("should allow update when applicationServiceRef is unchanged")
        void testUpdateWithSameOwner() {
            VirtualCluster existing = buildVirtualCluster(CLUSTER_ID, APP_SERVICE);
            VirtualCluster updated = buildVirtualCluster(CLUSTER_ID, APP_SERVICE);
            updated.getSpec().setClusterId("prod-cluster-v2"); // Change other field

            ValidationResult result = validator.validateUpdate(existing, updated);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should reject update when applicationServiceRef is changed")
        void testUpdateWithDifferentOwner() {
            VirtualCluster existing = buildVirtualCluster(CLUSTER_ID, APP_SERVICE);
            VirtualCluster updated = buildVirtualCluster(CLUSTER_ID, OTHER_APP_SERVICE);

            ValidationResult result = validator.validateUpdate(existing, updated);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("Cannot change applicationServiceRef")
                    .contains(APP_SERVICE)
                    .contains(OTHER_APP_SERVICE)
                    .contains("Only the original owner can modify");
        }

        @Test
        @DisplayName("should reject update when existing resource has null owner")
        void testUpdateWithNullExistingOwner() {
            ApplicationService existing = buildApplicationService(APP_SERVICE);
            ApplicationService updated = buildApplicationService(APP_SERVICE);

            ValidationResult result = validator.validateUpdate(existing, updated);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("must have applicationServiceRef");
        }

        @Test
        @DisplayName("should reject update when new resource has null owner")
        void testUpdateWithNullNewOwner() {
            VirtualCluster existing = buildVirtualCluster(CLUSTER_ID, APP_SERVICE);
            ApplicationService updated = buildApplicationService(APP_SERVICE);

            ValidationResult result = validator.validateUpdate(existing, updated);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("must have applicationServiceRef");
        }

        @Test
        @DisplayName("should allow Topic update when owner unchanged")
        void testTopicUpdateWithSameOwner() {
            Topic existing = buildTopic("orders-events", SERVICE_ACCOUNT, APP_SERVICE);
            Topic updated = buildTopic("orders-events", SERVICE_ACCOUNT, APP_SERVICE);
            updated.getSpec().setPartitions(12); // Change partition count

            ValidationResult result = validator.validateUpdate(existing, updated);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should reject Topic update when owner changed")
        void testTopicUpdateWithDifferentOwner() {
            Topic existing = buildTopic("orders-events", SERVICE_ACCOUNT, APP_SERVICE);
            Topic updated = buildTopic("orders-events", SERVICE_ACCOUNT, OTHER_APP_SERVICE);

            ValidationResult result = validator.validateUpdate(existing, updated);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("Cannot change applicationServiceRef").contains(APP_SERVICE).contains(OTHER_APP_SERVICE);
        }
    }

    // ==================== DELETE VALIDATION TESTS ====================

    @Nested
    @DisplayName("DELETE Validation Tests")
    class DeleteValidationTests {

        @Test
        @DisplayName("should allow delete when requestor is the owner")
        void testDeleteByOwner() {
            VirtualCluster resource = buildVirtualCluster(CLUSTER_ID, APP_SERVICE);

            ValidationResult result = validator.validateDelete(resource, APP_SERVICE);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should reject delete when requestor is not the owner")
        void testDeleteByNonOwner() {
            VirtualCluster resource = buildVirtualCluster(CLUSTER_ID, APP_SERVICE);

            ValidationResult result = validator.validateDelete(resource, OTHER_APP_SERVICE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("ApplicationService").contains(OTHER_APP_SERVICE).contains("cannot delete resource owned by").contains(APP_SERVICE);
        }

        @Test
        @DisplayName("should allow Topic delete by owner")
        void testTopicDeleteByOwner() {
            Topic topic = buildTopic("orders-events", SERVICE_ACCOUNT, APP_SERVICE);

            ValidationResult result = validator.validateDelete(topic, APP_SERVICE);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should reject Topic delete by non-owner")
        void testTopicDeleteByNonOwner() {
            Topic topic = buildTopic("orders-events", SERVICE_ACCOUNT, APP_SERVICE);

            ValidationResult result = validator.validateDelete(topic, OTHER_APP_SERVICE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains(OTHER_APP_SERVICE).contains("cannot delete").contains(APP_SERVICE);
        }

        @Test
        @DisplayName("should allow ACL delete by owner")
        void testACLDeleteByOwner() {
            ACL acl = buildACL("orders-rw", SERVICE_ACCOUNT, "orders-events", APP_SERVICE);

            ValidationResult result = validator.validateDelete(acl, APP_SERVICE);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should reject ACL delete by non-owner")
        void testACLDeleteByNonOwner() {
            ACL acl = buildACL("orders-rw", SERVICE_ACCOUNT, "orders-events", APP_SERVICE);

            ValidationResult result = validator.validateDelete(acl, OTHER_APP_SERVICE);

            assertThat(result.isValid()).isFalse();
        }
    }

    // ==================== HELPER METHODS ====================

    private void setupCompleteOwnershipChain() {
        store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
        store.create("VirtualCluster", NAMESPACE, buildVirtualCluster(CLUSTER_ID, APP_SERVICE));
        store.create("ServiceAccount", NAMESPACE, buildServiceAccount(SERVICE_ACCOUNT, CLUSTER_ID, APP_SERVICE));
    }

    private ApplicationService buildApplicationService(String name) {
        ApplicationService appService = new ApplicationService();
        appService.setMetadata(new ObjectMeta());
        appService.getMetadata().setName(name);
        appService.getMetadata().setNamespace(NAMESPACE);

        ApplicationServiceSpec spec = new ApplicationServiceSpec();
        spec.setName(name);
        appService.setSpec(spec);

        return appService;
    }

    private VirtualCluster buildVirtualCluster(String clusterId, String appServiceRef) {
        VirtualCluster vCluster = new VirtualCluster();
        vCluster.setMetadata(new ObjectMeta());
        vCluster.getMetadata().setName(clusterId);
        vCluster.getMetadata().setNamespace(NAMESPACE);

        VirtualClusterSpec spec = new VirtualClusterSpec();
        spec.setClusterId(clusterId);
        spec.setApplicationServiceRef(appServiceRef);
        vCluster.setSpec(spec);

        return vCluster;
    }

    private ServiceAccount buildServiceAccount(String name, String clusterRef, String appServiceRef) {
        ServiceAccount sa = new ServiceAccount();
        sa.setMetadata(new ObjectMeta());
        sa.getMetadata().setName(name);
        sa.getMetadata().setNamespace(NAMESPACE);

        ServiceAccountSpec spec = new ServiceAccountSpec();
        spec.setName(name.replace("-sa", ""));
        spec.setDn(List.of("CN=" + name + ",OU=TEST,O=EXAMPLE,L=CITY,C=US"));
        spec.setClusterRef(clusterRef);
        spec.setApplicationServiceRef(appServiceRef);
        sa.setSpec(spec);

        return sa;
    }

    private Topic buildTopic(String name, String serviceRef, String appServiceRef) {
        Topic topic = new Topic();
        topic.setMetadata(new ObjectMeta());
        topic.getMetadata().setName(name);
        topic.getMetadata().setNamespace(NAMESPACE);

        TopicCRSpec spec = new TopicCRSpec();
        spec.setName(name);
        spec.setPartitions(3);
        spec.setReplicationFactor(3);
        spec.setServiceRef(serviceRef);
        spec.setApplicationServiceRef(appServiceRef);
        topic.setSpec(spec);

        return topic;
    }

    private ACL buildACL(String name, String serviceRef, String topicRef, String appServiceRef) {
        ACL acl = new ACL();
        acl.setMetadata(new ObjectMeta());
        acl.getMetadata().setName(name);
        acl.getMetadata().setNamespace(NAMESPACE);

        AclCRSpec spec = new AclCRSpec();
        spec.setServiceRef(serviceRef);
        spec.setTopicRef(topicRef);
        spec.setApplicationServiceRef(appServiceRef);
        acl.setSpec(spec);

        return acl;
    }
}
