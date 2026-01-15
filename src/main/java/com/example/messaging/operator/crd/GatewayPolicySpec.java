package com.example.messaging.operator.crd;

import com.example.messaging.operator.conduktor.model.PolicyType;
import io.fabric8.generator.annotation.Required;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayPolicySpec {

    @Required
    private String applicationServiceRef;

    @Required
    private String clusterRef;

    private String serviceAccountRef;

    private String groupRef;

    @Required
    private PolicyType policyType;

    @Required
    private Integer priority;

    private Map<String, Object> config;
}
