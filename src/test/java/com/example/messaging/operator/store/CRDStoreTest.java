package com.example.messaging.operator.store;

import static org.assertj.core.api.Assertions.*;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.events.ReconciliationEvent;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for CRDStore. Tests CRUD operations, resource versioning, event publishing, and ownership enforcement.
 */
@DisplayName("CRDStore Unit Tests")
class CRDStoreTest {

    private CRDStore store;
    private static final String NAMESPACE = "test-namespace";
    private static final String APP_SERVICE = "orders-service";

    @BeforeEach
    void setup() {
        store = new CRDStore();
    }

    @AfterEach
    void cleanup() {
        store.clear();
    }

    // ==================== CREATE OPERATION TESTS ====================

    @Nested
    @DisplayName("CREATE Operation Tests")
    class CreateOperationTests {

        @Test
        @DisplayName("should create resource and assign resource version")
        void testCreateAssignsResourceVersion() {
            ApplicationService appService = buildApplicationService(APP_SERVICE);

            ApplicationService created = store.create("ApplicationService", NAMESPACE, appService);

            assertThat(created.getMetadata().getResourceVersion())
                    .isNotNull()
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("should create resource and assign UID")
        void testCreateAssignsUID() {
            ApplicationService appService = buildApplicationService(APP_SERVICE);

            ApplicationService created = store.create("ApplicationService", NAMESPACE, appService);

            assertThat(created.getMetadata().getUid())
                    .isNotNull()
                    .isNotEmpty();
        }

        @Test
        @DisplayName("should increment resource version for subsequent creates")
        void testCreateIncrementsResourceVersion() {
            ApplicationService app1 = buildApplicationService("app1");
            ApplicationService app2 = buildApplicationService("app2");

            ApplicationService created1 = store.create("ApplicationService", NAMESPACE, app1);
            ApplicationService created2 = store.create("ApplicationService", NAMESPACE, app2);

            assertThat(created1.getMetadata().getResourceVersion())
                    .isEqualTo("1");
            assertThat(created2.getMetadata().getResourceVersion())
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("should throw exception when creating duplicate resource")
        void testCreateDuplicateThrowsException() {
            ApplicationService appService = buildApplicationService(APP_SERVICE);
            store.create("ApplicationService", NAMESPACE, appService);

            assertThatThrownBy(() -> store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Resource already exists");
        }

        @Test
        @DisplayName("should publish BEFORE and AFTER events on successful create")
        void testCreatePublishesEvents() throws InterruptedException {
            List<ReconciliationEvent> events = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(2); // BEFORE + AFTER

            store.getEventPublisher().addListener(event -> {
                events.add(event);
                latch.countDown();
            });

            ApplicationService appService = buildApplicationService(APP_SERVICE);
            store.create("ApplicationService", NAMESPACE, appService);

            latch.await(1, TimeUnit.SECONDS);

            assertThat(events)
                    .hasSize(2);
            assertThat(events.get(0).getPhase())
                    .isEqualTo(ReconciliationEvent.Phase.BEFORE);
            assertThat(events.get(1).getPhase())
                    .isEqualTo(ReconciliationEvent.Phase.AFTER);
            assertThat(events.get(1).getResult())
                    .isEqualTo(ReconciliationEvent.Result.SUCCESS);
        }

        @Test
        @DisplayName("should publish FAILURE event on duplicate create")
        void testCreatePublishesFailureEventOnDuplicate() throws InterruptedException {
            List<ReconciliationEvent> events = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(4); // First: BEFORE+AFTER, Second: BEFORE+AFTER

            store.getEventPublisher().addListener(event -> {
                events.add(event);
                latch.countDown();
            });

            ApplicationService appService = buildApplicationService(APP_SERVICE);
            store.create("ApplicationService", NAMESPACE, appService);

            try {
                store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
            } catch (IllegalStateException e) {
                // Expected
            }

            latch.await(1, TimeUnit.SECONDS);

            assertThat(events)
                    .hasSizeGreaterThanOrEqualTo(3);
            ReconciliationEvent failureEvent = events.get(events.size() - 1);
            assertThat(failureEvent.getPhase())
                    .isEqualTo(ReconciliationEvent.Phase.AFTER);
            assertThat(failureEvent.getResult())
                    .isEqualTo(ReconciliationEvent.Result.FAILURE);
        }

        @Test
        @DisplayName("should enforce ownership on VirtualCluster create")
        void testCreateEnforcesOwnershipForVirtualCluster() {
            VirtualCluster vCluster = buildVirtualCluster("prod-cluster", "nonexistent-app");

            assertThatThrownBy(() -> store.create("VirtualCluster", NAMESPACE, vCluster))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Ownership validation failed");
        }

        @Test
        @DisplayName("should allow VirtualCluster create when ApplicationService exists")
        void testCreateAllowsVirtualClusterWithValidOwnership() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));

            VirtualCluster vCluster = buildVirtualCluster("prod-cluster", APP_SERVICE);
            VirtualCluster created = store.create("VirtualCluster", NAMESPACE, vCluster);

            assertThat(created)
                    .isNotNull();
            assertThat(created.getMetadata().getResourceVersion())
                    .isEqualTo("2");
        }
    }

