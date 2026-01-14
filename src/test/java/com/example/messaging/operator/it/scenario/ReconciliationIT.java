package com.example.messaging.operator.it.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.ApplicationService;
import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.crd.VirtualCluster;
import com.example.messaging.operator.events.ReconciliationEvent;
import com.example.messaging.operator.it.base.ScenarioITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.store.CRDKind;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scenario tests for reconciliation event publishing and listener integration.
 */
@DisplayName("Reconciliation Event Scenario Tests")
public class ReconciliationIT extends ScenarioITBase {

    @Test
    @DisplayName("Scenario: CREATE triggers reconciliation events (START → END)")
    void testCreateTriggersReconciliationEvents() {
        // Setup: Create event listener
        List<ReconciliationEvent> receivedEvents = new CopyOnWriteArrayList<>();
        store.addReconciliationListener(receivedEvents::add);

        // Action: Create ApplicationService
        ApplicationService app = TestDataBuilder.applicationService().name("test-app").appName("test-app").createIn(k8sClient);

        store.create(CRDKind.APPLICATION_SERVICE, "default", app);

        // Verify: Two events fired (START → END)
        assertThat(receivedEvents)
                .hasSize(2);

        // Verify START event
        ReconciliationEvent startEvent = receivedEvents.get(0);
        assertThat(startEvent.getPhase())
                .isEqualTo(ReconciliationEvent.Phase.BEFORE);
        assertThat(startEvent.getOperation())
                .isEqualTo(ReconciliationEvent.Operation.CREATE);
        assertThat(startEvent.getResourceKind())
                .isEqualTo(CRDKind.APPLICATION_SERVICE);
        assertThat(startEvent.getResourceName())
                .isEqualTo("test-app");
        assertThat(startEvent.getResourceNamespace())
                .isEqualTo("default");
        assertThat(startEvent.getMessage())
                .isNull();

        // Verify END event
        ReconciliationEvent endEvent = receivedEvents.get(1);
        assertThat(endEvent.getPhase())
                .isEqualTo(ReconciliationEvent.Phase.AFTER);
        assertThat(endEvent.getOperation())
                .isEqualTo(ReconciliationEvent.Operation.CREATE);
        assertThat(endEvent.getResourceKind())
                .isEqualTo(CRDKind.APPLICATION_SERVICE);
        assertThat(endEvent.getResourceName())
                .isEqualTo("test-app");
        assertThat(endEvent.getResourceNamespace())
                .isEqualTo("default");
        assertThat(endEvent.isSuccess())
                .isTrue();
        assertThat(endEvent.getMessage())
                .contains("CREATE completed successfully");
    }

    @Test
    @DisplayName("Scenario: UPDATE triggers reconciliation events (START → END)")
    void testUpdateTriggersReconciliationEvents() {
        // Setup: Create resource first
        ApplicationService app = TestDataBuilder.applicationService().name("test-app").appName("test-app").createIn(k8sClient);
        store.create(CRDKind.APPLICATION_SERVICE, "default", app);

        // Setup: Event listener after initial create
        List<ReconciliationEvent> receivedEvents = new CopyOnWriteArrayList<>();
        store.addReconciliationListener(receivedEvents::add);

        // Action: Update the resource
        app.getSpec().setName("test-app-updated");
        store.update(CRDKind.APPLICATION_SERVICE, "default", "test-app", app);

        // Verify: Two events fired (START → END)
        assertThat(receivedEvents)
                .hasSize(2);

        ReconciliationEvent startEvent = receivedEvents.get(0);
        assertThat(startEvent.getPhase())
                .isEqualTo(ReconciliationEvent.Phase.BEFORE);
        assertThat(startEvent.getOperation())
                .isEqualTo(ReconciliationEvent.Operation.UPDATE);
        assertThat(startEvent.getResourceKind())
                .isEqualTo(CRDKind.APPLICATION_SERVICE);

        ReconciliationEvent endEvent = receivedEvents.get(1);
        assertThat(endEvent.getPhase())
                .isEqualTo(ReconciliationEvent.Phase.AFTER);
        assertThat(endEvent.getOperation())
                .isEqualTo(ReconciliationEvent.Operation.UPDATE);
        assertThat(endEvent.isSuccess())
                .isTrue();
        assertThat(endEvent.getMessage())
                .contains("UPDATE completed successfully");
    }

