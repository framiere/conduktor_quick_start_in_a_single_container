package com.example.messaging.operator.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdmissionResponse {

    @JsonProperty("uid")
    private String uid; // Must match request uid

    @JsonProperty("allowed")
    private boolean allowed;

    @JsonProperty("status")
    private Status status;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Status {
        @JsonProperty("message")
        private String message;

        @JsonProperty("code")
        private Integer code;
    }

    public static AdmissionResponse allowed(String uid) {
        AdmissionResponse response = new AdmissionResponse();
        response.setUid(uid);
        response.setAllowed(true);
        return response;
    }

    public static AdmissionResponse denied(String uid, String message) {
        AdmissionResponse response = new AdmissionResponse();
        response.setUid(uid);
        response.setAllowed(false);
        Status status = new Status();
        status.setMessage(message);
        status.setCode(HttpStatus.FORBIDDEN.getCode());
        response.setStatus(status);
        return response;
    }
}
