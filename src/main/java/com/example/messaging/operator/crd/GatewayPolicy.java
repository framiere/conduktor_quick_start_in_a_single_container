package com.example.messaging.operator.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("messaging.example.com")
@Version("v1")
@Plural("gatewaypolicies")
public class GatewayPolicy extends CustomResource<GatewayPolicySpec, Void> implements Namespaced {

    @Override
    protected GatewayPolicySpec initSpec() {
        return new GatewayPolicySpec();
    }
}
