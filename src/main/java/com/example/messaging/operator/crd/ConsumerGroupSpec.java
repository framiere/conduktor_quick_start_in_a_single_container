package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import lombok.Builder;
import lombok.Data;

@Buildable(editableEnabled = false)
@Data
@Builder(toBuilder = true)
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
    private ResourcePatternType patternType = ResourcePatternType.LITERAL;

    @JsonProperty("applicationServiceRef")
    @JsonPropertyDescription("Reference to ApplicationService CR")
    @Required
    private String applicationServiceRef;

    public enum ResourcePatternType {
        LITERAL,
        PREFIXED
    }
}
