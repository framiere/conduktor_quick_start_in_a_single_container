package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Buildable(editableEnabled = false)
@Data
public class ServiceAccountSpec {

    @JsonProperty("name")
    @JsonPropertyDescription("Service account name")
    @Required
    private String name;

    @JsonProperty("dn")
    @JsonPropertyDescription("Distinguished Names (DN) for certificates")
    private List<String> dn = new ArrayList<>();

    @JsonProperty("clusterRef")
    @JsonPropertyDescription("Reference to KafkaCluster CR")
    @Required
    private String clusterRef;

    @JsonProperty("applicationServiceRef")
    @JsonPropertyDescription("Reference to ApplicationService CR")
    @Required
    private String applicationServiceRef;
}