    // ==================== UPDATE OPERATION TESTS ====================

    @Nested
    @DisplayName("UPDATE Operation Tests")
    class UpdateOperationTests {

        @Test
        @DisplayName("should update resource and increment resource version")
        void testUpdateIncrementsResourceVersion() {
            ApplicationService appService = buildApplicationService(APP_SERVICE);
            store.create("ApplicationService", NAMESPACE, appService);

            appService.getSpec().setName("updated-name");
            ApplicationService updated = store.update("ApplicationService", NAMESPACE, APP_SERVICE, appService);

            assertThat(updated.getMetadata().getResourceVersion())
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("should throw exception when updating non-existent resource")
        void testUpdateNonExistentThrowsException() {
            ApplicationService appService = buildApplicationService(APP_SERVICE);

            assertThatThrownBy(() -> store.update("ApplicationService", NAMESPACE, APP_SERVICE, appService))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Resource not found");
        }

        @Test
        @DisplayName("should publish BEFORE and AFTER events on successful update")
        void testUpdatePublishesEvents() throws InterruptedException {
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));

            List<ReconciliationEvent> events = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(2); // BEFORE + AFTER

            store.getEventPublisher().addListener(event -> {
                events.add(event);
                latch.countDown();
            });

            ApplicationService appService = store.get("ApplicationService", NAMESPACE, APP_SERVICE);
            appService.getSpec().setName("updated");
            store.update("ApplicationService", NAMESPACE, APP_SERVICE, appService);

            latch.await(1, TimeUnit.SECONDS);

            assertThat(events)
                    .hasSize(2);
            assertThat(events.get(0).getPhase())
                    .isEqualTo(ReconciliationEvent.Phase.BEFORE);
            assertThat(events.get(0).getOperation())
                    .isEqualTo(ReconciliationEvent.Operation.UPDATE);
            assertThat(events.get(1).getResult())
                    .isEqualTo(ReconciliationEvent.Result.SUCCESS);
        }

        @Test
        @DisplayName("should enforce ownership immutability on update")
        void testUpdateEnforcesOwnershipImmutability() {
            // Setup ownership chain
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
            store.create("ApplicationService", NAMESPACE, buildApplicationService("other-service"));
            store.create("VirtualCluster", NAMESPACE, buildVirtualCluster("prod-cluster", APP_SERVICE));

            // Try to change owner - create new VirtualCluster with different owner
            VirtualCluster modifiedCluster = buildVirtualCluster("prod-cluster", "other-service");

            assertThatThrownBy(() -> store.update("VirtualCluster", NAMESPACE, "prod-cluster", modifiedCluster))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Ownership validation failed")
                    .hasMessageContaining("Cannot change applicationServiceRef");
        }

        @Test
        @DisplayName("should allow update when ownership unchanged")
        void testUpdateAllowsWhenOwnershipUnchanged() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
            store.create("VirtualCluster", NAMESPACE, buildVirtualCluster("prod-cluster", APP_SERVICE));

