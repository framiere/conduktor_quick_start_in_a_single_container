package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Max;
import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Pattern;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Buildable(editableEnabled = false)
@Data
public class TopicCRSpec {

    @JsonProperty("serviceRef")
    @JsonPropertyDescription("Reference to ServiceAccount CR")
    @Required private String serviceRef;

    @JsonProperty("name")
    @JsonPropertyDescription("Topic name")
    @Required
    @Pattern("^[a-zA-Z0-9._-]+$")
    @Max(249) private String name;

    @JsonProperty("partitions")
    @JsonPropertyDescription("Number of partitions")
    @Min(1)
    @Max(1000) private int partitions = 6;

    @JsonProperty("replicationFactor")
    @JsonPropertyDescription("Replication factor")
    @Min(1)
    @Max(5) private int replicationFactor = 3;

    @JsonProperty("config")
    @JsonPropertyDescription("Topic configuration (key-value pairs)") private Map<String, String> config = new HashMap<>();

    @JsonProperty("applicationServiceRef")
    @JsonPropertyDescription("Reference to ApplicationService CR")
    @Required private String applicationServiceRef;
}
