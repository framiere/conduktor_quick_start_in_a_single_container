package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import lombok.Data;

@Buildable(editableEnabled = false)
@Data
public class ConsumerGroupSpec {

    @JsonProperty("serviceRef")
    @JsonPropertyDescription("Reference to ServiceAccount CR")
    @Required
    private String serviceRef;

    @JsonProperty("name")
    @JsonPropertyDescription("Consumer group name or prefix")
    @Required
    private String name;

    @JsonProperty("patternType")
    @JsonPropertyDescription("Pattern type (LITERAL or PREFIXED)")
    private String patternType = "LITERAL";

    @JsonProperty("applicationServiceRef")
    @JsonPropertyDescription("Reference to ApplicationService CR")
    @Required
    private String applicationServiceRef;
}
