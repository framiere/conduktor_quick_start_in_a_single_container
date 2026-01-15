package com.example.messaging.operator.validation;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import lombok.RequiredArgsConstructor;

/**
 * ResourceLookup implementation backed by in-memory CRDStore.
 * Used for unit testing and standalone operation.
 */
@RequiredArgsConstructor
public class CRDStoreResourceLookup implements ResourceLookup {
    private final CRDStore store;

    @Override
    public ApplicationService getApplicationService(String namespace, String name) {
        return store.get(CRDKind.APPLICATION_SERVICE, namespace, name);
    }

    @Override
    public VirtualCluster getVirtualCluster(String namespace, String name) {
        return store.get(CRDKind.VIRTUAL_CLUSTER, namespace, name);
    }

    @Override
    public ServiceAccount getServiceAccount(String namespace, String name) {
        return store.get(CRDKind.SERVICE_ACCOUNT, namespace, name);
    }

    @Override
    public Topic getTopic(String namespace, String name) {
        return store.get(CRDKind.TOPIC, namespace, name);
    }

    @Override
    public ConsumerGroup getConsumerGroup(String namespace, String name) {
        return store.get(CRDKind.CONSUMER_GROUP, namespace, name);
    }

    @Override
    public ACL getAcl(String namespace, String name) {
        return store.get(CRDKind.ACL, namespace, name);
    }
}
