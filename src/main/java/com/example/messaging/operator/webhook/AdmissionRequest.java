package com.example.messaging.operator.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

/**
 * Kubernetes AdmissionRequest
 * https://kubernetes.io/docs/reference/access-authn-authz/extensible-admission-controllers/
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdmissionRequest {

    @JsonProperty("uid")
    private String uid;

    @JsonProperty("operation")
    private String operation; // CREATE, UPDATE, DELETE

    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("name")
    private String name;

    @JsonProperty("userInfo")
    private UserInfo userInfo;

    @JsonProperty("object")
    private Map<String, Object> object;

    @JsonProperty("oldObject")
    private Map<String, Object> oldObject;
}
