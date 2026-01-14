package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import lombok.Data;

@Buildable(editableEnabled = false)
@Data
public class MessagingServiceSpec {

    @JsonProperty("serviceAccountRef")
    @JsonPropertyDescription("Reference to ServiceAccount CR")
    @Required
    private String serviceAccountRef;

    @JsonProperty("clusterRef")
    @JsonPropertyDescription("Reference to VirtualCluster CR")
    @Required
    private String clusterRef;
}
