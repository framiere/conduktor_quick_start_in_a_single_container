package com.example.messaging.operator.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * Custom Resource Definition for Messaging Declaration.
 * Represents a declarative specification for Kafka topics, ACLs, and service accounts
 * within a Conduktor Virtual Cluster.
 */
@Group("messaging.example.com")
@Version("v1")
@ShortNames({"md", "msgdecl"})
@Plural("messagingdeclarations")
public class MessagingDeclaration
        extends CustomResource<MessagingDeclarationSpec, MessagingDeclarationStatus>
        implements Namespaced {

    @Override
    protected MessagingDeclarationSpec initSpec() {
        return new MessagingDeclarationSpec();
    }

    @Override
    protected MessagingDeclarationStatus initStatus() {
        return new MessagingDeclarationStatus();
    }
}
