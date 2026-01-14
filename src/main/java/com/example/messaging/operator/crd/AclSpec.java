package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Pattern;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import lombok.Data;

import java.util.List;

/**
 * ACL specification for a topic.
 * Defines which Kafka operations are allowed for the service account on a given topic.
 */
@Buildable(editableEnabled = false)
@Data
public class AclSpec {

    @JsonProperty("topic")
    @JsonPropertyDescription("Topic name (owned or referenced)")
    @Required
    @Pattern("^[a-zA-Z0-9._-]+$")
    private String topic;

    @JsonProperty("operations")
    @JsonPropertyDescription("Kafka operations (unique set)")
    @Required
    private List<KafkaOperation> operations = List.of();

    /**
     * Kafka ACL operations enum.
     */
    public enum KafkaOperation {
        READ,
        WRITE,
        CREATE,
        DELETE,
        ALTER,
        DESCRIBE,
        CLUSTER_ACTION,
        DESCRIBE_CONFIGS,
        ALTER_CONFIGS,
        IDEMPOTENT_WRITE,
        ALL
    }
}
