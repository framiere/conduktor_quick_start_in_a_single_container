package com.example.messaging.operator.conduktor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec"})
public class ConduktorTopic extends ConduktorResource<ConduktorTopicSpec> {
    public static final String API_VERSION = "kafka/v2";
    public static final String KIND = "Topic";

    private ConduktorTopicSpec spec;

    @Override
    public ConduktorTopicSpec getSpec() {
        return spec;
    }
}
