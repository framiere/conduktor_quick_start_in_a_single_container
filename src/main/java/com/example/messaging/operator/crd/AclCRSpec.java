package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import lombok.Data;
import org.openapitools.client.model.AclPermissionTypeForAccessControlEntry;
import org.openapitools.client.model.Operation;

import java.util.ArrayList;
import java.util.List;

/**
 * ACL specification for individual ACL CR.
 * References a MessagingService and either a Topic or ConsumerGroup.
 */
@Buildable(editableEnabled = false)
@Data
public class AclCRSpec {

    @JsonProperty("serviceRef")
    @JsonPropertyDescription("Reference to MessagingService CR")
    @Required
    private String serviceRef;

    @JsonProperty("topicRef")
    @JsonPropertyDescription("Reference to Topic CR (mutually exclusive with consumerGroupRef)")
    private String topicRef;

    @JsonProperty("consumerGroupRef")
    @JsonPropertyDescription("Reference to ConsumerGroup CR (mutually exclusive with topicRef)")
    private String consumerGroupRef;

    @JsonProperty("operations")
    @JsonPropertyDescription("Kafka operations (READ, WRITE, DESCRIBE, etc.)")
    @Required
    private List<Operation> operations = new ArrayList<>();

    @JsonProperty("host")
    @JsonPropertyDescription("Host from which operations are allowed")
    private String host = "*";

    @JsonProperty("permission")
    @JsonPropertyDescription("Permission type (ALLOW or DENY)")
    private AclPermissionTypeForAccessControlEntry permission = AclPermissionTypeForAccessControlEntry.ALLOW;
}
