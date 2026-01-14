package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import lombok.Data;

@Buildable(editableEnabled = false)
@Data
public class ServiceAccountSpec {

    @JsonProperty("name")
    @JsonPropertyDescription("Service account name")
    @Required
    private String name;
}
