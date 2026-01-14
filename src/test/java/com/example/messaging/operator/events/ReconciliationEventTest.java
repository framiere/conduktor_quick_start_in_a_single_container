package com.example.messaging.operator.events;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for operator event system.
 * Validates event creation, publishing, and listener notification.
 */
@DisplayName("Reconciliation Event System Tests")
class ReconciliationEventTest {

    private static final String TEST_NAMESPACE = "test-namespace";
    private static final String TEST_RESOURCE = "test-resource";
    private static final String TEST_APP_SERVICE = "test-app-service";

    @Nested
    @DisplayName("Event Creation and Properties")
    class EventCreationTest {

        @Test
        @DisplayName("should create BEFORE event with all required fields")
        void testCreateBeforeEvent() {
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName(TEST_RESOURCE)
                    .resourceNamespace(TEST_NAMESPACE)
                    .applicationService(TEST_APP_SERVICE)
                    .build();

            assertThat(event).isNotNull().satisfies(e -> {
                assertThat(e.getPhase()).isEqualTo(ReconciliationEvent.Phase.BEFORE);
                assertThat(e.getOperation()).isEqualTo(ReconciliationEvent.Operation.CREATE);
                assertThat(e.getResourceKind()).isEqualTo("Topic");
                assertThat(e.getResourceName()).isEqualTo(TEST_RESOURCE);
                assertThat(e.getResourceNamespace()).isEqualTo(TEST_NAMESPACE);
                assertThat(e.getApplicationService()).isEqualTo(TEST_APP_SERVICE);
                assertThat(e.getTimestamp()).isNotNull();
                assertThat(e.getResult()).isNull();
            });
        }

        @Test
        @DisplayName("should create AFTER event with result and metadata")
        void testCreateAfterEventWithResult() {
            Long resourceVersion = 42L;
            String message = "Resource created successfully";

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName(TEST_RESOURCE)
                    .resourceNamespace(TEST_NAMESPACE)
                    .applicationService(TEST_APP_SERVICE)
                    .result(ReconciliationEvent.Result.SUCCESS)
                    .message(message)
                    .resourceVersion(resourceVersion)
                    .build();

            assertThat(event).satisfies(e -> {
                assertThat(e.getPhase()).isEqualTo(ReconciliationEvent.Phase.AFTER);
                assertThat(e.getResult()).isEqualTo(ReconciliationEvent.Result.SUCCESS);
                assertThat(e.getMessage()).isEqualTo(message);
                assertThat(e.getResourceVersion()).isEqualTo(resourceVersion);
                assertThat(e.isSuccess()).isTrue();
                assertThat(e.isFailure()).isFalse();
            });
        }

        @Test
        @DisplayName("should create AFTER event with validation error")
        void testCreateValidationErrorEvent() {
            String reason = "ApplicationService does not exist";
            String errorDetails = "Referenced ApplicationService 'missing-service' not found";

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("VirtualCluster")
                    .resourceName(TEST_RESOURCE)
                    .resourceNamespace(TEST_NAMESPACE)
                    .applicationService(TEST_APP_SERVICE)
                    .result(ReconciliationEvent.Result.VALIDATION_ERROR)
                    .reason(reason)
                    .errorDetails(errorDetails)
                    .build();

            assertThat(event).satisfies(e -> {
                assertThat(e.getResult()).isEqualTo(ReconciliationEvent.Result.VALIDATION_ERROR);
                assertThat(e.getReason()).isEqualTo(reason);
                assertThat(e.getErrorDetails()).isEqualTo(errorDetails);
                assertThat(e.isSuccess()).isFalse();
                assertThat(e.isFailure()).isTrue();
            });
        }

