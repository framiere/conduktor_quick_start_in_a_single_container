package com.example.messaging.operator.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Publisher for reconciliation events following observer pattern.
 * Allows multiple listeners to observe CRD reconciliation lifecycle events.
 */
public class ReconciliationEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(ReconciliationEventPublisher.class);

    private final List<ReconciliationEventListener> listeners = new CopyOnWriteArrayList<>();
    private final boolean auditLoggingEnabled;

    public ReconciliationEventPublisher() {
        this(true);
    }

    public ReconciliationEventPublisher(boolean auditLoggingEnabled) {
        this.auditLoggingEnabled = auditLoggingEnabled;
    }

    /**
     * Register a listener for reconciliation events
     */
    public void addListener(ReconciliationEventListener listener) {
        listeners.add(listener);
        logger.info("Registered reconciliation event listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Remove a listener
     */
    public void removeListener(ReconciliationEventListener listener) {
        listeners.remove(listener);
        logger.info("Removed reconciliation event listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Publish a reconciliation event to all registered listeners
     */
    public void publish(ReconciliationEvent event) {
        if (auditLoggingEnabled) {
            auditLog(event);
        }

        for (ReconciliationEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                logger.error("Error in reconciliation event listener {}: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Create and publish BEFORE event
     */
    public void publishBefore(ReconciliationEvent.Operation operation,
                               String resourceKind,
                               String resourceName,
                               String resourceNamespace,
                               String applicationService) {
        ReconciliationEvent event = ReconciliationEvent.builder()
                .phase(ReconciliationEvent.Phase.BEFORE)
                .operation(operation)
                .resourceKind(resourceKind)
                .resourceName(resourceName)
                .resourceNamespace(resourceNamespace)
                .applicationService(applicationService)
                .build();

        publish(event);
    }

    /**
     * Create and publish AFTER event with result
     */
    public void publishAfter(ReconciliationEvent.Operation operation,
                             String resourceKind,
                             String resourceName,
                             String resourceNamespace,
                             String applicationService,
                             ReconciliationEvent.Result result,
                             String message,
                             Long resourceVersion) {
        ReconciliationEvent event = ReconciliationEvent.builder()
                .phase(ReconciliationEvent.Phase.AFTER)
                .operation(operation)
                .resourceKind(resourceKind)
                .resourceName(resourceName)
                .resourceNamespace(resourceNamespace)
                .applicationService(applicationService)
                .result(result)
                .message(message)
                .resourceVersion(resourceVersion)
                .build();

        publish(event);
    }

    /**
     * Create and publish AFTER event for success
     */
    public void publishSuccess(ReconciliationEvent.Operation operation,
                                String resourceKind,
                                String resourceName,
                                String resourceNamespace,
                                String applicationService,
                                Long resourceVersion) {
        publishAfter(operation, resourceKind, resourceName, resourceNamespace, applicationService,
                ReconciliationEvent.Result.SUCCESS,
                operation.name() + " completed successfully",
                resourceVersion);
    }

    /**
     * Create and publish AFTER event for failure
     */
    public void publishFailure(ReconciliationEvent.Operation operation,
                                String resourceKind,
                                String resourceName,
                                String resourceNamespace,
                                String applicationService,
                                String errorMessage) {
        ReconciliationEvent event = ReconciliationEvent.builder()
                .phase(ReconciliationEvent.Phase.AFTER)
                .operation(operation)
                .resourceKind(resourceKind)
                .resourceName(resourceName)
                .resourceNamespace(resourceNamespace)
                .applicationService(applicationService)
                .result(ReconciliationEvent.Result.FAILURE)
                .message("Operation failed")
                .errorDetails(errorMessage)
                .build();

        publish(event);
    }

    /**
     * Create and publish AFTER event for validation error
     */
    public void publishValidationError(ReconciliationEvent.Operation operation,
                                        String resourceKind,
                                        String resourceName,
                                        String resourceNamespace,
                                        String applicationService,
                                        String validationMessage) {
        ReconciliationEvent event = ReconciliationEvent.builder()
                .phase(ReconciliationEvent.Phase.AFTER)
                .operation(operation)
                .resourceKind(resourceKind)
                .resourceName(resourceName)
                .resourceNamespace(resourceNamespace)
                .applicationService(applicationService)
                .result(ReconciliationEvent.Result.VALIDATION_ERROR)
                .message("Validation failed")
                .reason(validationMessage)
                .build();

        publish(event);
    }

    /**
     * Get the number of registered listeners
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * Clear all listeners
     */
    public void clearListeners() {
        listeners.clear();
        logger.info("Cleared all reconciliation event listeners");
    }

    /**
     * Audit log for compliance and debugging
     */
    private void auditLog(ReconciliationEvent event) {
        if (event.getPhase() == ReconciliationEvent.Phase.BEFORE) {
            logger.info("RECONCILIATION_START: {} {} {}/{}",
                    event.getOperation(),
                    event.getResourceKind(),
                    event.getResourceNamespace(),
                    event.getResourceName());
        } else {
            String resultIndicator = event.isSuccess() ? "SUCCESS" : "FAILED";
            logger.info("RECONCILIATION_END: {} {} {}/{} - {} {}",
                    event.getOperation(),
                    event.getResourceKind(),
                    event.getResourceNamespace(),
                    event.getResourceName(),
                    resultIndicator,
                    event.getMessage() != null ? event.getMessage() : "");
        }
    }

    /**
     * Functional interface for event listeners
     */
    @FunctionalInterface
    public interface ReconciliationEventListener {
        void onEvent(ReconciliationEvent event);
    }
}