            VirtualCluster vCluster = store.get("VirtualCluster", NAMESPACE, "prod-cluster");
            vCluster.getSpec().setClusterId("prod-cluster-updated");

            VirtualCluster updated = store.update("VirtualCluster", NAMESPACE, "prod-cluster", vCluster);

            assertThat(updated.getSpec().getClusterId())
                    .isEqualTo("prod-cluster-updated");
        }
    }

    // ==================== DELETE OPERATION TESTS ====================

    @Nested
    @DisplayName("DELETE Operation Tests")
    class DeleteOperationTests {

        @Test
        @DisplayName("should delete existing resource")
        void testDeleteExistingResource() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));

            boolean deleted = store.delete("ApplicationService", NAMESPACE, APP_SERVICE);

            assertThat(deleted)
                    .isTrue();
            assertThat(store.<ApplicationService>get("ApplicationService", NAMESPACE, APP_SERVICE))
                    .isNull();
        }

        @Test
        @DisplayName("should return false when deleting non-existent resource")
        void testDeleteNonExistentResource() {
            boolean deleted = store.delete("ApplicationService", NAMESPACE, "nonexistent");

            assertThat(deleted)
                    .isFalse();
        }

        @Test
        @DisplayName("should publish BEFORE and AFTER events on successful delete")
        void testDeletePublishesEvents() throws InterruptedException {
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));

            List<ReconciliationEvent> events = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(2); // BEFORE + AFTER

            store.getEventPublisher().addListener(event -> {
                events.add(event);
                latch.countDown();
            });

            store.delete("ApplicationService", NAMESPACE, APP_SERVICE);

            latch.await(1, TimeUnit.SECONDS);

            assertThat(events)
                    .hasSize(2);
            assertThat(events.get(0).getPhase())
                    .isEqualTo(ReconciliationEvent.Phase.BEFORE);
            assertThat(events.get(0).getOperation())
                    .isEqualTo(ReconciliationEvent.Operation.DELETE);
            assertThat(events.get(1).getResult())
                    .isEqualTo(ReconciliationEvent.Result.SUCCESS);
        }

        @Test
        @DisplayName("should publish NOT_FOUND result when deleting non-existent resource")
        void testDeletePublishesNotFoundResult() throws InterruptedException {
            List<ReconciliationEvent> events = new ArrayList<>();
            CountDownLatch latch = new CountDownLatch(2);

            store.getEventPublisher().addListener(event -> {
                events.add(event);
                latch.countDown();
            });

            store.delete("ApplicationService", NAMESPACE, "nonexistent");

            latch.await(1, TimeUnit.SECONDS);

            ReconciliationEvent afterEvent = events.get(1);
            assertThat(afterEvent.getResult())
                    .isEqualTo(ReconciliationEvent.Result.NOT_FOUND);
        }

        @Test
        @DisplayName("should enforce ownership on delete")
        void testDeleteEnforcesOwnership() {
            // Setup
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
            store.create("ApplicationService", NAMESPACE, buildApplicationService("other-service"));
            store.create("VirtualCluster", NAMESPACE, buildVirtualCluster("prod-cluster", APP_SERVICE));

            // Try to delete with wrong owner
            assertThatThrownBy(() -> store.delete("VirtualCluster", NAMESPACE, "prod-cluster", "other-service"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Ownership validation failed");
        }

        @Test
        @DisplayName("should allow delete by owner")
        void testDeleteAllowsByOwner() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
            store.create("VirtualCluster", NAMESPACE, buildVirtualCluster("prod-cluster", APP_SERVICE));

            boolean deleted = store.delete("VirtualCluster", NAMESPACE, "prod-cluster", APP_SERVICE);

            assertThat(deleted)
                    .isTrue();
        }
    }

    // ==================== GET OPERATION TESTS ====================

    @Nested
    @DisplayName("GET Operation Tests")
    class GetOperationTests {

        @Test
        @DisplayName("should get existing resource")
        void testGetExistingResource() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));

            ApplicationService retrieved = store.get("ApplicationService", NAMESPACE, APP_SERVICE);

            assertThat(retrieved)
                    .isNotNull();
            assertThat(retrieved.getSpec().getName())
                    .isEqualTo(APP_SERVICE);
        }

        @Test
        @DisplayName("should return null for non-existent resource")
        void testGetNonExistentResource() {
            ApplicationService retrieved = store.get("ApplicationService", NAMESPACE, "nonexistent");

            assertThat(retrieved)
                    .isNull();
        }

        @Test
        @DisplayName("should get resource with correct metadata")
        void testGetResourceWithMetadata() {
            ApplicationService appService = buildApplicationService(APP_SERVICE);
            store.create("ApplicationService", NAMESPACE, appService);

            ApplicationService retrieved = store.get("ApplicationService", NAMESPACE, APP_SERVICE);

            assertThat(retrieved.getMetadata().getResourceVersion())
                    .isEqualTo("1");
            assertThat(retrieved.getMetadata().getUid())
                    .isNotNull();
        }
    }

    // ==================== LIST OPERATION TESTS ====================

    @Nested
    @DisplayName("LIST Operation Tests")
    class ListOperationTests {

        @Test
        @DisplayName("should list all resources of a kind in namespace")
        void testListAllResources() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService("app1"));
            store.create("ApplicationService", NAMESPACE, buildApplicationService("app2"));
            store.create("ApplicationService", NAMESPACE, buildApplicationService("app3"));

            List<ApplicationService> list = store.list("ApplicationService", NAMESPACE);

            assertThat(list)
                    .hasSize(3);
        }

        @Test
        @DisplayName("should return empty list when no resources exist")
        void testListEmptyNamespace() {
            List<ApplicationService> list = store.list("ApplicationService", NAMESPACE);

            assertThat(list)
                    .isEmpty();
        }

        @Test
        @DisplayName("should not list resources from different namespace")
        void testListIsolatesNamespaces() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService("app1"));
            store.create("ApplicationService", "other-namespace", buildApplicationService("app2"));

            List<ApplicationService> list = store.list("ApplicationService", NAMESPACE);

            assertThat(list)
                    .hasSize(1);
            assertThat(list.get(0).getSpec().getName())
                    .isEqualTo("app1");
        }

        @Test
        @DisplayName("should not list different resource kinds")
        void testListIsolatesResourceKinds() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService(APP_SERVICE));
            store.create("VirtualCluster", NAMESPACE, buildVirtualCluster("cluster1", APP_SERVICE));

            List<ApplicationService> apps = store.list("ApplicationService", NAMESPACE);
            List<VirtualCluster> clusters = store.list("VirtualCluster", NAMESPACE);

            assertThat(apps)
                    .hasSize(1);
            assertThat(clusters)
                    .hasSize(1);
        }
    }

    // ==================== CLEAR OPERATION TESTS ====================

    @Nested
    @DisplayName("CLEAR Operation Tests")
    class ClearOperationTests {

        @Test
        @DisplayName("should clear all resources")
        void testClearAllResources() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService("app1"));
            store.create("ApplicationService", NAMESPACE, buildApplicationService("app2"));

            store.clear();

            List<ApplicationService> list = store.list("ApplicationService", NAMESPACE);
            assertThat(list)
                    .isEmpty();
        }

        @Test
        @DisplayName("should reset resource version counter")
        void testClearResetsResourceVersion() {
            store.create("ApplicationService", NAMESPACE, buildApplicationService("app1"));
            store.clear();
            ApplicationService appService = store.create("ApplicationService", NAMESPACE, buildApplicationService("app2"));

            assertThat(appService.getMetadata().getResourceVersion())
                    .isEqualTo("1");
        }
    }

    // ==================== CONCURRENCY TESTS ====================

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("should handle concurrent creates")
        void testConcurrentCreates() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Exception> exceptions = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        store.create("ApplicationService", NAMESPACE, buildApplicationService("app" + index));
                    } catch (Exception e) {
                        synchronized (exceptions) {
                            exceptions.add(e);
                        }
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await(5, TimeUnit.SECONDS);

            assertThat(exceptions)
                    .isEmpty();
            assertThat(store.<ApplicationService>list("ApplicationService", NAMESPACE))
                    .hasSize(threadCount);
        }
    }

    // ==================== HELPER METHODS ====================

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
}
