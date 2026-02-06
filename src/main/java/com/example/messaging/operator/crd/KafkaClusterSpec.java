package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import lombok.Data;

@Buildable(editableEnabled = false)
@Data
public class KafkaClusterSpec {

    @JsonProperty("clusterId")
    @JsonPropertyDescription("Conduktor Virtual Cluster ID")
    @Required
    private String clusterId;

    @JsonProperty("applicationServiceRef")
    @JsonPropertyDescription("Reference to ApplicationService CR")
    @Required
    private String applicationServiceRef;

    @JsonProperty("authType")
    @JsonPropertyDescription("Authentication type: MTLS or SASL_SSL")
    private AuthType authType;
}
