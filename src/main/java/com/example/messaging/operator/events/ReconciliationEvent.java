package com.example.messaging.operator.events;

import com.example.messaging.operator.store.CRDKind;
import java.time.Instant;

import lombok.*;

/**
 * Event emitted during CRD reconciliation operations. Follows Kubernetes Event best practices for operator observability.
 */
@Getter
@Builder
@EqualsAndHashCode
public class ReconciliationEvent {

    public enum Phase {
        BEFORE, AFTER
    }

    public enum Operation {
        CREATE, UPDATE, DELETE
    }

    public enum Result {
        SUCCESS, FAILURE, VALIDATION_ERROR, CONFLICT, NOT_FOUND
    }

    @NonNull private final Phase phase;
    @NonNull private final Operation operation;
    @NonNull private final CRDKind resourceKind;
    @NonNull private final String resourceName;
    @NonNull private final String resourceNamespace;
    private final String applicationService;
    @Builder.Default
    private final Instant timestamp = Instant.now();

    private Result result;
    private String message;
    private String reason;
    private Long resourceVersion;
    private String errorDetails;

    public String getResourceReference() {
        return resourceKind.getValue() + "/" + resourceNamespace + "/" + resourceName;
    }

    public boolean isSuccess() {
        return result == Result.SUCCESS;
    }

    public boolean isFailure() {
        return result != null && result != Result.SUCCESS;
    }

    @Override
    public String toString() {
        String base = phase + " " + operation + " " + getResourceReference();
        if (applicationService != null) {
            base += " (owner: " + applicationService + ")";
        }
        if (result != null) {
            base += " - " + result;
        }
        if (resourceVersion != null) {
            base += " [v" + resourceVersion + "]";
        }
        if (message != null) {
            base += ": " + message;
        }
        return base;
    }

}
