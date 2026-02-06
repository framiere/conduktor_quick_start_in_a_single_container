package com.example.messaging.operator.crd;

import io.fabric8.generator.annotation.Required;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScopeSpec {

    @Required
    private String applicationServiceRef;

    @Required
    private String clusterRef;

    private String serviceAccountRef;

    private String groupRef;
}
