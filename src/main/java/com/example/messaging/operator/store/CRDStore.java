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

    /**
     * Create a new resource
     *
     * @param kind      The resource kind (e.g., "Topic", "ServiceAccount")
     * @param namespace The namespace
     * @param resource  The resource to create
     * @return The created resource with server-assigned metadata
     * @throws IllegalStateException if resource already exists
     */
    public <T> T create(String kind, String namespace, T resource) {
        String name = getName(resource);
        String appService = getApplicationServiceRef(resource);

        // Publish BEFORE event
        eventPublisher.publishBefore(
                ReconciliationEvent.Operation.CREATE,
                kind,
                name,
                namespace,
                appService
        );

        try {
            String key = getKey(kind, namespace, name);
            if (store.containsKey(key)) {
                String errorMessage = "Resource already exists: " + key;
                eventPublisher.publishFailure(
                        ReconciliationEvent.Operation.CREATE,
                        kind,
                        name,
                        namespace,
                        appService,
                        errorMessage
                );
                throw new IllegalStateException(errorMessage);
            }

            // OWNERSHIP ENFORCEMENT: Validate ownership chains and references
            if (!(resource instanceof ApplicationService)) {
                ValidationResult validationResult = ownershipValidator.validateCreate(resource, namespace);
                if (!validationResult.isValid()) {
                    eventPublisher.publishValidationError(
                            ReconciliationEvent.Operation.CREATE,
                            kind,
                            name,
                            namespace,
                            appService,
                            validationResult.getMessage()
                    );
                    throw new SecurityException("Ownership validation failed: " + validationResult.getMessage());
                }
            }

            long version = resourceVersionCounter.getAndIncrement();
            setResourceVersion(resource, String.valueOf(version));
            setUid(resource, UUID.randomUUID().toString());

            Map<String, Object> entry = new HashMap<>();
            entry.put("resource", resource);
            entry.put("timestamp", System.currentTimeMillis());
            store.put(key, entry);

            // Publish AFTER SUCCESS event
            eventPublisher.publishSuccess(
                    ReconciliationEvent.Operation.CREATE,
                    kind,
                    name,
                    namespace,
                    appService,
                    version
            );

            return resource;
        } catch (IllegalStateException | SecurityException e) {
            // Already published failure/validation event, just rethrow
            throw e;
        } catch (Exception e) {
            // Unexpected exception, publish failure and rethrow
            eventPublisher.publishFailure(
                    ReconciliationEvent.Operation.CREATE,
                    kind,
                    name,
                    namespace,
                    appService,
                    e.getMessage()
            );
            throw e;
        }
    }

    /**
     * Update an existing resource
     *
     * @param kind      The resource kind
     * @param namespace The namespace
     * @param name      The resource name
     * @param resource  The updated resource
     * @return The updated resource with incremented version
     * @throws IllegalStateException if resource not found
     */
    public <T> T update(String kind, String namespace, String name, T resource) {
        String appService = getApplicationServiceRef(resource);

        // Publish BEFORE event
        eventPublisher.publishBefore(
                ReconciliationEvent.Operation.UPDATE,
                kind,
                name,
                namespace,
                appService
        );

        try {
            String key = getKey(kind, namespace, name);
            if (!store.containsKey(key)) {
                String errorMessage = "Resource not found: " + key;
                eventPublisher.publishFailure(
                        ReconciliationEvent.Operation.UPDATE,
                        kind,
                        name,
                        namespace,
                        appService,
                        errorMessage
                );
                throw new IllegalStateException(errorMessage);
            }

            // OWNERSHIP ENFORCEMENT: Validate immutable ownership
            Object existingResource = get(kind, namespace, name);
            if (!(resource instanceof ApplicationService)) {
                ValidationResult validationResult = ownershipValidator.validateUpdate(existingResource, resource);
                if (!validationResult.isValid()) {
                    eventPublisher.publishValidationError(
                            ReconciliationEvent.Operation.UPDATE,
                            kind,
                            name,
                            namespace,
                            appService,
                            validationResult.getMessage()
                    );
                    throw new SecurityException("Ownership validation failed: " + validationResult.getMessage());
                }
            }

            long version = resourceVersionCounter.getAndIncrement();
            setResourceVersion(resource, String.valueOf(version));

            Map<String, Object> entry = new HashMap<>();
            entry.put("resource", resource);
            entry.put("timestamp", System.currentTimeMillis());
            store.put(key, entry);

            // Publish AFTER SUCCESS event
            eventPublisher.publishSuccess(
                    ReconciliationEvent.Operation.UPDATE,
                    kind,
                    name,
                    namespace,
                    appService,
                    version
            );

            return resource;
        } catch (IllegalStateException | SecurityException e) {
            // Already published failure/validation event, just rethrow
            throw e;
        } catch (Exception e) {
            // Unexpected exception, publish failure and rethrow
            eventPublisher.publishFailure(
                    ReconciliationEvent.Operation.UPDATE,
                    kind,
                    name,
                    namespace,
                    appService,
                    e.getMessage()
            );
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
        eventPublisher.publishBefore(
                ReconciliationEvent.Operation.DELETE,
                kind,
                name,
                namespace,
                appService
        );

        try {
            // OWNERSHIP ENFORCEMENT: Validate only owner can delete
            if (existingResource != null && requestingAppService != null && !(existingResource instanceof ApplicationService)) {
                ValidationResult validationResult = ownershipValidator.validateDelete(existingResource, requestingAppService);
                if (!validationResult.isValid()) {
                    eventPublisher.publishValidationError(
                            ReconciliationEvent.Operation.DELETE,
                            kind,
                            name,
                            namespace,
                            appService,
                            validationResult.getMessage()
                    );
                    throw new SecurityException("Ownership validation failed: " + validationResult.getMessage());
                }
            }

            String key = getKey(kind, namespace, name);
            boolean deleted = store.remove(key) != null;

            if (deleted) {
                // Publish AFTER SUCCESS event
                eventPublisher.publishSuccess(
                        ReconciliationEvent.Operation.DELETE,
                        kind,
                        name,
                        namespace,
                        appService,
                        null  // No version after delete
                );
            } else {
                eventPublisher.publishAfter(
                        ReconciliationEvent.Operation.DELETE,
                        kind,
                        name,
                        namespace,
                        appService,
                        ReconciliationEvent.Result.NOT_FOUND,
                        "Resource not found",
                        null
                );
            }

            return deleted;
        } catch (SecurityException e) {
            // Already published validation event, just rethrow
            throw e;
        } catch (Exception e) {
            // Unexpected exception, publish failure and rethrow
            eventPublisher.publishFailure(
                    ReconciliationEvent.Operation.DELETE,
                    kind,
                    name,
                    namespace,
                    appService,
                    e.getMessage()
            );
            throw e;
        }
    }

    /**
     * Clear all resources from the store
     */
    public void clear() {
        store.clear();
        resourceVersionCounter.set(1);
    }

    private String getName(Object resource) {
        if (resource instanceof ApplicationService) {
            return ((ApplicationService) resource).getMetadata().getName();
        } else if (resource instanceof VirtualCluster) {
            return ((VirtualCluster) resource).getMetadata().getName();
        } else if (resource instanceof ServiceAccount) {
            return ((ServiceAccount) resource).getMetadata().getName();
        } else if (resource instanceof Topic) {
            return ((Topic) resource).getMetadata().getName();
        } else if (resource instanceof ConsumerGroup) {
            return ((ConsumerGroup) resource).getMetadata().getName();
        } else if (resource instanceof ACL) {
            return ((ACL) resource).getMetadata().getName();
        }
        throw new IllegalArgumentException("Unknown resource type: " + resource.getClass());
    }

    private String getApplicationServiceRef(Object resource) {
        if (resource instanceof ApplicationService) {
            return ((ApplicationService) resource).getSpec().getName();
        } else if (resource instanceof VirtualCluster) {
            return ((VirtualCluster) resource).getSpec().getApplicationServiceRef();
        } else if (resource instanceof ServiceAccount) {
            return ((ServiceAccount) resource).getSpec().getApplicationServiceRef();
        } else if (resource instanceof Topic) {
            return ((Topic) resource).getSpec().getApplicationServiceRef();
        } else if (resource instanceof ConsumerGroup) {
            return ((ConsumerGroup) resource).getSpec().getApplicationServiceRef();
        } else if (resource instanceof ACL) {
            return ((ACL) resource).getSpec().getApplicationServiceRef();
        }
        return null;
    }

    private void setResourceVersion(Object resource, String version) {
        if (resource instanceof ApplicationService) {
            ((ApplicationService) resource).getMetadata().setResourceVersion(version);
        } else if (resource instanceof VirtualCluster) {
            ((VirtualCluster) resource).getMetadata().setResourceVersion(version);
        } else if (resource instanceof ServiceAccount) {
            ((ServiceAccount) resource).getMetadata().setResourceVersion(version);
        } else if (resource instanceof Topic) {
            ((Topic) resource).getMetadata().setResourceVersion(version);
        } else if (resource instanceof ConsumerGroup) {
            ((ConsumerGroup) resource).getMetadata().setResourceVersion(version);
        } else if (resource instanceof ACL) {
            ((ACL) resource).getMetadata().setResourceVersion(version);
        }
    }

    private void setUid(Object resource, String uid) {
        if (resource instanceof ApplicationService) {
            ((ApplicationService) resource).getMetadata().setUid(uid);
        } else if (resource instanceof VirtualCluster) {
            ((VirtualCluster) resource).getMetadata().setUid(uid);
        } else if (resource instanceof ServiceAccount) {
            ((ServiceAccount) resource).getMetadata().setUid(uid);
        } else if (resource instanceof Topic) {
            ((Topic) resource).getMetadata().setUid(uid);
        } else if (resource instanceof ConsumerGroup) {
            ((ConsumerGroup) resource).getMetadata().setUid(uid);
        } else if (resource instanceof ACL) {
            ((ACL) resource).getMetadata().setUid(uid);
        }
    }
}