        @Test
        @DisplayName("should fail when required fields are missing")
        void testRequiredFieldValidation() {
            assertThatThrownBy(() -> ReconciliationEvent.builder()
                            .phase(ReconciliationEvent.Phase.BEFORE)
                            // Missing operation
                            .resourceKind("Topic")
                            .resourceName(TEST_RESOURCE)
                            .resourceNamespace(TEST_NAMESPACE)
                            .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("operation");
        }

        @Test
        @DisplayName("should generate resource reference string")
        void testResourceReference() {
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.UPDATE)
                    .resourceKind("ServiceAccount")
                    .resourceName("orders-sa")
                    .resourceNamespace("production")
                    .build();

            assertThat(event.getResourceReference()).isEqualTo("ServiceAccount/production/orders-sa");
        }

        @Test
        @DisplayName("should format event toString with all details")
        void testEventToString() {
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.UPDATE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .result(ReconciliationEvent.Result.SUCCESS)
                    .message("Partitions updated from 12 to 24")
                    .resourceVersion(5L)
                    .build();

            String eventString = event.toString();

            assertThat(eventString)
                    .contains("AFTER")
                    .contains("UPDATE")
                    .contains("Topic/production/orders-events")
                    .contains("(owner: orders-service)")
                    .contains("SUCCESS")
                    .contains("[v5]")
                    .contains("Partitions updated from 12 to 24");
        }
    }

    @Nested
    @DisplayName("Event Publisher and Listeners")
    class EventPublisherTest {

        @Test
        @DisplayName("should publish event to single listener")
        void testPublishToSingleListener() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);
            List<ReconciliationEvent> receivedEvents = new ArrayList<>();

            publisher.addListener(receivedEvents::add);

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName(TEST_RESOURCE)
                    .resourceNamespace(TEST_NAMESPACE)
                    .build();

            publisher.publish(event);

