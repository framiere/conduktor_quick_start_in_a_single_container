package com.example.messaging.operator.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("messaging.example.com")
@Version("v1")
@Plural("messagingservices")
public class MessagingService extends CustomResource<MessagingServiceSpec, Void> implements Namespaced {

    @Override
    protected MessagingServiceSpec initSpec() {
        return new MessagingServiceSpec();
    }
}
