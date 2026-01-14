package com.example.messaging.operator.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        logger.info(
                "Registered reconciliation event listener: {}",
                listener.getClass().getSimpleName());
    }

    /**
     * Remove a listener
     */
    public void removeListener(ReconciliationEventListener listener) {
        listeners.remove(listener);
        logger.info(
                "Removed reconciliation event listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Publish a reconciliation event to all registered listeners
     */
    public void publish(ReconciliationEvent event) {
        if (auditLoggingEnabled) {
            if (event.getPhase() == ReconciliationEvent.Phase.BEFORE) {
                logger.info(
                        "RECONCILIATION_START: {} {} {}/{}",
                        event.getOperation(),
                        event.getResourceKind(),
                        event.getResourceNamespace(),
                        event.getResourceName());
            } else {
                String resultIndicator = event.isSuccess() ? "SUCCESS" : "FAILED";
                logger.info(
                        "RECONCILIATION_END: {} {} {}/{} - {} {}",
                        event.getOperation(),
                        event.getResourceKind(),
                        event.getResourceNamespace(),
                        event.getResourceName(),
                        resultIndicator,
                        event.getMessage() != null ? event.getMessage() : "");
            }
        }

        for (ReconciliationEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                logger.error(
                        "Error in reconciliation event listener {}: {}",
                        listener.getClass().getSimpleName(),
                        e.getMessage(),
                        e);
            }
        }
    }

    public int getListenerCount() {
        return listeners.size();
    }

    public void clearListeners() {
        listeners.clear();
        logger.info("Cleared all reconciliation event listeners");
    }

    /**
     * Functional interface for event listeners
     */
    @FunctionalInterface
    public interface ReconciliationEventListener {
        void onEvent(ReconciliationEvent event);
    }
}