            assertThat(receivedEvents).hasSize(1).first().isEqualTo(event);
        }

        @Test
        @DisplayName("should publish event to multiple listeners")
        void testPublishToMultipleListeners() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);

            AtomicInteger listener1Count = new AtomicInteger(0);
            AtomicInteger listener2Count = new AtomicInteger(0);
            AtomicInteger listener3Count = new AtomicInteger(0);

            publisher.addListener(e -> listener1Count.incrementAndGet());
            publisher.addListener(e -> listener2Count.incrementAndGet());
            publisher.addListener(e -> listener3Count.incrementAndGet());

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName(TEST_RESOURCE)
                    .resourceNamespace(TEST_NAMESPACE)
                    .build();

            publisher.publish(event);

            assertThat(listener1Count.get()).isEqualTo(1);
            assertThat(listener2Count.get()).isEqualTo(1);
            assertThat(listener3Count.get()).isEqualTo(1);
            assertThat(publisher.getListenerCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should handle listener exceptions gracefully")
        void testListenerExceptionHandling() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);

            List<ReconciliationEvent> successfulEvents = new ArrayList<>();

            // Listener that throws exception
            publisher.addListener(e -> {
                throw new RuntimeException("Listener error");
            });

            // Listener that works
            publisher.addListener(successfulEvents::add);

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName(TEST_RESOURCE)
                    .resourceNamespace(TEST_NAMESPACE)
                    .build();

            // Should not throw, second listener should still receive event
            assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

            assertThat(successfulEvents).hasSize(1).first().isEqualTo(event);
        }

        @Test
        @DisplayName("should remove listener")
        void testRemoveListener() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);

            AtomicInteger count = new AtomicInteger(0);
            ReconciliationEventPublisher.ReconciliationEventListener listener = e -> count.incrementAndGet();

            publisher.addListener(listener);
            assertThat(publisher.getListenerCount()).isEqualTo(1);

            publisher.removeListener(listener);
            assertThat(publisher.getListenerCount()).isEqualTo(0);

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName(TEST_RESOURCE)
                    .resourceNamespace(TEST_NAMESPACE)
                    .build();

            publisher.publish(event);

            assertThat(count.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("should clear all listeners")
        void testClearListeners() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);

            publisher.addListener(e -> {});
            publisher.addListener(e -> {});
            publisher.addListener(e -> {});

            assertThat(publisher.getListenerCount()).isEqualTo(3);

            publisher.clearListeners();

            assertThat(publisher.getListenerCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Convenience Publishing Methods")
    class ConvenienceMethodsTest {

        @Test
        @DisplayName("should publish BEFORE event using convenience method")
        void testPublishBefore() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);
            List<ReconciliationEvent> events = new ArrayList<>();
            publisher.addListener(events::add);

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .build();

            publisher.publish(event);

            assertThat(events).hasSize(1).first().satisfies(e -> {
                assertThat(e.getPhase()).isEqualTo(ReconciliationEvent.Phase.BEFORE);
                assertThat(e.getOperation()).isEqualTo(ReconciliationEvent.Operation.CREATE);
                assertThat(e.getResourceKind()).isEqualTo("Topic");
                assertThat(e.getResourceName()).isEqualTo("orders-events");
                assertThat(e.getResourceNamespace()).isEqualTo("production");
                assertThat(e.getApplicationService()).isEqualTo("orders-service");
            });
        }

        @Test
        @DisplayName("should publish success event using convenience method")
        void testPublishSuccess() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);
            List<ReconciliationEvent> events = new ArrayList<>();
            publisher.addListener(events::add);

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.UPDATE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .result(ReconciliationEvent.Result.SUCCESS)
                    .message(ReconciliationEvent.Operation.UPDATE.name() + " completed successfully")
                    .resourceVersion(5L)
                    .build();

            publisher.publish(event);

            assertThat(events).hasSize(1).first().satisfies(e -> {
                assertThat(e.getPhase()).isEqualTo(ReconciliationEvent.Phase.AFTER);
                assertThat(e.getResult()).isEqualTo(ReconciliationEvent.Result.SUCCESS);
                assertThat(e.getResourceVersion()).isEqualTo(5L);
                assertThat(e.getMessage()).contains("completed successfully");
            });
        }

        @Test
        @DisplayName("should publish failure event using convenience method")
        void testPublishFailure() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);
            List<ReconciliationEvent> events = new ArrayList<>();
            publisher.addListener(events::add);

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.DELETE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .result(ReconciliationEvent.Result.FAILURE)
                    .message("Operation failed")
                    .errorDetails("Resource not found in Kafka")
                    .build();

            publisher.publish(event);

            assertThat(events).hasSize(1).first().satisfies(e -> {
                assertThat(e.getPhase()).isEqualTo(ReconciliationEvent.Phase.AFTER);
                assertThat(e.getResult()).isEqualTo(ReconciliationEvent.Result.FAILURE);
                assertThat(e.getErrorDetails()).isEqualTo("Resource not found in Kafka");
            });
        }

        @Test
        @DisplayName("should publish validation error using convenience method")
        void testPublishValidationError() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);
            List<ReconciliationEvent> events = new ArrayList<>();
            publisher.addListener(events::add);

            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("VirtualCluster")
                    .resourceName("prod-cluster")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .result(ReconciliationEvent.Result.VALIDATION_ERROR)
                    .message("Validation failed")
                    .reason("ApplicationService 'orders-service' does not exist")
                    .build();

            publisher.publish(event);

            assertThat(events).hasSize(1).first().satisfies(e -> {
                assertThat(e.getResult()).isEqualTo(ReconciliationEvent.Result.VALIDATION_ERROR);
                assertThat(e.getReason()).contains("ApplicationService");
            });
        }
    }

    @Nested
    @DisplayName("Complete Reconciliation Lifecycle")
    class ReconciliationLifecycleTest {

        @Test
        @DisplayName("should emit BEFORE and AFTER events for successful create")
        void testSuccessfulCreateLifecycle() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);
            List<ReconciliationEvent> events = new ArrayList<>();
            publisher.addListener(events::add);

            // BEFORE event
            ReconciliationEvent event1 = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .build();

            publisher.publish(event1);

            // Simulate reconciliation work...

            // AFTER event
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .result(ReconciliationEvent.Result.SUCCESS)
                    .message(ReconciliationEvent.Operation.CREATE.name() + " completed successfully")
                    .resourceVersion(1L)
                    .build();

            publisher.publish(event);

            assertThat(events).hasSize(2).satisfies(list -> {
                // First event should be BEFORE
                assertThat(list.get(0).getPhase()).isEqualTo(ReconciliationEvent.Phase.BEFORE);
                assertThat(list.get(0).getResult()).isNull();

                // Second event should be AFTER with SUCCESS
                assertThat(list.get(1).getPhase()).isEqualTo(ReconciliationEvent.Phase.AFTER);
                assertThat(list.get(1).getResult()).isEqualTo(ReconciliationEvent.Result.SUCCESS);
                assertThat(list.get(1).getResourceVersion()).isEqualTo(1L);
            });
        }

        @Test
        @DisplayName("should emit BEFORE and AFTER events for failed create")
        void testFailedCreateLifecycle() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);
            List<ReconciliationEvent> events = new ArrayList<>();
            publisher.addListener(events::add);

            // BEFORE event
            ReconciliationEvent event1 = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .build();

            publisher.publish(event1);

            // Simulate validation failure...

            // AFTER event with validation error
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .result(ReconciliationEvent.Result.VALIDATION_ERROR)
                    .message("Validation failed")
                    .reason("ServiceAccount 'orders-sa' does not exist")
                    .build();

            publisher.publish(event);

            assertThat(events).hasSize(2).satisfies(list -> {
                assertThat(list.get(0).getPhase()).isEqualTo(ReconciliationEvent.Phase.BEFORE);
                assertThat(list.get(1).getPhase()).isEqualTo(ReconciliationEvent.Phase.AFTER);
                assertThat(list.get(1).getResult()).isEqualTo(ReconciliationEvent.Result.VALIDATION_ERROR);
                assertThat(list.get(1).isFailure()).isTrue();
            });
        }

        @Test
        @DisplayName("should track complete update lifecycle with resource version changes")
        void testUpdateLifecycleWithVersioning() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);
            List<ReconciliationEvent> events = new ArrayList<>();
            publisher.addListener(events::add);

            // BEFORE event
            ReconciliationEvent event1 = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.UPDATE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .build();

            publisher.publish(event1);

            // AFTER event with new version
            // New version after update
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.UPDATE)
                    .resourceKind("Topic")
                    .resourceName("orders-events")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .result(ReconciliationEvent.Result.SUCCESS)
                    .message(ReconciliationEvent.Operation.UPDATE.name() + " completed successfully")
                    .resourceVersion(5L)
                    .build();

            publisher.publish(event);

            assertThat(events).hasSize(2).satisfies(list -> {
                ReconciliationEvent before = list.get(0);
                ReconciliationEvent after = list.get(1);

                assertThat(before.getResourceVersion()).isNull();
                assertThat(after.getResourceVersion()).isEqualTo(5L);
                assertThat(after.isSuccess()).isTrue();
            });
        }

        @Test
        @DisplayName("should track delete lifecycle")
        void testDeleteLifecycle() {
            ReconciliationEventPublisher publisher = new ReconciliationEventPublisher(false);
            List<ReconciliationEvent> events = new ArrayList<>();
            publisher.addListener(events::add);

            // BEFORE event
            ReconciliationEvent event1 = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.BEFORE)
                    .operation(ReconciliationEvent.Operation.DELETE)
                    .resourceKind("ACL")
                    .resourceName("orders-events-rw")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .build();

            publisher.publish(event1);

            // AFTER event
            // No version after delete
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.DELETE)
                    .resourceKind("ACL")
                    .resourceName("orders-events-rw")
                    .resourceNamespace("production")
                    .applicationService("orders-service")
                    .result(ReconciliationEvent.Result.SUCCESS)
                    .message(ReconciliationEvent.Operation.DELETE.name() + " completed successfully")
                    .resourceVersion(null)
                    .build();

            publisher.publish(event);

            assertThat(events).hasSize(2).satisfies(list -> {
                assertThat(list.get(0).getOperation()).isEqualTo(ReconciliationEvent.Operation.DELETE);
                assertThat(list.get(1).getResult()).isEqualTo(ReconciliationEvent.Result.SUCCESS);
                assertThat(list.get(1).getResourceVersion()).isNull();
            });
        }
    }
}
