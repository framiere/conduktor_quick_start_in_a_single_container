package com.example.messaging.operator.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdmissionReview {

    @JsonProperty("apiVersion") private String apiVersion = "admission.k8s.io/v1";

    @JsonProperty("kind") private String kind = "AdmissionReview";

    @JsonProperty("request") private AdmissionRequest request;

    @JsonProperty("response") private AdmissionResponse response;
}
