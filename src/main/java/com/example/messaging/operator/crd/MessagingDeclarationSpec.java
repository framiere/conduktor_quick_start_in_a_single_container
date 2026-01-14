package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Max;
import io.fabric8.generator.annotation.Pattern;
import io.fabric8.generator.annotation.Required;
import io.sundr.builder.annotations.Buildable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Specification of a Messaging Declaration.
 * Defines the topics to own, ACLs to configure, and the target Virtual Cluster.
 */
@Buildable(editableEnabled = false)
@Data
public class MessagingDeclarationSpec {

    @JsonProperty("serviceName")
    @JsonPropertyDescription("Name of the service owning these resources")
    @Required
    @Pattern("^[a-z0-9][a-z0-9-]*[a-z0-9]$")
    @Max(63)
    private String serviceName;

    @JsonProperty("virtualClusterId")
    @JsonPropertyDescription("Conduktor Virtual Cluster ID")
    @Required
    @Max(100)
    private String virtualClusterId;

    @JsonProperty("topics")
    @JsonPropertyDescription("Topics OWNED by this CR")
    private List<TopicSpec> topics = new ArrayList<>();

    @JsonProperty("acls")
    @JsonPropertyDescription("ACL declarations")
    private List<AclSpec> acls = new ArrayList<>();
}
