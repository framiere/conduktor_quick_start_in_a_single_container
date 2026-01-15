package com.example.messaging.operator.crd;

import static com.example.messaging.operator.crd.AclCRSpec.Operation.ALTER;
import static com.example.messaging.operator.crd.AclCRSpec.Operation.DESCRIBE;
import static com.example.messaging.operator.crd.AclCRSpec.Operation.READ;
import static com.example.messaging.operator.crd.AclCRSpec.Operation.WRITE;
import static com.example.messaging.operator.events.ReconciliationEvent.Operation.*;
import static com.example.messaging.operator.events.ReconciliationEvent.Phase.*;
import static com.example.messaging.operator.events.ReconciliationEvent.Result.*;
import static com.example.messaging.operator.events.ReconciliationEventPublisher.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.events.ReconciliationEvent;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import com.example.messaging.operator.validation.OwnershipValidator;
import com.example.messaging.operator.validation.ValidationResult;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;

/**
 * Comprehensive reconciliation tests for CRD lifecycle management. Tests create, update, delete operations with before/after state validation. Validates
 * applicationService ownership and authorization rules.
 *
 * This test suite simulates etcd behavior using in-memory storage to validate reconciliation logic without requiring a live Kubernetes cluster.
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
            List<ApplicationService> beforeState = crdStore.list(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE);

            assertThat(beforeState).as("BEFORE: No ApplicationService should exist").isEmpty();

            // CREATE OPERATION
            ApplicationService appService = buildApplicationService(OWNER_APP_SERVICE);
            ApplicationService created = crdStore.create(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE, appService);

            // AFTER STATE: Resource exists with metadata
            List<ApplicationService> afterState = crdStore.list(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE);

            assertThat(afterState).as("AFTER: ApplicationService should exist").hasSize(1).first().satisfies(as -> {
                assertThat(as.getMetadata().getName()).isEqualTo(OWNER_APP_SERVICE);
                assertThat(as.getSpec().getName()).isEqualTo(OWNER_APP_SERVICE);
                assertThat(as.getMetadata().getResourceVersion()).as("Resource version should be set").isNotNull().isEqualTo("1");
                assertThat(as.getMetadata().getUid()).as("UID should be generated").isNotNull().isNotEmpty();
            });

            assertThat(created).as("Created resource should have server-assigned metadata").isEqualTo(afterState.get(0));
        }

        @Test
        @DisplayName("should create KafkaCluster with ownership validation")
        void testCreateKafkaClusterWithValidation() {
            // Setup: Create ApplicationService
            crdStore.create(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE, buildApplicationService(OWNER_APP_SERVICE));

            // BEFORE STATE
            List<KafkaCluster> beforeState = crdStore.list(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE);
            assertThat(beforeState).isEmpty();

            // CREATE OPERATION with validation
            KafkaCluster vCluster = buildKafkaCluster("prod-cluster", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult validationResult = validator.validateCreate(vCluster, TEST_NAMESPACE);

            assertThat(validationResult.isValid()).as("Validation should pass when ApplicationService exists").isTrue();

            KafkaCluster created = crdStore.create(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE, vCluster);

            // AFTER STATE
            List<KafkaCluster> afterState = crdStore.list(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE);

            assertThat(afterState).hasSize(1).first().satisfies(vc -> {
                assertThat(vc.getSpec().getClusterId()).isEqualTo("prod-cluster");
                assertThat(vc.getSpec().getApplicationServiceRef()).isEqualTo(OWNER_APP_SERVICE);
                assertThat(vc.getMetadata().getResourceVersion()).isEqualTo("2");
            });
        }

        @Test
        @DisplayName("should reject KafkaCluster creation with missing ApplicationService")
        void testRejectKafkaClusterWithMissingOwner() {
            // BEFORE STATE: No ApplicationService exists
            KafkaCluster vCluster = buildKafkaCluster("prod-cluster", "nonexistent-service");

            // CREATE OPERATION should fail validation
            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(vCluster, TEST_NAMESPACE);

            assertThat(result.isValid()).as("Validation should fail when ApplicationService doesn't exist").isFalse();

            assertThat(result.getMessage()).contains("ApplicationService").contains("does not exist");

            // AFTER STATE: Nothing should be created
            List<KafkaCluster> afterState = crdStore.list(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE);
            assertThat(afterState).isEmpty();
        }

        @Test
        @DisplayName("should create ServiceAccount with complete ownership chain validation")
        void testCreateServiceAccountWithOwnershipChain() {
            // Setup ownership chain
            crdStore.create(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE, buildApplicationService(OWNER_APP_SERVICE));
            crdStore.create(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE, buildKafkaCluster("prod-cluster", OWNER_APP_SERVICE));

            // BEFORE STATE
            assertThat(crdStore.<ServiceAccount>list(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE)).isEmpty();

            // CREATE OPERATION
            ServiceAccount sa = buildServiceAccount("orders-service-sa", "prod-cluster", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(sa, TEST_NAMESPACE);

            assertThat(result.isValid()).as("Validation should pass with complete ownership chain").isTrue();

            ServiceAccount created = crdStore.create(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE, sa);

            // AFTER STATE
            List<ServiceAccount> afterState = crdStore.list(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE);

            assertThat(afterState).hasSize(1).first().satisfies(serviceAccount -> {
                assertThat(serviceAccount.getSpec().getDn()).hasSize(2).contains("CN=orders-service-sa,OU=ORDERS,O=EXAMPLE,L=CITY,C=US");
                assertThat(serviceAccount.getSpec().getClusterRef()).isEqualTo("prod-cluster");
                assertThat(serviceAccount.getSpec().getApplicationServiceRef()).isEqualTo(OWNER_APP_SERVICE);
                assertThat(serviceAccount.getMetadata().getResourceVersion()).isEqualTo("3");
            });
        }

        @Test
        @DisplayName("should reject ServiceAccount with mismatched owner in KafkaCluster")
        void testRejectServiceAccountWithOwnershipMismatch() {
            // Setup: ApplicationServices and KafkaCluster owned by different service
            crdStore.create(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE, buildApplicationService(OWNER_APP_SERVICE));
            crdStore.create(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE, buildApplicationService(DIFFERENT_APP_SERVICE));
            crdStore.create(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE, buildKafkaCluster("prod-cluster", DIFFERENT_APP_SERVICE));

            // Try to create ServiceAccount claiming wrong owner
            ServiceAccount sa = buildServiceAccount("orders-service-sa", "prod-cluster", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(sa, TEST_NAMESPACE);

            assertThat(result.isValid()).as("Validation should fail when KafkaCluster is owned by different ApplicationService").isFalse();

            assertThat(result.getMessage()).contains("KafkaCluster").contains("owned by").contains(DIFFERENT_APP_SERVICE);
        }

        @Test
        @DisplayName("should create Topic with ServiceAccount ownership validation")
        void testCreateTopicWithOwnershipValidation() {
            // Setup complete chain
            setupCompleteOwnershipChain();

            // BEFORE STATE
            assertThat(crdStore.<Topic>list(CRDKind.TOPIC, TEST_NAMESPACE)).isEmpty();

            // CREATE OPERATION
            Topic topic = buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(topic, TEST_NAMESPACE);

            assertThat(result.isValid()).isTrue();

            Topic created = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, topic);

            // AFTER STATE
            List<Topic> afterState = crdStore.list(CRDKind.TOPIC, TEST_NAMESPACE);

            assertThat(afterState).hasSize(1).first().satisfies(t -> {
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
            assertThat(result.getMessage()).contains("ServiceAccount").contains("owned by").contains(OWNER_APP_SERVICE).contains("not").contains(DIFFERENT_APP_SERVICE);
        }

        @Test
        @DisplayName("should create ACL with complete ownership validation")
        void testCreateACLWithOwnershipValidation() {
            // Setup
            setupCompleteOwnershipChain();
            crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            assertThat(crdStore.<ACL>list(CRDKind.ACL, TEST_NAMESPACE)).isEmpty();

            // CREATE OPERATION
            ACL acl = buildACL("orders-events-rw", "orders-service-sa", "orders-events", OWNER_APP_SERVICE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateCreate(acl, TEST_NAMESPACE);

            assertThat(result.isValid()).isTrue();

            ACL created = crdStore.create(CRDKind.ACL, TEST_NAMESPACE, acl);

            // AFTER STATE
            List<ACL> afterState = crdStore.list(CRDKind.ACL, TEST_NAMESPACE);

            assertThat(afterState).hasSize(1).first().satisfies(a -> {
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
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            Topic beforeState = crdStore.get(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");
            String beforeResourceVersion = beforeState.getMetadata().getResourceVersion();

            assertThat(beforeState.getSpec().getPartitions()).isEqualTo(12);
            assertThat(beforeResourceVersion).isEqualTo("4");

            // UPDATE OPERATION
            beforeState.getSpec().setPartitions(24);
            Topic updated = crdStore.update(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events", beforeState);

            // AFTER STATE
            Topic afterState = crdStore.get(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");

            assertThat(afterState).satisfies(t -> {
                assertThat(t.getSpec().getPartitions()).as("Partitions should be updated").isEqualTo(24);
                assertThat(t.getMetadata().getResourceVersion()).as("Resource version should increment").isEqualTo("5").isNotEqualTo(beforeResourceVersion);
            });
        }

        @Test
        @DisplayName("should reject update that changes applicationServiceRef")
        void testRejectOwnershipChange() {
            // Setup
            setupCompleteOwnershipChain();
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            Topic beforeState = crdStore.get(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");
            String originalOwner = beforeState.getSpec().getApplicationServiceRef();
            assertThat(originalOwner).isEqualTo(OWNER_APP_SERVICE);

            // ATTEMPT UNAUTHORIZED UPDATE - simulate what would come from API
            // Create a new spec with modified owner (simulating API request)
            Topic modifiedTopic = buildTopic("orders-events", "orders-service-sa", DIFFERENT_APP_SERVICE);
            modifiedTopic.getMetadata().setName("orders-events");
            modifiedTopic.getMetadata().setNamespace(TEST_NAMESPACE);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateUpdate(beforeState, modifiedTopic);

            assertThat(result.isValid()).as("Validation should reject ownership change").isFalse();

            assertThat(result.getMessage()).contains("Cannot change applicationServiceRef")
                    .contains(OWNER_APP_SERVICE)
                    .contains(DIFFERENT_APP_SERVICE)
                    .contains("Only the original owner can modify");

            // AFTER STATE: Verify original is unchanged in store
            Topic afterState = crdStore.get(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");
            assertThat(afterState.getSpec().getApplicationServiceRef()).as("Owner should remain unchanged").isEqualTo(originalOwner);
        }

        @Test
        @DisplayName("should update ServiceAccount DN list with same owner")
        void testUpdateServiceAccountDN() {
            // Setup
            setupCompleteOwnershipChain();

            // BEFORE STATE
            ServiceAccount beforeState = crdStore.get(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE, "orders-service-sa");
            assertThat(beforeState.getSpec().getDn()).hasSize(2);
            String beforeResourceVersion = beforeState.getMetadata().getResourceVersion();

            // UPDATE OPERATION - Add another DN
            beforeState.getSpec().getDn().add("CN=orders-service-tertiary,OU=ORDERS,O=EXAMPLE,L=CITY,C=US");

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateUpdate(crdStore.get(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE, "orders-service-sa"), beforeState);

            assertThat(result.isValid()).as("Update should be allowed when owner is unchanged").isTrue();

            ServiceAccount updated = crdStore.update(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE, "orders-service-sa", beforeState);

            // AFTER STATE
            ServiceAccount afterState = crdStore.get(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE, "orders-service-sa");

            assertThat(afterState.getSpec().getDn()).hasSize(3).contains("CN=orders-service-tertiary,OU=ORDERS,O=EXAMPLE,L=CITY,C=US");

            assertThat(afterState.getMetadata().getResourceVersion()).isNotEqualTo(beforeResourceVersion);
        }

        @Test
        @DisplayName("should update ACL operations preserving ownership")
        void testUpdateACLOperations() {
            // Setup
            setupCompleteOwnershipChain();
            crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            ACL acl = crdStore.create(CRDKind.ACL, TEST_NAMESPACE, buildACL("orders-events-rw", "orders-service-sa", "orders-events", OWNER_APP_SERVICE));

            // BEFORE STATE
            ACL beforeState = crdStore.get(CRDKind.ACL, TEST_NAMESPACE, "orders-events-rw");
            assertThat(beforeState.getSpec().getOperations()).hasSize(2);

            // UPDATE OPERATION
            beforeState.getSpec().getOperations().add(DESCRIBE);
            beforeState.getSpec().getOperations().add(ALTER);

            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateUpdate(crdStore.get(CRDKind.ACL, TEST_NAMESPACE, "orders-events-rw"), beforeState);

            assertThat(result.isValid()).isTrue();

            ACL updated = crdStore.update(CRDKind.ACL, TEST_NAMESPACE, "orders-events-rw", beforeState);

            // AFTER STATE
            ACL afterState = crdStore.get(CRDKind.ACL, TEST_NAMESPACE, "orders-events-rw");

            assertThat(afterState.getSpec().getOperations()).hasSize(4).containsExactlyInAnyOrder(READ, WRITE, DESCRIBE, ALTER);

            assertThat(afterState.getSpec().getApplicationServiceRef()).as("Owner should remain unchanged").isEqualTo(OWNER_APP_SERVICE);
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
            crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            List<Topic> beforeState = crdStore.list(CRDKind.TOPIC, TEST_NAMESPACE);
            assertThat(beforeState).as("BEFORE: Topic should exist").hasSize(1);

            // DELETE OPERATION
            boolean deleted = crdStore.delete(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");

            assertThat(deleted).as("Delete operation should succeed").isTrue();

            // AFTER STATE
            List<Topic> afterState = crdStore.list(CRDKind.TOPIC, TEST_NAMESPACE);
            assertThat(afterState).as("AFTER: Topic should be removed").isEmpty();

            Topic retrieved = crdStore.get(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");
            assertThat(retrieved).as("Get operation should return null after delete").isNull();
        }

        @Test
        @DisplayName("should reject delete from non-owner ApplicationService")
        void testRejectUnauthorizedDelete() {
            // Setup
            setupCompleteOwnershipChain();
            crdStore.create(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE, buildApplicationService(DIFFERENT_APP_SERVICE));
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // ATTEMPT UNAUTHORIZED DELETE
            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateDelete(topic, DIFFERENT_APP_SERVICE);

            assertThat(result.isValid()).as("Delete should be rejected from non-owner").isFalse();

            assertThat(result.getMessage()).contains(DIFFERENT_APP_SERVICE).contains("cannot delete").contains(OWNER_APP_SERVICE);

            // AFTER STATE: Resource should still exist
            Topic afterState = crdStore.get(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");
            assertThat(afterState).as("Resource should not be deleted").isNotNull();
        }

        @Test
        @DisplayName("should allow delete from owner ApplicationService")
        void testAllowAuthorizedDelete() {
            // Setup
            setupCompleteOwnershipChain();
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // BEFORE STATE
            assertThat(crdStore.<Topic>list(CRDKind.TOPIC, TEST_NAMESPACE)).hasSize(1);

            // AUTHORIZED DELETE
            OwnershipValidator validator = new OwnershipValidator(crdStore);
            ValidationResult result = validator.validateDelete(topic, OWNER_APP_SERVICE);

            assertThat(result.isValid()).as("Delete should be allowed for owner").isTrue();

            boolean deleted = crdStore.delete(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");
            assertThat(deleted).isTrue();

            // AFTER STATE
            assertThat(crdStore.<Topic>list(CRDKind.TOPIC, TEST_NAMESPACE)).isEmpty();
        }

        @Test
        @DisplayName("should delete ACL and maintain referential integrity")
        void testDeleteACL() {
            // Setup
            setupCompleteOwnershipChain();
            crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            crdStore.create(CRDKind.ACL, TEST_NAMESPACE, buildACL("orders-events-rw", "orders-service-sa", "orders-events", OWNER_APP_SERVICE));

            // BEFORE STATE
            assertThat(crdStore.<ACL>list(CRDKind.ACL, TEST_NAMESPACE)).hasSize(1);
            assertThat(crdStore.<Topic>list(CRDKind.TOPIC, TEST_NAMESPACE)).hasSize(1);

            // DELETE OPERATION
            boolean deleted = crdStore.delete(CRDKind.ACL, TEST_NAMESPACE, "orders-events-rw");
            assertThat(deleted).isTrue();

            // AFTER STATE
            assertThat(crdStore.<ACL>list(CRDKind.ACL, TEST_NAMESPACE)).as("ACL should be deleted").isEmpty();

            assertThat(crdStore.<Topic>list(CRDKind.TOPIC, TEST_NAMESPACE)).as("Referenced Topic should remain").hasSize(1);
        }

        @Test
        @DisplayName("should track state through cascade delete scenario")
        void testCascadeDeleteStateTransitions() {
            // Setup complete hierarchy
            setupCompleteOwnershipChain();
            crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            crdStore.create(CRDKind.ACL, TEST_NAMESPACE, buildACL("orders-events-rw", "orders-service-sa", "orders-events", OWNER_APP_SERVICE));

            // BEFORE STATE
            Map<CRDKind, Integer> beforeCounts = Map.of(CRDKind.APPLICATION_SERVICE,
                    crdStore.<ApplicationService>list(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE).size(), CRDKind.KAFKA_CLUSTER,
                    crdStore.<KafkaCluster>list(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE).size(), CRDKind.SERVICE_ACCOUNT,
                    crdStore.<ServiceAccount>list(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE).size(), CRDKind.TOPIC,
                    crdStore.<Topic>list(CRDKind.TOPIC, TEST_NAMESPACE).size(), CRDKind.ACL, crdStore.<ACL>list(CRDKind.ACL, TEST_NAMESPACE).size());

            assertThat(beforeCounts).as("BEFORE: All resources exist")
                    .containsEntry(CRDKind.APPLICATION_SERVICE, 1)
                    .containsEntry(CRDKind.KAFKA_CLUSTER, 1)
                    .containsEntry(CRDKind.SERVICE_ACCOUNT, 1)
                    .containsEntry(CRDKind.TOPIC, 1)
                    .containsEntry(CRDKind.ACL, 1);

            // CASCADE DELETE SIMULATION
            // In production, Kubernetes would handle cascade with ownerReferences
            // Here we simulate manual cascade
            crdStore.delete(CRDKind.ACL, TEST_NAMESPACE, "orders-events-rw");
            crdStore.delete(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");
            crdStore.delete(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE, "orders-service-sa");
            crdStore.delete(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE, "prod-cluster");
            crdStore.delete(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE, OWNER_APP_SERVICE);

            // AFTER STATE
            Map<CRDKind, Integer> afterCounts = Map.of(CRDKind.APPLICATION_SERVICE, crdStore.<ApplicationService>list(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE).size(),
                    CRDKind.KAFKA_CLUSTER, crdStore.<KafkaCluster>list(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE).size(), CRDKind.SERVICE_ACCOUNT,
                    crdStore.<ServiceAccount>list(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE).size(), CRDKind.TOPIC,
                    crdStore.<Topic>list(CRDKind.TOPIC, TEST_NAMESPACE).size(), CRDKind.ACL, crdStore.<ACL>list(CRDKind.ACL, TEST_NAMESPACE).size());

            assertThat(afterCounts).as("AFTER: All resources should be deleted")
                    .containsEntry(CRDKind.APPLICATION_SERVICE, 0)
                    .containsEntry(CRDKind.KAFKA_CLUSTER, 0)
                    .containsEntry(CRDKind.SERVICE_ACCOUNT, 0)
                    .containsEntry(CRDKind.TOPIC, 0)
                    .containsEntry(CRDKind.ACL, 0);
        }
    }

    @Nested
    @DisplayName("Event Publishing Tests")
    class EventPublishingTest {

        private List<ReconciliationEvent> capturedEvents;
        private ReconciliationEventListener eventCapture;

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
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            // VERIFY EVENTS
            assertThat(capturedEvents).as("Should have BEFORE and AFTER events").hasSize(2);

            ReconciliationEvent beforeEvent = capturedEvents.get(0);
            assertThat(beforeEvent.getPhase()).isEqualTo(BEFORE);
            assertThat(beforeEvent.getOperation()).isEqualTo(CREATE);
            assertThat(beforeEvent.getResourceKind()).isEqualTo(CRDKind.TOPIC);
            assertThat(beforeEvent.getResourceName()).isEqualTo("orders-events");
            assertThat(beforeEvent.getResourceNamespace()).isEqualTo(TEST_NAMESPACE);
            assertThat(beforeEvent.getApplicationService()).isEqualTo(OWNER_APP_SERVICE);
            assertThat(beforeEvent.getResult()).isNull();

            ReconciliationEvent afterEvent = capturedEvents.get(1);
            assertThat(afterEvent.getPhase()).isEqualTo(AFTER);
            assertThat(afterEvent.getOperation()).isEqualTo(CREATE);
            assertThat(afterEvent.getResourceKind()).isEqualTo(CRDKind.TOPIC);
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
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            capturedEvents.clear(); // Clear setup events

            // UPDATE OPERATION
            topic.getSpec().setPartitions(6);
            Topic updated = crdStore.update(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events", topic);

            // VERIFY EVENTS
            assertThat(capturedEvents).as("Should have BEFORE and AFTER events").hasSize(2);

            ReconciliationEvent beforeEvent = capturedEvents.get(0);
            assertThat(beforeEvent.getPhase()).isEqualTo(BEFORE);
            assertThat(beforeEvent.getOperation()).isEqualTo(UPDATE);
            assertThat(beforeEvent.getResourceKind()).isEqualTo(CRDKind.TOPIC);
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
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            capturedEvents.clear(); // Clear setup events

            // DELETE OPERATION
            boolean deleted = crdStore.delete(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");

            // VERIFY EVENTS
            assertThat(deleted).isTrue();
            assertThat(capturedEvents).as("Should have BEFORE and AFTER events").hasSize(2);

            ReconciliationEvent beforeEvent = capturedEvents.get(0);
            assertThat(beforeEvent.getPhase()).isEqualTo(BEFORE);
            assertThat(beforeEvent.getOperation()).isEqualTo(DELETE);
            assertThat(beforeEvent.getResourceKind()).isEqualTo(CRDKind.TOPIC);
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
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            capturedEvents.clear();

            // CREATE OPERATION - should fail due to duplicate resource
            try {
                Topic duplicate = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            } catch (Exception e) {
                // Expected failure
            }

            // VERIFY EVENTS
            assertThat(capturedEvents).as("Should have BEFORE and AFTER(FAILURE) events").hasSize(2);

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
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));
            assertThat(capturedEvents).hasSize(2); // BEFORE + AFTER

            // UPDATE
            topic.getSpec().setPartitions(6);
            crdStore.update(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events", topic);
            assertThat(capturedEvents).hasSize(4); // +2 for update

            // DELETE
            crdStore.delete(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events");
            assertThat(capturedEvents).hasSize(6); // +2 for delete

            // VERIFY FULL LIFECYCLE
            assertThat(capturedEvents.stream().filter(e -> e.getPhase() == BEFORE).count()).as("Should have 3 BEFORE events").isEqualTo(3);

            assertThat(capturedEvents.stream().filter(e -> e.getPhase() == AFTER).count()).as("Should have 3 AFTER events").isEqualTo(3);

            assertThat(capturedEvents.stream().filter(e -> e.getResult() == SUCCESS).count()).as("All operations should be successful").isEqualTo(3);

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
            Topic topic = crdStore.create(CRDKind.TOPIC, TEST_NAMESPACE, buildTopic("orders-events", "orders-service-sa", OWNER_APP_SERVICE));

            ReconciliationEvent afterCreate = capturedEvents.get(1);
            Long versionAfterCreate = afterCreate.getResourceVersion();
            assertThat(versionAfterCreate).isNotNull().isPositive();

            // UPDATE
            topic.getSpec().setPartitions(6);
            crdStore.update(CRDKind.TOPIC, TEST_NAMESPACE, "orders-events", topic);

            ReconciliationEvent afterUpdate = capturedEvents.get(3);
            Long versionAfterUpdate = afterUpdate.getResourceVersion();
            assertThat(versionAfterUpdate).as("Resource version should increment after update").isGreaterThan(versionAfterCreate);
        }
    }

    // Helper methods
    private void setupCompleteOwnershipChain() {
        crdStore.create(CRDKind.APPLICATION_SERVICE, TEST_NAMESPACE, buildApplicationService(OWNER_APP_SERVICE));
        crdStore.create(CRDKind.KAFKA_CLUSTER, TEST_NAMESPACE, buildKafkaCluster("prod-cluster", OWNER_APP_SERVICE));
        crdStore.create(CRDKind.SERVICE_ACCOUNT, TEST_NAMESPACE, buildServiceAccount("orders-service-sa", "prod-cluster", OWNER_APP_SERVICE));
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

    private KafkaCluster buildKafkaCluster(String clusterId, String appServiceRef) {
        KafkaCluster vCluster = new KafkaCluster();
        vCluster.setMetadata(new ObjectMeta());
        vCluster.getMetadata().setName(clusterId);
        vCluster.getMetadata().setNamespace(TEST_NAMESPACE);

        KafkaClusterSpec spec = new KafkaClusterSpec();
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
        spec.setDn(new ArrayList<>(List.of("CN=" + name + ",OU=ORDERS,O=EXAMPLE,L=CITY,C=US", "CN=" + name + "-backup,OU=ORDERS,O=EXAMPLE,L=CITY,C=US")));
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
        spec.setOperations(new ArrayList<>(List.of(READ, WRITE)));
        spec.setApplicationServiceRef(appServiceRef);
        acl.setSpec(spec);

        return acl;
    }
}
