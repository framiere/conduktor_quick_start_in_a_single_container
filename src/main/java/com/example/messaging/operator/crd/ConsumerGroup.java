package com.example.messaging.operator.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("messaging.example.com")
@Version("v1")
@Plural("consumergroups")
public class ConsumerGroup extends CustomResource<ConsumerGroupSpec, Void> implements Namespaced {

    @Override
    protected ConsumerGroupSpec initSpec() {
        return new ConsumerGroupSpec();
    }
}
