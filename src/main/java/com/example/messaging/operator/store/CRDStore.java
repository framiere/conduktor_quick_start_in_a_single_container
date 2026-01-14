package com.example.messaging.operator.store;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.events.ReconciliationEvent;
import com.example.messaging.operator.events.ReconciliationEventPublisher;
import com.example.messaging.operator.validation.OwnershipValidator;
import com.example.messaging.operator.validation.ValidationResult;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory CRD store simulating etcd behavior with event publishing and ownership enforcement.
 * Provides CRUD operations for Custom Resource Definitions with:
 * - Resource versioning for optimistic concurrency control
 * - UID generation for resource identity
 * - Event publishing for observability
 * - Thread-safe concurrent access
 * - Strict ownership validation and enforcement
 */
public class CRDStore {
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong resourceVersionCounter = new AtomicLong(1);
    @Getter
    private final ReconciliationEventPublisher eventPublisher = new ReconciliationEventPublisher(true);
    private final OwnershipValidator ownershipValidator = new OwnershipValidator(this);


    private String getKey(String kind, String namespace, String name) {
        return String.format("%s/%s/%s", kind, namespace, name);
    }

    public <T> T create(String kind, String namespace, T resource) {
        String name = getName(resource);
        String appService = getApplicationServiceRef(resource);

        // Publish BEFORE event
        ReconciliationEvent event1 = ReconciliationEvent.builder()
                .phase(ReconciliationEvent.Phase.BEFORE)
                .operation(ReconciliationEvent.Operation.CREATE)
                .resourceKind(kind)
                .resourceName(name)
                .resourceNamespace(namespace)
                .applicationService(appService)
                .build();

        eventPublisher.publish(event1);

        try {
            String key = getKey(kind, namespace, name);
            if (store.containsKey(key)) {
                String errorMessage = "Resource already exists: " + key;
                ReconciliationEvent event = ReconciliationEvent.builder()
                        .phase(ReconciliationEvent.Phase.AFTER)
                        .operation(ReconciliationEvent.Operation.CREATE)
                        .resourceKind(kind)
                        .resourceName(name)
                        .resourceNamespace(namespace)
                        .applicationService(appService)
                        .result(ReconciliationEvent.Result.FAILURE)
                        .message("Operation failed")
                        .errorDetails(errorMessage)
                        .build();

                eventPublisher.publish(event);
                throw new IllegalStateException(errorMessage);
            }

            // OWNERSHIP ENFORCEMENT: Validate ownership chains and references
            if (!(resource instanceof ApplicationService)) {
                ValidationResult validationResult = ownershipValidator.validateCreate(resource, namespace);
                if (!validationResult.isValid()) {
                    String validationMessage = validationResult.getMessage();
                    ReconciliationEvent event = ReconciliationEvent.builder()
                            .phase(ReconciliationEvent.Phase.AFTER)
                            .operation(ReconciliationEvent.Operation.CREATE)
                            .resourceKind(kind)
                            .resourceName(name)
                            .resourceNamespace(namespace)
                            .applicationService(appService)
                            .result(ReconciliationEvent.Result.VALIDATION_ERROR)
                            .message("Validation failed")
                            .reason(validationMessage)
                            .build();

                    eventPublisher.publish(event);
                    throw new SecurityException("Ownership validation failed: " + validationResult.getMessage());
                }
            }

            long version = resourceVersionCounter.getAndIncrement();
            setResourceVersion(resource, String.valueOf(version));
            setUid(resource, UUID.randomUUID().toString());


            store.put(key, Map.of(
                    "resource", resource,
                    "timestamp", System.currentTimeMillis()));

            // Publish AFTER SUCCESS event
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind(kind)
                    .resourceName(name)
                    .resourceNamespace(namespace)
                    .applicationService(appService)
                    .result(ReconciliationEvent.Result.SUCCESS)
                    .message(ReconciliationEvent.Operation.CREATE.name() + " completed successfully")
                    .resourceVersion(version)
                    .build();

            eventPublisher.publish(event);

            return resource;
        } catch (IllegalStateException | SecurityException e) {
            // Already published failure/validation event, just rethrow
            throw e;
        } catch (Exception e) {
            // Unexpected exception, publish failure and rethrow
            String errorMessage = e.getMessage();
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.CREATE)
                    .resourceKind(kind)
                    .resourceName(name)
                    .resourceNamespace(namespace)
                    .applicationService(appService)
                    .result(ReconciliationEvent.Result.FAILURE)
                    .message("Operation failed")
                    .errorDetails(errorMessage)
                    .build();

            eventPublisher.publish(event);
            throw e;
        }
    }

    public <T> T update(String kind, String namespace, String name, T resource) {
        String appService = getApplicationServiceRef(resource);

        ReconciliationEvent event1 = ReconciliationEvent.builder()
                .phase(ReconciliationEvent.Phase.BEFORE)
                .operation(ReconciliationEvent.Operation.UPDATE)
                .resourceKind(kind)
                .resourceName(name)
                .resourceNamespace(namespace)
                .applicationService(appService)
                .build();

        eventPublisher.publish(event1);

        try {
            String key = getKey(kind, namespace, name);
            if (!store.containsKey(key)) {
                String errorMessage = "Resource not found: " + key;
                ReconciliationEvent event = ReconciliationEvent.builder()
                        .phase(ReconciliationEvent.Phase.AFTER)
                        .operation(ReconciliationEvent.Operation.UPDATE)
                        .resourceKind(kind)
                        .resourceName(name)
                        .resourceNamespace(namespace)
                        .applicationService(appService)
                        .result(ReconciliationEvent.Result.FAILURE)
                        .message("Operation failed")
                        .errorDetails(errorMessage)
                        .build();

                eventPublisher.publish(event);
                throw new IllegalStateException(errorMessage);
            }

            // OWNERSHIP ENFORCEMENT: Validate immutable ownership
            Object existingResource = get(kind, namespace, name);
            if (!(resource instanceof ApplicationService)) {
                ValidationResult validationResult = ownershipValidator.validateUpdate(existingResource, resource);
                if (!validationResult.isValid()) {
                    String validationMessage = validationResult.getMessage();
                    ReconciliationEvent event = ReconciliationEvent.builder()
                            .phase(ReconciliationEvent.Phase.AFTER)
                            .operation(ReconciliationEvent.Operation.UPDATE)
                            .resourceKind(kind)
                            .resourceName(name)
                            .resourceNamespace(namespace)
                            .applicationService(appService)
                            .result(ReconciliationEvent.Result.VALIDATION_ERROR)
                            .message("Validation failed")
                            .reason(validationMessage)
                            .build();

                    eventPublisher.publish(event);
                    throw new SecurityException("Ownership validation failed: " + validationResult.getMessage());
                }
            }

            long version = resourceVersionCounter.getAndIncrement();
            setResourceVersion(resource, String.valueOf(version));

            store.put(key, Map.of(
                    "resource", resource,
                    "timestamp", System.currentTimeMillis()));

            // Publish AFTER SUCCESS event
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.UPDATE)
                    .resourceKind(kind)
                    .resourceName(name)
                    .resourceNamespace(namespace)
                    .applicationService(appService)
                    .result(ReconciliationEvent.Result.SUCCESS)
                    .message(ReconciliationEvent.Operation.UPDATE.name() + " completed successfully")
                    .resourceVersion(version)
                    .build();

            eventPublisher.publish(event);

            return resource;
        } catch (IllegalStateException | SecurityException e) {
            // Already published failure/validation event, just rethrow
            throw e;
        } catch (Exception e) {
            // Unexpected exception, publish failure and rethrow
            String errorMessage = e.getMessage();
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.UPDATE)
                    .resourceKind(kind)
                    .resourceName(name)
                    .resourceNamespace(namespace)
                    .applicationService(appService)
                    .result(ReconciliationEvent.Result.FAILURE)
                    .message("Operation failed")
                    .errorDetails(errorMessage)
                    .build();

            eventPublisher.publish(event);
            throw e;
        }
    }

    /**
     * Get a resource by name
     *
     * @param kind      The resource kind
     * @param namespace The namespace
     * @param name      The resource name
     * @return The resource, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String kind, String namespace, String name) {
        String key = getKey(kind, namespace, name);
        Map<String, Object> entry = store.get(key);
        return entry != null ? (T) entry.get("resource") : null;
    }

    /**
     * List all resources of a kind in a namespace
     *
     * @param kind      The resource kind
     * @param namespace The namespace
     * @return List of resources
     */
    public <T> List<T> list(String kind, String namespace) {
        return store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(kind + "/" + namespace + "/"))
                .map(e -> (T) e.getValue().get("resource"))
                .collect(Collectors.toList());
    }

    /**
     * Delete a resource
     *
     * @param kind      The resource kind
     * @param namespace The namespace
     * @param name      The resource name
     * @return true if deleted, false if not found
     */
    public boolean delete(String kind, String namespace, String name) {
        return delete(kind, namespace, name, null);
    }

    /**
     * Delete a resource with requesting owner context
     *
     * @param kind                 The resource kind
     * @param namespace            The namespace
     * @param name                 The resource name
     * @param requestingAppService The requesting application service
     * @return true if deleted, false if not found
     */
    public boolean delete(String kind, String namespace, String name, String requestingAppService) {
        // Get resource before deletion to extract appService
        Object existingResource = get(kind, namespace, name);
        String appService = existingResource != null ? getApplicationServiceRef(existingResource) : requestingAppService;

        // Publish BEFORE event
        ReconciliationEvent event1 = ReconciliationEvent.builder()
                .phase(ReconciliationEvent.Phase.BEFORE)
                .operation(ReconciliationEvent.Operation.DELETE)
                .resourceKind(kind)
                .resourceName(name)
                .resourceNamespace(namespace)
                .applicationService(appService)
                .build();

        eventPublisher.publish(event1);

        try {
            // OWNERSHIP ENFORCEMENT: Validate only owner can delete
            if (existingResource != null && requestingAppService != null && !(existingResource instanceof ApplicationService)) {
                ValidationResult validationResult = ownershipValidator.validateDelete(existingResource, requestingAppService);
                if (!validationResult.isValid()) {
                    String validationMessage = validationResult.getMessage();
                    ReconciliationEvent event = ReconciliationEvent.builder()
                            .phase(ReconciliationEvent.Phase.AFTER)
                            .operation(ReconciliationEvent.Operation.DELETE)
                            .resourceKind(kind)
                            .resourceName(name)
                            .resourceNamespace(namespace)
                            .applicationService(appService)
                            .result(ReconciliationEvent.Result.VALIDATION_ERROR)
                            .message("Validation failed")
                            .reason(validationMessage)
                            .build();

                    eventPublisher.publish(event);
                    throw new SecurityException("Ownership validation failed: " + validationResult.getMessage());
                }
            }

            String key = getKey(kind, namespace, name);
            boolean deleted = store.remove(key) != null;

            if (deleted) {
                // Publish AFTER SUCCESS event
                // No version after delete
                ReconciliationEvent event = ReconciliationEvent.builder()
                        .phase(ReconciliationEvent.Phase.AFTER)
                        .operation(ReconciliationEvent.Operation.DELETE)
                        .resourceKind(kind)
                        .resourceName(name)
                        .resourceNamespace(namespace)
                        .applicationService(appService)
                        .result(ReconciliationEvent.Result.SUCCESS)
                        .message(ReconciliationEvent.Operation.DELETE.name() + " completed successfully")
                        .resourceVersion(null)
                        .build();

                eventPublisher.publish(event);
            } else {
                ReconciliationEvent event = ReconciliationEvent.builder()
                        .phase(ReconciliationEvent.Phase.AFTER)
                        .operation(ReconciliationEvent.Operation.DELETE)
                        .resourceKind(kind)
                        .resourceName(name)
                        .resourceNamespace(namespace)
                        .applicationService(appService)
                        .result(ReconciliationEvent.Result.NOT_FOUND)
                        .message("Resource not found")
                        .resourceVersion(null)
                        .build();

                eventPublisher.publish(event);
            }

            return deleted;
        } catch (SecurityException e) {
            // Already published validation event, just rethrow
            throw e;
        } catch (Exception e) {
            // Unexpected exception, publish failure and rethrow
            ReconciliationEvent event = ReconciliationEvent.builder()
                    .phase(ReconciliationEvent.Phase.AFTER)
                    .operation(ReconciliationEvent.Operation.DELETE)
                    .resourceKind(kind)
                    .resourceName(name)
                    .resourceNamespace(namespace)
                    .applicationService(appService)
                    .result(ReconciliationEvent.Result.FAILURE)
                    .message("Operation failed")
                    .errorDetails(e.getMessage())
                    .build();

            eventPublisher.publish(event);
            throw e;
        }
    }

    /**
     * Add a listener for reconciliation events
     */
    public void addReconciliationListener(ReconciliationEventPublisher.ReconciliationEventListener listener) {
        eventPublisher.addListener(listener);
    }

    /**
     * Remove a reconciliation event listener
     */
    public void removeReconciliationListener(ReconciliationEventPublisher.ReconciliationEventListener listener) {
        eventPublisher.removeListener(listener);
    }

    /**
     * Clear all resources from the store
     */
    public void clear() {
        store.clear();
        resourceVersionCounter.set(1);
    }

    private String getName(Object resource) {
        return switch (resource) {
            case ApplicationService r -> r.getMetadata().getName();
            case VirtualCluster r -> r.getMetadata().getName();
            case ServiceAccount r -> r.getMetadata().getName();
            case Topic r -> r.getMetadata().getName();
            case ConsumerGroup r -> r.getMetadata().getName();
            case ACL r -> r.getMetadata().getName();
            default -> throw new IllegalArgumentException("Unknown resource type: " + resource.getClass());
        };
    }

    private String getApplicationServiceRef(Object resource) {
        return switch (resource) {
            case ApplicationService r -> r.getSpec().getName();
            case VirtualCluster r -> r.getSpec().getApplicationServiceRef();
            case ServiceAccount r -> r.getSpec().getApplicationServiceRef();
            case Topic r -> r.getSpec().getApplicationServiceRef();
            case ConsumerGroup r -> r.getSpec().getApplicationServiceRef();
            case ACL r -> r.getSpec().getApplicationServiceRef();
            default -> null;
        };
    }

    private void setResourceVersion(Object resource, String version) {
        switch (resource) {
            case ApplicationService r -> r.getMetadata().setResourceVersion(version);
            case VirtualCluster r -> r.getMetadata().setResourceVersion(version);
            case ServiceAccount r -> r.getMetadata().setResourceVersion(version);
            case Topic r -> r.getMetadata().setResourceVersion(version);
            case ConsumerGroup r -> r.getMetadata().setResourceVersion(version);
            case ACL r -> r.getMetadata().setResourceVersion(version);
            default -> throw new IllegalArgumentException("Unknown resource type: " + resource.getClass());
        }
    }

    private void setUid(Object resource, String uid) {
        switch (resource) {
            case ApplicationService r -> r.getMetadata().setUid(uid);
            case VirtualCluster r -> r.getMetadata().setUid(uid);
            case ServiceAccount r -> r.getMetadata().setUid(uid);
            case Topic r -> r.getMetadata().setUid(uid);
            case ConsumerGroup r -> r.getMetadata().setUid(uid);
            case ACL r -> r.getMetadata().setUid(uid);
            default -> throw new IllegalArgumentException("Unknown resource type: " + resource.getClass());
        }
    }
}