    @Test
    @DisplayName("Scenario: DELETE triggers reconciliation events (START → END)")
    void testDeleteTriggersReconciliationEvents() {
        // Setup: Create resource first
        ApplicationService app = TestDataBuilder.applicationService().name("test-app").appName("test-app").createIn(k8sClient);
        store.create(CRDKind.APPLICATION_SERVICE, "default", app);

        // Setup: Event listener after initial create
        List<ReconciliationEvent> receivedEvents = new CopyOnWriteArrayList<>();
        store.addReconciliationListener(receivedEvents::add);

        // Action: Delete the resource
        store.delete(CRDKind.APPLICATION_SERVICE, "default", "test-app");

        // Verify: Two events fired (START → END)
        assertThat(receivedEvents)
                .hasSize(2);

        ReconciliationEvent startEvent = receivedEvents.get(0);
        assertThat(startEvent.getPhase())
                .isEqualTo(ReconciliationEvent.Phase.BEFORE);
        assertThat(startEvent.getOperation())
                .isEqualTo(ReconciliationEvent.Operation.DELETE);
        assertThat(startEvent.getResourceKind())
                .isEqualTo(CRDKind.APPLICATION_SERVICE);

        ReconciliationEvent endEvent = receivedEvents.get(1);
        assertThat(endEvent.getPhase())
                .isEqualTo(ReconciliationEvent.Phase.AFTER);
        assertThat(endEvent.getOperation())
                .isEqualTo(ReconciliationEvent.Operation.DELETE);
        assertThat(endEvent.isSuccess())
                .isTrue();
        assertThat(endEvent.getMessage())
                .contains("DELETE completed successfully");
    }

    @Test
    @DisplayName("Scenario: Failed validation triggers FAILED reconciliation event")
    void testFailedValidationTriggersFailedEvent() {
        // Setup: Create event listener
        List<ReconciliationEvent> receivedEvents = new CopyOnWriteArrayList<>();
        store.addReconciliationListener(receivedEvents::add);

        // Setup: Create an ApplicationService but NOT the ServiceAccount it references
        ApplicationService app = TestDataBuilder.applicationService().name("test-app").appName("test-app").createIn(k8sClient);
        store.create(CRDKind.APPLICATION_SERVICE, "default", app);

        // Clear events from setup
        receivedEvents.clear();

        // Action: Try to create Topic with non-existent ServiceAccount (should fail validation)
        Topic topic = TestDataBuilder.topic()
                .name("orphan-topic")
                .topicName("orphan.topic")
                .ownedBy(app)
                .serviceRef("nonexistent-sa") // This ServiceAccount doesn't
                                              // exist
                .build();

        // This should trigger validation failure
        try {
            store.create(CRDKind.TOPIC, "default", topic);
        } catch (Exception e) {
            // Expected to fail
        }

        // Verify: Events include failure (BEFORE and AFTER with FAILURE result)
        assertThat(receivedEvents)
                .hasSizeGreaterThanOrEqualTo(2);

        // Verify BEFORE event
        ReconciliationEvent beforeEvent = receivedEvents.get(0);
        assertThat(beforeEvent.getPhase())
                .isEqualTo(ReconciliationEvent.Phase.BEFORE);
        assertThat(beforeEvent.getOperation())
                .isEqualTo(ReconciliationEvent.Operation.CREATE);

        // Verify AFTER event with FAILURE result
        ReconciliationEvent afterEvent = receivedEvents.get(1);
        assertThat(afterEvent.getPhase())
                .isEqualTo(ReconciliationEvent.Phase.AFTER);
        assertThat(afterEvent.getOperation())
                .isEqualTo(ReconciliationEvent.Operation.CREATE);
        assertThat(afterEvent.getResourceKind())
                .isEqualTo(CRDKind.TOPIC);
        assertThat(afterEvent.isSuccess())
                .isFalse();
        assertThat(afterEvent.getResult()).isIn(ReconciliationEvent.Result.FAILURE, ReconciliationEvent.Result.VALIDATION_ERROR);
        assertThat(afterEvent.getMessage())
                .isNotNull();
    }

