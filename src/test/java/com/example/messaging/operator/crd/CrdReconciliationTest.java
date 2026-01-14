package com.example.messaging.operator.crd;

import com.example.messaging.operator.events.ReconciliationEvent;
import com.example.messaging.operator.events.ReconciliationEventPublisher;
import com.example.messaging.operator.store.CRDStore;
import com.example.messaging.operator.validation.OwnershipValidator;
import com.example.messaging.operator.validation.ValidationResult;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.messaging.operator.crd.AclCRSpec.Operation.ALTER;
import static com.example.messaging.operator.crd.AclCRSpec.Operation.DESCRIBE;
import static com.example.messaging.operator.crd.AclCRSpec.Operation.READ;
import static com.example.messaging.operator.crd.AclCRSpec.Operation.WRITE;
import static com.example.messaging.operator.events.ReconciliationEvent.Operation.*;
import static com.example.messaging.operator.events.ReconciliationEvent.Phase.*;
import static com.example.messaging.operator.events.ReconciliationEvent.Result.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive reconciliation tests for CRD lifecycle management.
 * Tests create, update, delete operations with before/after state validation.
 * Validates applicationService ownership and authorization rules.
 *
 * This test suite simulates etcd behavior using in-memory storage to validate
 * reconciliation logic without requiring a live Kubernetes cluster.
 */
