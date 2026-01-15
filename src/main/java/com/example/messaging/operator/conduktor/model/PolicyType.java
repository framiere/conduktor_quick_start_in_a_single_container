package com.example.messaging.operator.conduktor.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PolicyType {
    // ═══════════════════════════════════════════════════════════════════════════
    // TRAFFIC CONTROL / GOVERNANCE POLICIES
    // ═══════════════════════════════════════════════════════════════════════════

    CREATE_TOPIC_POLICY("CreateTopicPolicy",
            "io.conduktor.gateway.interceptor.safeguard.CreateTopicPolicyPlugin",
            "Enforce topic creation rules (partitions, replication, naming)"),

    ALTER_TOPIC_POLICY("AlterTopicPolicy",
            "io.conduktor.gateway.interceptor.safeguard.AlterTopicConfigPolicyPlugin",
            "Enforce topic configuration change rules"),

    PRODUCE_POLICY("ProducePolicy",
            "io.conduktor.gateway.interceptor.safeguard.ProducePolicyPlugin",
            "Enforce producer settings (acks, compression)"),

    FETCH_POLICY("FetchPolicy",
            "io.conduktor.gateway.interceptor.safeguard.FetchPolicyPlugin",
            "Enforce consumer fetch policies"),

    CONSUMER_GROUP_POLICY("ConsumerGroupPolicy",
            "io.conduktor.gateway.interceptor.safeguard.ConsumerGroupPolicyPlugin",
            "Enforce consumer group policies (groupId, session timeout)"),

    CLIENT_ID_POLICY("ClientIdPolicy",
            "io.conduktor.gateway.interceptor.safeguard.ClientIdRequiredPolicyPlugin",
            "Enforce client ID naming conventions"),

    PRODUCER_RATE_LIMITING("ProducerRateLimiting",
            "io.conduktor.gateway.interceptor.safeguard.ProducerRateLimitingPolicyPlugin",
            "Add producer throughput quota limits"),

    LIMIT_CONNECTION("LimitConnection",
            "io.conduktor.gateway.interceptor.safeguard.LimitConnectionPolicyPlugin",
            "Limit connection attempts"),

    LIMIT_JOIN_GROUP("LimitJoinGroup",
            "io.conduktor.gateway.interceptor.safeguard.LimitJoinGroupPolicyPlugin",
            "Limit consumer group join requests per minute"),

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA QUALITY POLICIES
    // ═══════════════════════════════════════════════════════════════════════════

    SCHEMA_VALIDATION("SchemaValidation",
            "io.conduktor.gateway.interceptor.safeguard.SchemaPayloadValidationPolicyPlugin",
            "Validate message payload against schema"),

    TOPIC_SCHEMA_ID_REQUIRED("TopicSchemaIdRequired",
            "io.conduktor.gateway.interceptor.safeguard.TopicRequiredSchemaIdPolicyPlugin",
            "Require schema ID for topic messages"),

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA SECURITY POLICIES
    // ═══════════════════════════════════════════════════════════════════════════

    FIELD_ENCRYPTION("FieldEncryption",
            "io.conduktor.gateway.interceptor.EncryptPlugin",
            "Field-level encryption on produce"),

    FIELD_DECRYPTION("FieldDecryption",
            "io.conduktor.gateway.interceptor.DecryptPlugin",
            "Field-level decryption on consume"),

    DATA_MASKING("DataMasking",
            "io.conduktor.gateway.interceptor.FieldLevelDataMaskingPlugin",
            "Mask sensitive fields in consumed messages"),

    AUDIT("Audit",
            "io.conduktor.gateway.interceptor.AuditPlugin",
            "Audit Kafka API requests"),

    HEADER_INJECTION("HeaderInjection",
            "io.conduktor.gateway.interceptor.DynamicHeaderInjectionPlugin",
            "Inject dynamic headers into messages"),

    HEADER_REMOVAL("HeaderRemoval",
            "io.conduktor.gateway.interceptor.safeguard.MessageHeaderRemovalPlugin",
            "Remove headers matching regex patterns"),

    // ═══════════════════════════════════════════════════════════════════════════
    // ADVANCED PATTERNS
    // ═══════════════════════════════════════════════════════════════════════════

    LARGE_MESSAGE_HANDLING("LargeMessageHandling",
            "io.conduktor.gateway.interceptor.LargeMessageHandlingPlugin",
            "Offload large messages to S3 (claim check pattern)"),

    SQL_TOPIC_FILTERING("SqlTopicFiltering",
            "io.conduktor.gateway.interceptor.VirtualSqlTopicPlugin",
            "Filter/project messages using SQL"),

    CEL_TOPIC_FILTERING("CelTopicFiltering",
            "io.conduktor.gateway.interceptor.CelTopicPlugin",
            "Filter messages using CEL expressions"),

    // ═══════════════════════════════════════════════════════════════════════════
    // CHAOS TESTING (for non-prod environments)
    // ═══════════════════════════════════════════════════════════════════════════

    CHAOS_LATENCY("ChaosLatency",
            "io.conduktor.gateway.interceptor.chaos.SimulateLatencyPlugin",
            "Simulate network latency"),

    CHAOS_SLOW_BROKER("ChaosSlowBroker",
            "io.conduktor.gateway.interceptor.chaos.SimulateSlowBrokerPlugin",
            "Simulate slow broker responses"),

    CHAOS_SLOW_PRODUCERS_CONSUMERS("ChaosSlowProducersConsumers",
            "io.conduktor.gateway.interceptor.chaos.SimulateSlowProducersConsumersPlugin",
            "Simulate slow producers/consumers on specific topics"),

    CHAOS_BROKEN_BROKER("ChaosBrokenBroker",
            "io.conduktor.gateway.interceptor.chaos.SimulateBrokenBrokersPlugin",
            "Simulate broker failures"),

    CHAOS_LEADER_ELECTION("ChaosLeaderElection",
            "io.conduktor.gateway.interceptor.chaos.SimulateLeaderElectionsErrorsPlugin",
            "Simulate leader election errors"),

    CHAOS_MESSAGE_CORRUPTION("ChaosMessageCorruption",
            "io.conduktor.gateway.interceptor.chaos.ProduceSimulateMessageCorruptionPlugin",
            "Simulate message corruption"),

    CHAOS_DUPLICATE_MESSAGES("ChaosDuplicateMessages",
            "io.conduktor.gateway.interceptor.chaos.DuplicateMessagesPlugin",
            "Inject duplicate messages");

    private final String displayName;
    private final String pluginClass;
    private final String description;
}
