package com.example.messaging.operator.store;

import com.example.messaging.operator.crd.*;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CRDKind {
    APPLICATION_SERVICE("ApplicationService", ApplicationService.class),
    KAFKA_CLUSTER("KafkaCluster", KafkaCluster.class),
    SERVICE_ACCOUNT("ServiceAccount", ServiceAccount.class),
    TOPIC("Topic", Topic.class),
    ACL("ACL", ACL.class),
    CONSUMER_GROUP("ConsumerGroup", ConsumerGroup.class),
    GATEWAY_POLICY("GatewayPolicy", GatewayPolicy.class);

    private final String value;
    private final Class<?> resourceClass;

    public static CRDKind fromClass(Class<?> clazz) {
        return Arrays.stream(values())
                .filter(kind -> kind.resourceClass.equals(clazz))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown resource class: " + clazz.getName()));
    }

    public static CRDKind fromValue(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown CRD kind: " + value));
    }

    @Override
    public String toString() {
        return value;
    }
}