    @Test
    @DisplayName("Scenario: Multiple listeners all receive events in order")
    void testMultipleListenersReceiveEvents() {
        // Setup: Create multiple event listeners
        List<ReconciliationEvent> listener1Events = new CopyOnWriteArrayList<>();
        List<ReconciliationEvent> listener2Events = new CopyOnWriteArrayList<>();
        List<ReconciliationEvent> listener3Events = new CopyOnWriteArrayList<>();

        store.addReconciliationListener(listener1Events::add);
        store.addReconciliationListener(listener2Events::add);
        store.addReconciliationListener(listener3Events::add);

        // Action: Create, update, and delete a resource
        ApplicationService app = TestDataBuilder.applicationService().name("multi-listener-app").appName("multi-listener-app").createIn(k8sClient);

        store.create(CRDKind.APPLICATION_SERVICE, "default", app);

        app.getSpec().setName("multi-listener-app-updated");
        store.update(CRDKind.APPLICATION_SERVICE, "default", "multi-listener-app", app);

        store.delete(CRDKind.APPLICATION_SERVICE, "default", "multi-listener-app");

        // Verify: All listeners received all 6 events (2 per operation × 3 operations)
        assertThat(listener1Events)
                .hasSize(6);
        assertThat(listener2Events)
                .hasSize(6);
        assertThat(listener3Events)
                .hasSize(6);

        // Verify: All listeners received same events in same order
        for (int i = 0; i < 6; i++) {
            ReconciliationEvent event1 = listener1Events.get(i);
            ReconciliationEvent event2 = listener2Events.get(i);
            ReconciliationEvent event3 = listener3Events.get(i);

            assertThat(event1.getOperation())
                    .isEqualTo(event2.getOperation());
            assertThat(event1.getOperation())
                    .isEqualTo(event3.getOperation());
            assertThat(event1.getPhase())
                    .isEqualTo(event2.getPhase());
            assertThat(event1.getPhase())
                    .isEqualTo(event3.getPhase());
        }

        // Verify: Event sequence is correct (CREATE START/END, UPDATE START/END, DELETE START/END)
        assertThat(listener1Events.get(0).getOperation())
                .isEqualTo(ReconciliationEvent.Operation.CREATE);
        assertThat(listener1Events.get(0).getPhase())
                .isEqualTo(ReconciliationEvent.Phase.BEFORE);
        assertThat(listener1Events.get(1).getOperation())
                .isEqualTo(ReconciliationEvent.Operation.CREATE);
        assertThat(listener1Events.get(1).getPhase())
                .isEqualTo(ReconciliationEvent.Phase.AFTER);

        assertThat(listener1Events.get(2).getOperation())
                .isEqualTo(ReconciliationEvent.Operation.UPDATE);
        assertThat(listener1Events.get(2).getPhase())
                .isEqualTo(ReconciliationEvent.Phase.BEFORE);
        assertThat(listener1Events.get(3).getOperation())
                .isEqualTo(ReconciliationEvent.Operation.UPDATE);
        assertThat(listener1Events.get(3).getPhase())
                .isEqualTo(ReconciliationEvent.Phase.AFTER);

        assertThat(listener1Events.get(4).getOperation())
                .isEqualTo(ReconciliationEvent.Operation.DELETE);
        assertThat(listener1Events.get(4).getPhase())
                .isEqualTo(ReconciliationEvent.Phase.BEFORE);
        assertThat(listener1Events.get(5).getOperation())
                .isEqualTo(ReconciliationEvent.Operation.DELETE);
        assertThat(listener1Events.get(5).getPhase())
                .isEqualTo(ReconciliationEvent.Phase.AFTER);
    }

    @Test
    @DisplayName("Scenario: Events contain correct resource metadata")
    void testEventsContainCorrectMetadata() {
        // Setup: Create event listener
        List<ReconciliationEvent> receivedEvents = new CopyOnWriteArrayList<>();
        store.addReconciliationListener(receivedEvents::add);

        // Action: Create complete ownership chain
        ApplicationService app = TestDataBuilder.applicationService().name("metadata-app").appName("metadata-app").createIn(k8sClient);
        store.create(CRDKind.APPLICATION_SERVICE, "default", app);

        VirtualCluster vc = TestDataBuilder.virtualCluster()
                .name("metadata-vc")
                .clusterId("metadata-cluster")
                .applicationServiceRef("metadata-app")
                .ownedBy(app)
                .createIn(k8sClient);
        store.create(CRDKind.VIRTUAL_CLUSTER, "default", vc);

        // Verify: Each resource type triggers correct events
        assertThat(receivedEvents).hasSizeGreaterThanOrEqualTo(4); // At least 2 per resource

        // Find ApplicationService events
        List<ReconciliationEvent> appEvents = receivedEvents.stream().filter(e -> e.getResourceKind().equals(CRDKind.APPLICATION_SERVICE)).toList();

        assertThat(appEvents)
                .hasSize(2);
        assertThat(appEvents.get(0).getResourceName())
                .isEqualTo("metadata-app");
        assertThat(appEvents.get(0).getResourceNamespace())
                .isEqualTo("default");

        // Find VirtualCluster events
        List<ReconciliationEvent> vcEvents = receivedEvents.stream().filter(e -> e.getResourceKind().equals(CRDKind.VIRTUAL_CLUSTER)).toList();

        assertThat(vcEvents)
                .hasSize(2);
        assertThat(vcEvents.get(0).getResourceName())
                .isEqualTo("metadata-vc");
        assertThat(vcEvents.get(0).getResourceNamespace())
                .isEqualTo("default");
    }
}
