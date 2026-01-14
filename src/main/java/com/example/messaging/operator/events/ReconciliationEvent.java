package com.example.messaging.operator.events;

import java.time.Instant;
import java.util.Objects;

/**
 * Event emitted during CRD reconciliation operations.
 * Follows Kubernetes Event best practices for operator observability.
 */
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

    private final Phase phase;
    private final Operation operation;
    private final String resourceKind;
    private final String resourceName;
    private final String resourceNamespace;
    private final String applicationService;
    private final Instant timestamp;
    private Result result;
    private String message;
    private String reason;
    private Long resourceVersion;
    private String errorDetails;

    private ReconciliationEvent(Builder builder) {
        this.phase = builder.phase;
        this.operation = builder.operation;
        this.resourceKind = builder.resourceKind;
        this.resourceName = builder.resourceName;
        this.resourceNamespace = builder.resourceNamespace;
        this.applicationService = builder.applicationService;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.result = builder.result;
        this.message = builder.message;
        this.reason = builder.reason;
        this.resourceVersion = builder.resourceVersion;
        this.errorDetails = builder.errorDetails;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public Phase getPhase() {
        return phase;
    }

    public Operation getOperation() {
        return operation;
    }

    public String getResourceKind() {
        return resourceKind;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getResourceNamespace() {
        return resourceNamespace;
    }

    public String getApplicationService() {
        return applicationService;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Result getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }

    public String getReason() {
        return reason;
    }

    public Long getResourceVersion() {
        return resourceVersion;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

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
        sb.append("[").append(timestamp).append("] ");
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

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReconciliationEvent that = (ReconciliationEvent) o;
        return phase == that.phase &&
                operation == that.operation &&
                Objects.equals(resourceKind, that.resourceKind) &&
                Objects.equals(resourceName, that.resourceName) &&
                Objects.equals(resourceNamespace, that.resourceNamespace) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phase, operation, resourceKind, resourceName, resourceNamespace, timestamp);
    }

    public static class Builder {
        private Phase phase;
        private Operation operation;
        private String resourceKind;
        private String resourceName;
        private String resourceNamespace;
        private String applicationService;
        private Instant timestamp;
        private Result result;
        private String message;
        private String reason;
        private Long resourceVersion;
        private String errorDetails;

        public Builder phase(Phase phase) {
            this.phase = phase;
            return this;
        }

        public Builder operation(Operation operation) {
            this.operation = operation;
            return this;
        }

        public Builder resourceKind(String resourceKind) {
            this.resourceKind = resourceKind;
            return this;
        }

        public Builder resourceName(String resourceName) {
            this.resourceName = resourceName;
            return this;
        }

        public Builder resourceNamespace(String resourceNamespace) {
            this.resourceNamespace = resourceNamespace;
            return this;
        }

        public Builder applicationService(String applicationService) {
            this.applicationService = applicationService;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder result(Result result) {
            this.result = result;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder resourceVersion(Long resourceVersion) {
            this.resourceVersion = resourceVersion;
            return this;
        }

        public Builder errorDetails(String errorDetails) {
            this.errorDetails = errorDetails;
            return this;
        }

        public ReconciliationEvent build() {
            Objects.requireNonNull(phase, "phase is required");
            Objects.requireNonNull(operation, "operation is required");
            Objects.requireNonNull(resourceKind, "resourceKind is required");
            Objects.requireNonNull(resourceName, "resourceName is required");
            Objects.requireNonNull(resourceNamespace, "resourceNamespace is required");
            return new ReconciliationEvent(this);
        }
    }
}
