package com.example.messaging.operator.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdmissionReview DTO Tests")
class AdmissionReviewTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("should deserialize AdmissionReview with UPDATE request")
    void testDeserializeUpdateRequest() throws Exception {
        String json = """
                {
                  "apiVersion": "admission.k8s.io/v1",
                  "kind": "AdmissionReview",
                  "request": {
                    "uid": "test-uid-123",
                    "operation": "UPDATE",
                    "namespace": "default",
                    "name": "test-resource",
                    "userInfo": {
                      "username": "test-user"
                    },
                    "object": {"key": "value"},
                    "oldObject": {"key": "oldValue"}
                  }
                }
                """;

        AdmissionReview review = mapper.readValue(json, AdmissionReview.class);

        assertThat(review.getApiVersion()).isEqualTo("admission.k8s.io/v1");
        assertThat(review.getKind()).isEqualTo("AdmissionReview");
        assertThat(review.getRequest().getUid()).isEqualTo("test-uid-123");
        assertThat(review.getRequest().getOperation()).isEqualTo("UPDATE");
        assertThat(review.getRequest().getNamespace()).isEqualTo("default");
        assertThat(review.getRequest().getUserInfo().getUsername()).isEqualTo("test-user");
    }
}
