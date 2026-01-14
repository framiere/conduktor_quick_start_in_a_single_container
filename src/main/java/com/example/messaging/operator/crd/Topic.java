package com.example.messaging.operator.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Group("messaging.example.com")
@Version("v1")
@Plural("topics")
public class Topic extends CustomResource<TopicCRSpec, Void> implements Namespaced {

    @Override
    protected TopicCRSpec initSpec() {
        return new TopicCRSpec();
    }
}
