package com.example.messaging.operator.crd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Condition;
import io.sundr.builder.annotations.Buildable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Status of a Messaging Declaration CR.
 * Tracks the current state, owned/referenced topics, and reconciliation metadata.
 */
@Buildable(editableEnabled = false)
@Data
public class MessagingDeclarationStatus {

    @JsonProperty("state")
    @JsonPropertyDescription("Current state of the CR")
    private CRState state = CRState.PENDING;

    @JsonProperty("message")
    @JsonPropertyDescription("Human-readable status message")
    private String message;

    @JsonProperty("ownedTopics")
    @JsonPropertyDescription("Topics successfully claimed (unique set)")
    private List<String> ownedTopics = new ArrayList<>();

    @JsonProperty("referencedTopics")
    @JsonPropertyDescription("External topics referenced (unique set)")
    private List<String> referencedTopics = new ArrayList<>();

    @JsonProperty("lastReconcileTime")
    @JsonPropertyDescription("Last reconciliation timestamp")
    private String lastReconcileTime = Instant.now().toString();

    @JsonProperty("observedGeneration")
    @JsonPropertyDescription("Last observed generation")
    private Long observedGeneration;

    @JsonProperty("conditions")
    @JsonPropertyDescription("Kubernetes conditions")
    private List<Condition> conditions = new ArrayList<>();

    /**
     * Possible CR states during reconciliation.
     */
    public enum CRState {
        PENDING,
        CLAIMING,
        CREATING,
        READY,
        CONFLICT,
        ERROR,
        WAITING,
        DELETING
    }
}
