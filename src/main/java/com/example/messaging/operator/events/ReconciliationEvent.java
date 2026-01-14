package com.example.messaging.operator.events;

import java.time.Instant;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

/**
 * Event emitted during CRD reconciliation operations.
 * Follows Kubernetes Event best practices for operator observability.
 */
@Getter
@Builder
@EqualsAndHashCode
public class ReconciliationEvent {

    public enum Phase {
        BEFORE,
        AFTER
    }

    public enum Operation {
        CREATE,
        UPDATE,
        DELETE
    }

    public enum Result {
        SUCCESS,
        FAILURE,
        VALIDATION_ERROR,
        CONFLICT,
        NOT_FOUND
    }

    @NonNull private final Phase phase;

    @NonNull private final Operation operation;

    @NonNull private final String resourceKind;

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
        return String.format("%s/%s/%s", resourceKind, resourceNamespace, resourceName);
    }

    public boolean isSuccess() {
        return result == Result.SUCCESS;
    }

    public boolean isFailure() {
        return result != null && result != Result.SUCCESS;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(phase).append(" ");
        sb.append(operation).append(" ");
        sb.append(getResourceReference());

        if (applicationService != null) {
            sb.append(" (owner: ").append(applicationService).append(")");
        }

        if (result != null) {
            sb.append(" - ").append(result);
        }

        if (resourceVersion != null) {
            sb.append(" [v").append(resourceVersion).append("]");
        }

        if (message != null) {
            sb.append(": ").append(message);
        }

        if (reason != null) {
            sb.append(" (").append(reason).append(")");
        }

        if (errorDetails != null) {
            sb.append(" | ").append(errorDetails);
        }

        return sb.toString();
    }
}