@DisplayName("CRD Reconciliation Tests with State Management")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrdReconciliationTest {

    private CRDStore crdStore;
    private static final String TEST_NAMESPACE = "test-reconciliation";
    private static final String OWNER_APP_SERVICE = "orders-service";
    private static final String DIFFERENT_APP_SERVICE = "unauthorized-service";

    @BeforeEach
    void setup() {
        crdStore = new CRDStore();
    }

    @AfterEach
    void cleanup() {
        crdStore.clear();
    }

    @Nested
    @DisplayName("Create Operations - Before/After State")
    class CreateOperationsTest {

        @Test
        @DisplayName("should create ApplicationService and capture state transitions")
        void testCreateApplicationService() {
            // BEFORE STATE: Empty store
            List<ApplicationService> beforeState = crdStore.list("ApplicationService", TEST_NAMESPACE);

            assertThat(beforeState)
                    .as("BEFORE: No ApplicationService should exist")
                    .isEmpty();

            // CREATE OPERATION
            ApplicationService appService = buildApplicationService(OWNER_APP_SERVICE);
            ApplicationService created = crdStore.create("ApplicationService", TEST_NAMESPACE, appService);

            // AFTER STATE: Resource exists with metadata
            List<ApplicationService> afterState = crdStore.list("ApplicationService", TEST_NAMESPACE);

            assertThat(afterState)
                    .as("AFTER: ApplicationService should exist")
                    .hasSize(1)
                    .first()
                    .satisfies(as -> {
                        assertThat(as.getMetadata().getName()).isEqualTo(OWNER_APP_SERVICE);
                        assertThat(as.getSpec().getName()).isEqualTo(OWNER_APP_SERVICE);
                        assertThat(as.getMetadata().getResourceVersion())
                                .as("Resource version should be set")
                                .isNotNull()
                                .isEqualTo("1");
                        assertThat(as.getMetadata().getUid())
                                .as("UID should be generated")
                                .isNotNull()
                                .isNotEmpty();
                    });

            assertThat(created)
                    .as("Created resource should have server-assigned metadata")
                    .isEqualTo(afterState.get(0));
        }

        @Test
        @DisplayName("should create VirtualCluster with ownership validation")
        void testCreateVirtualClusterWithValidation() {
            // Setup: Create ApplicationService
            crdStore.create("ApplicationService", TEST_NAMESPACE,
                    buildApplicationService(OWNER_APP_SERVICE));

            // BEFORE STATE
            List<VirtualCluster> beforeState = crdStore.list("VirtualCluster", TEST_NAMESPACE);
            assertThat(beforeState).isEmpty();

            // CREATE OPERATION with validation
            VirtualCluster vCluster = buildVirtualCluster("prod-cluster", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult validationResult = validator.validateCreate(vCluster, TEST_NAMESPACE);

            assertThat(validationResult.isValid())
                    .as("Validation should pass when ApplicationService exists")
                    .isTrue();

            VirtualCluster created = crdStore.create("VirtualCluster", TEST_NAMESPACE, vCluster);

            // AFTER STATE
            List<VirtualCluster> afterState = crdStore.list("VirtualCluster", TEST_NAMESPACE);

            assertThat(afterState)
                    .hasSize(1)
                    .first()
                    .satisfies(vc -> {
                        assertThat(vc.getSpec().getClusterId()).isEqualTo("prod-cluster");
                        assertThat(vc.getSpec().getApplicationServiceRef()).isEqualTo(OWNER_APP_SERVICE);
                        assertThat(vc.getMetadata().getResourceVersion()).isEqualTo("2");
                    });
        }

        @Test
        @DisplayName("should reject VirtualCluster creation with missing ApplicationService")
        void testRejectVirtualClusterWithMissingOwner() {
            // BEFORE STATE: No ApplicationService exists
            VirtualCluster vCluster = buildVirtualCluster("prod-cluster", "nonexistent-service");

            // CREATE OPERATION should fail validation
            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(vCluster, TEST_NAMESPACE);

            assertThat(result.isValid())
                    .as("Validation should fail when ApplicationService doesn't exist")
                    .isFalse();

            assertThat(result.getMessage())
                    .contains("ApplicationService")
                    .contains("does not exist");

            // AFTER STATE: Nothing should be created
            List<VirtualCluster> afterState = crdStore.list("VirtualCluster", TEST_NAMESPACE);
            assertThat(afterState).isEmpty();
        }

        @Test
        @DisplayName("should create ServiceAccount with complete ownership chain validation")
        void testCreateServiceAccountWithOwnershipChain() {
            // Setup ownership chain
            crdStore.create("ApplicationService", TEST_NAMESPACE,
                    buildApplicationService(OWNER_APP_SERVICE));
            crdStore.create("VirtualCluster", TEST_NAMESPACE,
                    buildVirtualCluster("prod-cluster", OWNER_APP_SERVICE));

            // BEFORE STATE
            assertThat(crdStore.<ServiceAccount>list("ServiceAccount", TEST_NAMESPACE)).isEmpty();

            // CREATE OPERATION
            ServiceAccount sa = buildServiceAccount("orders-service-sa", "prod-cluster", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(sa, TEST_NAMESPACE);

            assertThat(result.isValid())
                    .as("Validation should pass with complete ownership chain")
                    .isTrue();

            ServiceAccount created = crdStore.create("ServiceAccount", TEST_NAMESPACE, sa);

            // AFTER STATE
            List<ServiceAccount> afterState = crdStore.list("ServiceAccount", TEST_NAMESPACE);

            assertThat(afterState)
                    .hasSize(1)
                    .first()
                    .satisfies(serviceAccount -> {
                        assertThat(serviceAccount.getSpec().getDn())
                                .hasSize(2)
                                .contains("CN=orders-service-sa,OU=ORDERS,O=EXAMPLE,L=CITY,C=US");
                        assertThat(serviceAccount.getSpec().getClusterRef()).isEqualTo("prod-cluster");
                        assertThat(serviceAccount.getSpec().getApplicationServiceRef()).isEqualTo(OWNER_APP_SERVICE);
                        assertThat(serviceAccount.getMetadata().getResourceVersion()).isEqualTo("3");
                    });
        }

        @Test
        @DisplayName("should reject ServiceAccount with mismatched owner in VirtualCluster")
        void testRejectServiceAccountWithOwnershipMismatch() {
            // Setup: ApplicationServices and VirtualCluster owned by different service
            crdStore.create("ApplicationService", TEST_NAMESPACE,
                    buildApplicationService(OWNER_APP_SERVICE));
            crdStore.create("ApplicationService", TEST_NAMESPACE,
                    buildApplicationService(DIFFERENT_APP_SERVICE));
            crdStore.create("VirtualCluster", TEST_NAMESPACE,
                    buildVirtualCluster("prod-cluster", DIFFERENT_APP_SERVICE));

            // Try to create ServiceAccount claiming wrong owner
            ServiceAccount sa = buildServiceAccount("orders-service-sa", "prod-cluster", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(sa, TEST_NAMESPACE);

            assertThat(result.isValid())
                    .as("Validation should fail when VirtualCluster is owned by different ApplicationService")
                    .isFalse();

            assertThat(result.getMessage())
                    .contains("VirtualCluster")
                    .contains("owned by")
                    .contains(DIFFERENT_APP_SERVICE);
        }

        @Test
        @DisplayName("should create Topic with ServiceAccount ownership validation")
        void testCreateTopicWithOwnershipValidation() {
            // Setup complete chain
            setupCompleteOwnershipChain();

            // BEFORE STATE
            assertThat(crdStore.<Topic>list("Topic", TEST_NAMESPACE)).isEmpty();

            // CREATE OPERATION
            Topic topic = buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(topic, TEST_NAMESPACE);

            assertThat(result.isValid()).isTrue();

            Topic created = crdStore.create("Topic", TEST_NAMESPACE, topic);

            // AFTER STATE
            List<Topic> afterState = crdStore.list("Topic", TEST_NAMESPACE);

            assertThat(afterState)
                    .hasSize(1)
                    .first()
                    .satisfies(t -> {
                        assertThat(t.getSpec().getName()).isEqualTo("orders.events");
                        assertThat(t.getSpec().getServiceRef()).isEqualTo("orders-service-sa");
                        assertThat(t.getSpec().getApplicationServiceRef()).isEqualTo(OWNER_APP_SERVICE);
                    });
        }

        @Test
        @DisplayName("should reject Topic with ServiceAccount ownership mismatch")
        void testRejectTopicWithOwnershipMismatch() {
            // Setup: ServiceAccount owned by OWNER_APP_SERVICE
            setupCompleteOwnershipChain();

            // Try to create Topic claiming DIFFERENT_APP_SERVICE
            Topic topic = buildTopic("invalid-topic", "orders-service-sa", DIFFERENT_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(topic, TEST_NAMESPACE);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage())
                    .contains("ServiceAccount")
                    .contains("owned by")
                    .contains(OWNER_APP_SERVICE)
                    .contains("not")
                    .contains(DIFFERENT_APP_SERVICE);
        }

        @Test
        @DisplayName("should create ACL with complete ownership validation")
        void testCreateACLWithOwnershipValidation() {
            // Setup
            setupCompleteOwnershipChain();
            crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            assertThat(crdStore.<ACL>list("ACL", TEST_NAMESPACE)).isEmpty();

            // CREATE OPERATION
            ACL acl = buildACL("orders-events-rw", "orders-service-sa", "orders-events", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(acl, TEST_NAMESPACE);

            assertThat(result.isValid()).isTrue();

            ACL created = crdStore.create("ACL", TEST_NAMESPACE, acl);

            // AFTER STATE
            List<ACL> afterState = crdStore.list("ACL", TEST_NAMESPACE);

            assertThat(afterState)
                    .hasSize(1)
                    .first()
                    .satisfies(a -> {
                        assertThat(a.getSpec().getTopicRef()).isEqualTo("orders-events");
                        assertThat(a.getSpec().getOperations()).contains(READ, WRITE);
                        assertThat(a.getSpec().getApplicationServiceRef()).isEqualTo(OWNER_APP_SERVICE);
                    });
        }
    }

    @Nested
    @DisplayName("Update Operations - Before/After State")
    class UpdateOperationsTest {

        @Test
        @DisplayName("should update Topic partitions and track resource version")
        void testUpdateTopicPartitions() {
            // Setup
            setupCompleteOwnershipChain();
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            Topic beforeState = crdStore.get("Topic", TEST_NAMESPACE, "orders-events");
            String beforeResourceVersion = beforeState.getMetadata().getResourceVersion();

            assertThat(beforeState.getSpec().getPartitions()).isEqualTo(12);
            assertThat(beforeResourceVersion).isEqualTo("4");

            // UPDATE OPERATION
            beforeState.getSpec().setPartitions(24);
            Topic updated = crdStore.update("Topic", TEST_NAMESPACE, "orders-events", beforeState);

            // AFTER STATE
            Topic afterState = crdStore.get("Topic", TEST_NAMESPACE, "orders-events");

            assertThat(afterState)
                    .satisfies(t -> {
                        assertThat(t.getSpec().getPartitions())
                                .as("Partitions should be updated")
                                .isEqualTo(24);
                        assertThat(t.getMetadata().getResourceVersion())
                                .as("Resource version should increment")
                                .isEqualTo("5")
                                .isNotEqualTo(beforeResourceVersion);
                    });
        }

        @Test
        @DisplayName("should reject update that changes applicationServiceRef")
        void testRejectOwnershipChange() {
            // Setup
            setupCompleteOwnershipChain();
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            Topic beforeState = crdStore.get("Topic", TEST_NAMESPACE, "orders-events");
            String originalOwner = beforeState.getSpec().getApplicationServiceRef();
            assertThat(originalOwner).isEqualTo(OWNER_APP_SERVICE);

            // ATTEMPT UNAUTHORIZED UPDATE - simulate what would come from API
            // Create a new spec with modified owner (simulating API request)
            Topic modifiedTopic = buildTopic("orders-events", "orders-service-sa", DIFFERENT_APP_SERVICE);
            modifiedTopic.getMetadata().setName("orders-events");
            modifiedTopic.getMetadata().setNamespace(TEST_NAMESPACE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateUpdate(beforeState, modifiedTopic);

            assertThat(result.isValid())
                    .as("Validation should reject ownership change")
                    .isFalse();

            assertThat(result.getMessage())
                    .contains("Cannot change applicationServiceRef")
                    .contains(OWNER_APP_SERVICE)
                    .contains(DIFFERENT_APP_SERVICE)
                    .contains("Only the original owner can modify");

            // AFTER STATE: Verify original is unchanged in store
            Topic afterState = crdStore.get("Topic", TEST_NAMESPACE, "orders-events");
            assertThat(afterState.getSpec().getApplicationServiceRef())
                    .as("Owner should remain unchanged")
                    .isEqualTo(originalOwner);
        }

        @Test
        @DisplayName("should update ServiceAccount DN list with same owner")
        void testUpdateServiceAccountDN() {
            // Setup
            setupCompleteOwnershipChain();

            // BEFORE STATE
            ServiceAccount beforeState = crdStore.get("ServiceAccount", TEST_NAMESPACE, "orders-service-sa");
            assertThat(beforeState.getSpec().getDn()).hasSize(2);
            String beforeResourceVersion = beforeState.getMetadata().getResourceVersion();

            // UPDATE OPERATION - Add another DN
            beforeState.getSpec().getDn().add("CN=orders-service-tertiary,OU=ORDERS,O=EXAMPLE,L=CITY,C=US");

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateUpdate(
                    crdStore.get("ServiceAccount", TEST_NAMESPACE, "orders-service-sa"),
                    beforeState
            );

            assertThat(result.isValid())
                    .as("Update should be allowed when owner is unchanged")
                    .isTrue();

            ServiceAccount updated = crdStore.update("ServiceAccount", TEST_NAMESPACE, "orders-service-sa", beforeState);

            // AFTER STATE
            ServiceAccount afterState = crdStore.get("ServiceAccount", TEST_NAMESPACE, "orders-service-sa");

            assertThat(afterState.getSpec().getDn())
                    .hasSize(3)
                    .contains("CN=orders-service-tertiary,OU=ORDERS,O=EXAMPLE,L=CITY,C=US");

            assertThat(afterState.getMetadata().getResourceVersion())
                    .isNotEqualTo(beforeResourceVersion);
        }

        @Test
        @DisplayName("should update ACL operations preserving ownership")
        void testUpdateACLOperations() {
            // Setup
            setupCompleteOwnershipChain();
            crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            ACL acl = crdStore.create("ACL", TEST_NAMESPACE,
                    buildACL("orders-events-rw", "orders-service-sa", "orders-events", OWNER_APP_SERVICE));

            // BEFORE STATE
            ACL beforeState = crdStore.get("ACL", TEST_NAMESPACE, "orders-events-rw");
            assertThat(beforeState.getSpec().getOperations()).hasSize(2);

            // UPDATE OPERATION
            beforeState.getSpec().getOperations().add(DESCRIBE);
            beforeState.getSpec().getOperations().add(ALTER);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateUpdate(
                    crdStore.get("ACL", TEST_NAMESPACE, "orders-events-rw"),
                    beforeState
            );

            assertThat(result.isValid()).isTrue();

            ACL updated = crdStore.update("ACL", TEST_NAMESPACE, "orders-events-rw", beforeState);

            // AFTER STATE
            ACL afterState = crdStore.get("ACL", TEST_NAMESPACE, "orders-events-rw");

            assertThat(afterState.getSpec().getOperations())
                    .hasSize(4)
                    .containsExactlyInAnyOrder(READ, WRITE, DESCRIBE, ALTER);

            assertThat(afterState.getSpec().getApplicationServiceRef())
                    .as("Owner should remain unchanged")
                    .isEqualTo(OWNER_APP_SERVICE);
        }
    }

    @Nested
    @DisplayName("Delete Operations - Before/After State")
    class DeleteOperationsTest {

        @Test
        @DisplayName("should delete Topic and verify complete removal")
        void testDeleteTopic() {
            // Setup
            setupCompleteOwnershipChain();
            crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            List<Topic> beforeState = crdStore.list("Topic", TEST_NAMESPACE);
            assertThat(beforeState)
                    .as("BEFORE: Topic should exist")
                    .hasSize(1);

            // DELETE OPERATION
            boolean deleted = crdStore.delete("Topic", TEST_NAMESPACE, "orders-events");

            assertThat(deleted)
                    .as("Delete operation should succeed")
                    .isTrue();

            // AFTER STATE
            List<Topic> afterState = crdStore.list("Topic", TEST_NAMESPACE);
            assertThat(afterState)
                    .as("AFTER: Topic should be removed")
                    .isEmpty();

            Topic retrieved = crdStore.get("Topic", TEST_NAMESPACE, "orders-events");
            assertThat(retrieved)
                    .as("Get operation should return null after delete")
                    .isNull();
        }

        @Test
        @DisplayName("should reject delete from non-owner ApplicationService")
        void testRejectUnauthorizedDelete() {
            // Setup
            setupCompleteOwnershipChain();
            crdStore.create("ApplicationService", TEST_NAMESPACE,
                    buildApplicationService(DIFFERENT_APP_SERVICE));
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // ATTEMPT UNAUTHORIZED DELETE
            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateDelete(topic, DIFFERENT_APP_SERVICE);

            assertThat(result.isValid())
                    .as("Delete should be rejected from non-owner")
                    .isFalse();

            assertThat(result.getMessage())
                    .contains(DIFFERENT_APP_SERVICE)
                    .contains("cannot delete")
                    .contains(OWNER_APP_SERVICE);

            // AFTER STATE: Resource should still exist
            Topic afterState = crdStore.get("Topic", TEST_NAMESPACE, "orders-events");
            assertThat(afterState)
                    .as("Resource should not be deleted")
                    .isNotNull();
        }

        @Test
        @DisplayName("should allow delete from owner ApplicationService")
        void testAllowAuthorizedDelete() {
            // Setup
            setupCompleteOwnershipChain();
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            assertThat(crdStore.<Topic>list("Topic", TEST_NAMESPACE)).hasSize(1);

            // AUTHORIZED DELETE
            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateDelete(topic, OWNER_APP_SERVICE);

            assertThat(result.isValid())
                    .as("Delete should be allowed for owner")
                    .isTrue();

            boolean deleted = crdStore.delete("Topic", TEST_NAMESPACE, "orders-events");
            assertThat(deleted).isTrue();

            // AFTER STATE
            assertThat(crdStore.<Topic>list("Topic", TEST_NAMESPACE)).isEmpty();
        }

        @Test
        @DisplayName("should delete ACL and maintain referential integrity")
        void testDeleteACL() {
            // Setup
            setupCompleteOwnershipChain();
            crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            crdStore.create("ACL", TEST_NAMESPACE,
                    buildACL("orders-events-rw", "orders-service-sa", "orders-events", OWNER_APP_SERVICE));

            // BEFORE STATE
            assertThat(crdStore.<ACL>list("ACL", TEST_NAMESPACE)).hasSize(1);
            assertThat(crdStore.<Topic>list("Topic", TEST_NAMESPACE)).hasSize(1);

            // DELETE OPERATION
            boolean deleted = crdStore.delete("ACL", TEST_NAMESPACE, "orders-events-rw");
            assertThat(deleted).isTrue();

            // AFTER STATE
            assertThat(crdStore.<ACL>list("ACL", TEST_NAMESPACE))
                    .as("ACL should be deleted")
                    .isEmpty();

            assertThat(crdStore.<Topic>list("Topic", TEST_NAMESPACE))
                    .as("Referenced Topic should remain")
                    .hasSize(1);
        }

        @Test
        @DisplayName("should track state through cascade delete scenario")
        void testCascadeDeleteStateTransitions() {
            // Setup complete hierarchy
            setupCompleteOwnershipChain();
            crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            crdStore.create("ACL", TEST_NAMESPACE,
                    buildACL("orders-events-rw", "orders-service-sa", "orders-events", OWNER_APP_SERVICE));

            // BEFORE STATE
            Map<String, Integer> beforeCounts = new HashMap<>();
            beforeCounts.put("ApplicationService", crdStore.<ApplicationService>list("ApplicationService", TEST_NAMESPACE).size());
            beforeCounts.put("VirtualCluster", crdStore.<VirtualCluster>list("VirtualCluster", TEST_NAMESPACE).size());
            beforeCounts.put("ServiceAccount", crdStore.<ServiceAccount>list("ServiceAccount", TEST_NAMESPACE).size());
            beforeCounts.put("Topic", crdStore.<Topic>list("Topic", TEST_NAMESPACE).size());
            beforeCounts.put("ACL", crdStore.<ACL>list("ACL", TEST_NAMESPACE).size());

            assertThat(beforeCounts)
                    .as("BEFORE: All resources exist")
                    .containsEntry("ApplicationService", 1)
                    .containsEntry("VirtualCluster", 1)
                    .containsEntry("ServiceAccount", 1)
                    .containsEntry("Topic", 1)
                    .containsEntry("ACL", 1);

            // CASCADE DELETE SIMULATION
            // In production, Kubernetes would handle cascade with ownerReferences
            // Here we simulate manual cascade
            crdStore.delete("ACL", TEST_NAMESPACE, "orders-events-rw");
            crdStore.delete("Topic", TEST_NAMESPACE, "orders-events");
            crdStore.delete("ServiceAccount", TEST_NAMESPACE, "orders-service-sa");
            crdStore.delete("VirtualCluster", TEST_NAMESPACE, "prod-cluster");
            crdStore.delete("ApplicationService", TEST_NAMESPACE, OWNER_APP_SERVICE);

            // AFTER STATE
            Map<String, Integer> afterCounts = new HashMap<>();
            afterCounts.put("ApplicationService", crdStore.<ApplicationService>list("ApplicationService", TEST_NAMESPACE).size());
            afterCounts.put("VirtualCluster", crdStore.<VirtualCluster>list("VirtualCluster", TEST_NAMESPACE).size());
            afterCounts.put("ServiceAccount", crdStore.<ServiceAccount>list("ServiceAccount", TEST_NAMESPACE).size());
            afterCounts.put("Topic", crdStore.<Topic>list("Topic", TEST_NAMESPACE).size());
            afterCounts.put("ACL", crdStore.<ACL>list("ACL", TEST_NAMESPACE).size());

            assertThat(afterCounts)
                    .as("AFTER: All resources should be deleted")
                    .containsEntry("ApplicationService", 0)
                    .containsEntry("VirtualCluster", 0)
                    .containsEntry("ServiceAccount", 0)
                    .containsEntry("Topic", 0)
                    .containsEntry("ACL", 0);
        }
    }

    @Nested
    @DisplayName("Event Publishing Tests")
    class EventPublishingTest {

        private List<ReconciliationEvent> capturedEvents;
        private ReconciliationEventPublisher.ReconciliationEventListener eventCapture;

        @BeforeEach
        void setupEventCapture() {
            capturedEvents = new ArrayList<>();
            eventCapture = event -> capturedEvents.add(event);
            crdStore.getEventPublisher().addListener(eventCapture);
        }

        @AfterEach
        void cleanupEventCapture() {
            crdStore.getEventPublisher().removeListener(eventCapture);
        }

        @Test
        @DisplayName("should publish BEFORE and AFTER events for successful create")
        void testCreateEventLifecycle() {
            // Setup
            setupCompleteOwnershipChain();
            capturedEvents.clear(); // Clear setup events

            // CREATE OPERATION
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // VERIFY EVENTS
            assertThat(capturedEvents)
                    .as("Should have BEFORE and AFTER events")
                    .hasSize(2);

            ReconciliationEvent beforeEvent = capturedEvents.get(0);
            assertThat(beforeEvent.getPhase()).isEqualTo(BEFORE);
            assertThat(beforeEvent.getOperation()).isEqualTo(CREATE);
            assertThat(beforeEvent.getResourceKind()).isEqualTo("Topic");
            assertThat(beforeEvent.getResourceName()).isEqualTo("orders-events");
            assertThat(beforeEvent.getResourceNamespace()).isEqualTo(TEST_NAMESPACE);
            assertThat(beforeEvent.getApplicationService()).isEqualTo(OWNER_APP_SERVICE);
            assertThat(beforeEvent.getResult()).isNull();

            ReconciliationEvent afterEvent = capturedEvents.get(1);
            assertThat(afterEvent.getPhase()).isEqualTo(AFTER);
            assertThat(afterEvent.getOperation()).isEqualTo(CREATE);
            assertThat(afterEvent.getResourceKind()).isEqualTo("Topic");
            assertThat(afterEvent.getResourceName()).isEqualTo("orders-events");
            assertThat(afterEvent.getResourceNamespace()).isEqualTo(TEST_NAMESPACE);
            assertThat(afterEvent.getApplicationService()).isEqualTo(OWNER_APP_SERVICE);
            assertThat(afterEvent.getResult()).isEqualTo(SUCCESS);
            assertThat(afterEvent.getResourceVersion()).isNotNull();
            assertThat(afterEvent.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should publish BEFORE and AFTER events for successful update")
        void testUpdateEventLifecycle() {
            // Setup
            setupCompleteOwnershipChain();
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            capturedEvents.clear(); // Clear setup events

            // UPDATE OPERATION
            topic.getSpec().setPartitions(6);
            Topic updated = crdStore.update("Topic", TEST_NAMESPACE, "orders-events", topic);

            // VERIFY EVENTS
            assertThat(capturedEvents)
                    .as("Should have BEFORE and AFTER events")
                    .hasSize(2);

            ReconciliationEvent beforeEvent = capturedEvents.get(0);
            assertThat(beforeEvent.getPhase()).isEqualTo(BEFORE);
            assertThat(beforeEvent.getOperation()).isEqualTo(UPDATE);
            assertThat(beforeEvent.getResourceKind()).isEqualTo("Topic");
            assertThat(beforeEvent.getResourceName()).isEqualTo("orders-events");

            ReconciliationEvent afterEvent = capturedEvents.get(1);
            assertThat(afterEvent.getPhase()).isEqualTo(AFTER);
            assertThat(afterEvent.getOperation()).isEqualTo(UPDATE);
            assertThat(afterEvent.getResult()).isEqualTo(SUCCESS);
            assertThat(afterEvent.getResourceVersion()).isNotNull();
            assertThat(afterEvent.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should publish BEFORE and AFTER events for successful delete")
        void testDeleteEventLifecycle() {
            // Setup
            setupCompleteOwnershipChain();
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            capturedEvents.clear(); // Clear setup events

            // DELETE OPERATION
            boolean deleted = crdStore.delete("Topic", TEST_NAMESPACE, "orders-events");

            // VERIFY EVENTS
            assertThat(deleted).isTrue();
            assertThat(capturedEvents)
                    .as("Should have BEFORE and AFTER events")
                    .hasSize(2);

            ReconciliationEvent beforeEvent = capturedEvents.get(0);
            assertThat(beforeEvent.getPhase()).isEqualTo(BEFORE);
            assertThat(beforeEvent.getOperation()).isEqualTo(DELETE);
            assertThat(beforeEvent.getResourceKind()).isEqualTo("Topic");
            assertThat(beforeEvent.getResourceName()).isEqualTo("orders-events");

            ReconciliationEvent afterEvent = capturedEvents.get(1);
            assertThat(afterEvent.getPhase()).isEqualTo(AFTER);
            assertThat(afterEvent.getOperation()).isEqualTo(DELETE);
            assertThat(afterEvent.getResult()).isEqualTo(SUCCESS);
            assertThat(afterEvent.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should publish FAILURE event when create fails")
        void testCreateFailureEvent() {
            // Setup - create a resource first
            setupCompleteOwnershipChain();
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            capturedEvents.clear();

            // CREATE OPERATION - should fail due to duplicate resource
            try {
                Topic duplicate = crdStore.create("Topic", TEST_NAMESPACE,
                        buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            } catch (Exception e) {
                // Expected failure
            }

            // VERIFY EVENTS
            assertThat(capturedEvents)
                    .as("Should have BEFORE and AFTER(FAILURE) events")
                    .hasSize(2);

            ReconciliationEvent beforeEvent = capturedEvents.get(0);
            assertThat(beforeEvent.getPhase()).isEqualTo(BEFORE);
            assertThat(beforeEvent.getOperation()).isEqualTo(CREATE);

            ReconciliationEvent afterEvent = capturedEvents.get(1);
            assertThat(afterEvent.getPhase()).isEqualTo(AFTER);
            assertThat(afterEvent.getOperation()).isEqualTo(CREATE);
            assertThat(afterEvent.getResult()).isEqualTo(FAILURE);
            assertThat(afterEvent.isFailure()).isTrue();
            assertThat(afterEvent.getErrorDetails()).isNotNull();
        }

        @Test
        @DisplayName("should track complete lifecycle with multiple operations")
        void testCompleteLifecycleEventTracking() {
            // Setup
            setupCompleteOwnershipChain();
            capturedEvents.clear();

            // CREATE
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            assertThat(capturedEvents).hasSize(2); // BEFORE + AFTER

            // UPDATE
            topic.getSpec().setPartitions(6);
            crdStore.update("Topic", TEST_NAMESPACE, "orders-events", topic);
            assertThat(capturedEvents).hasSize(4); // +2 for update

            // DELETE
            crdStore.delete("Topic", TEST_NAMESPACE, "orders-events");
            assertThat(capturedEvents).hasSize(6); // +2 for delete

            // VERIFY FULL LIFECYCLE
            assertThat(capturedEvents.stream()
                    .filter(e -> e.getPhase() == BEFORE)
                    .count())
                    .as("Should have 3 BEFORE events")
                    .isEqualTo(3);

            assertThat(capturedEvents.stream()
                    .filter(e -> e.getPhase() == AFTER)
                    .count())
                    .as("Should have 3 AFTER events")
                    .isEqualTo(3);

            assertThat(capturedEvents.stream()
                    .filter(e -> e.getResult() == SUCCESS)
                    .count())
                    .as("All operations should be successful")
                    .isEqualTo(3);

            // Verify operations in order
            assertThat(capturedEvents.get(0).getOperation()).isEqualTo(CREATE);
            assertThat(capturedEvents.get(2).getOperation()).isEqualTo(UPDATE);
            assertThat(capturedEvents.get(4).getOperation()).isEqualTo(ReconciliationEvent.Operation.DELETE);
        }

        @Test
        @DisplayName("should include resource version in AFTER events")
        void testResourceVersionInEvents() {
            // Setup
            setupCompleteOwnershipChain();
            capturedEvents.clear();

            // CREATE
            Topic topic = crdStore.create("Topic", TEST_NAMESPACE,
                    buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            ReconciliationEvent afterCreate = capturedEvents.get(1);
            Long versionAfterCreate = afterCreate.getResourceVersion();
            assertThat(versionAfterCreate).isNotNull().isPositive();

            // UPDATE
            topic.getSpec().setPartitions(6);
            crdStore.update("Topic", TEST_NAMESPACE, "orders-events", topic);

            ReconciliationEvent afterUpdate = capturedEvents.get(3);
            Long versionAfterUpdate = afterUpdate.getResourceVersion();
            assertThat(versionAfterUpdate)
                    .as("Resource version should increment after update")
                    .isGreaterThan(versionAfterCreate);
        }
    }

    // Helper methods
    private void setupCompleteOwnershipChain() {
        crdStore.create("ApplicationService", TEST_NAMESPACE,
                buildApplicationService(OWNER_APP_SERVICE));
        crdStore.create("VirtualCluster", TEST_NAMESPACE,
                buildVirtualCluster("prod-cluster", OWNER_APP_SERVICE));
        crdStore.create("ServiceAccount", TEST_NAMESPACE,
                buildServiceAccount("orders-service-sa", "prod-cluster", OWNER_APP_SERVICE));
    }

    private ApplicationService buildApplicationService(String name) {
        ApplicationService appService = new ApplicationService();
        appService.setMetadata(new ObjectMeta());
        appService.getMetadata().setName(name);
        appService.getMetadata().setNamespace(TEST_NAMESPACE);

        ApplicationServiceSpec spec = new ApplicationServiceSpec();
        spec.setName(name);
        appService.setSpec(spec);

        return appService;
    }

    private VirtualCluster buildVirtualCluster(String clusterId, String appServiceRef) {
        VirtualCluster vCluster = new VirtualCluster();
        vCluster.setMetadata(new ObjectMeta());
        vCluster.getMetadata().setName(clusterId);
        vCluster.getMetadata().setNamespace(TEST_NAMESPACE);

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
        sa.getMetadata().setNamespace(TEST_NAMESPACE);

        ServiceAccountSpec spec = new ServiceAccountSpec();
        spec.setName(name.replace("-sa", ""));
        spec.setDn(new ArrayList<>() {{
            add("CN=" + name + ",OU=ORDERS,O=EXAMPLE,L=CITY,C=US");
            add("CN=" + name + "-backup,OU=ORDERS,O=EXAMPLE,L=CITY,C=US");
        }});
        spec.setClusterRef(clusterRef);
        spec.setApplicationServiceRef(appServiceRef);
        sa.setSpec(spec);

        return sa;
    }

    private Topic buildTopic(String name, String serviceRef, String appServiceRef) {
        Topic topic = new Topic();
        topic.setMetadata(new ObjectMeta());
        topic.getMetadata().setName(name);
        topic.getMetadata().setNamespace(TEST_NAMESPACE);

        TopicCRSpec spec = new TopicCRSpec();
        spec.setServiceRef(serviceRef);
        spec.setName(name.replace("-", "."));
        spec.setPartitions(12);
        spec.setReplicationFactor(3);
        spec.setApplicationServiceRef(appServiceRef);
        topic.setSpec(spec);

        return topic;
    }

    private ACL buildACL(String name, String serviceRef, String topicRef, String appServiceRef) {
        ACL acl = new ACL();
        acl.setMetadata(new ObjectMeta());
        acl.getMetadata().setName(name);
        acl.getMetadata().setNamespace(TEST_NAMESPACE);

        AclCRSpec spec = new AclCRSpec();
        spec.setServiceRef(serviceRef);
        spec.setTopicRef(topicRef);
        spec.setOperations(new ArrayList<>() {{
            add(READ);
            add(WRITE);
        }});
        spec.setApplicationServiceRef(appServiceRef);
        acl.setSpec(spec);

        return acl;
    }
}
