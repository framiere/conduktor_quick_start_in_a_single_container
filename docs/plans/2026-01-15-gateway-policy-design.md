# GatewayPolicy CRD Design

**Date:** 2026-01-15
**Status:** Approved
**Author:** Claude Code

## Overview

Add support for Conduktor Gateway Interceptors (Policies) to the Kubernetes Messaging Operator. This enables teams to declare governance, security, and data quality policies as Kubernetes CRDs that are transformed and applied to Conduktor Gateway.

## Goals

1. Enable declarative policy management via Kubernetes CRDs
2. Support all 27 Conduktor Gateway interceptor types
3. Maintain ownership chain integrity (ApplicationService → GatewayPolicy)
4. Transform internal CRDs to Conduktor `gateway/v2` Interceptor format

## Non-Goals (Deferred)

- GatewayGroup CRD for user grouping (future iteration)
- ConcentrationRule CRD for topic concentration (future iteration)

## Architecture

### CRD Hierarchy

```
ApplicationService (root)
├── KafkaCluster → VirtualCluster
├── ServiceAccount → GatewayServiceAccount
│   ├── Topic → ConduktorTopic
│   ├── ACL
│   └── ConsumerGroup
└── GatewayPolicy → Interceptor  ← NEW
```

### Internal CRD: GatewayPolicy

```yaml
apiVersion: messaging.example.com/v1
kind: GatewayPolicy
metadata:
  name: enforce-topic-partitions
  namespace: payments-team
spec:
  # Ownership (required)
  applicationServiceRef: payments-service

  # Scoping (at least clusterRef required)
  clusterRef: payments-prod-vcluster
  serviceAccountRef: payments-admin      # Optional
  groupRef: admin-group                  # Optional (future)

  # Policy definition
  policyType: CREATE_TOPIC_POLICY
  priority: 100

  # Plugin-specific configuration
  config:
    topic: "payments-.*"
    numPartition:
      min: 3
      max: 12
      action: BLOCK
```

### Conduktor Output: Interceptor

```yaml
apiVersion: gateway/v2
kind: Interceptor
metadata:
  name: payments-team--enforce-topic-partitions
  scope:
    vCluster: payments-prod-vcluster
spec:
  pluginClass: io.conduktor.gateway.interceptor.safeguard.CreateTopicPolicyPlugin
  priority: 100
  config:
    topic: "payments-.*"
    numPartition:
      min: 3
      max: 12
      action: BLOCK
```

## PolicyType Enum

27 policy types organized by category:

### Traffic Control / Governance
| Enum | Plugin Class |
|------|-------------|
| CREATE_TOPIC_POLICY | safeguard.CreateTopicPolicyPlugin |
| ALTER_TOPIC_POLICY | safeguard.AlterTopicConfigPolicyPlugin |
| PRODUCE_POLICY | safeguard.ProducePolicyPlugin |
| FETCH_POLICY | safeguard.FetchPolicyPlugin |
| CONSUMER_GROUP_POLICY | safeguard.ConsumerGroupPolicyPlugin |
| CLIENT_ID_POLICY | safeguard.ClientIdRequiredPolicyPlugin |
| PRODUCER_RATE_LIMITING | safeguard.ProducerRateLimitingPolicyPlugin |
| LIMIT_CONNECTION | safeguard.LimitConnectionPolicyPlugin |
| LIMIT_JOIN_GROUP | safeguard.LimitJoinGroupPolicyPlugin |

### Data Quality
| Enum | Plugin Class |
|------|-------------|
| SCHEMA_VALIDATION | safeguard.SchemaPayloadValidationPolicyPlugin |
| TOPIC_SCHEMA_ID_REQUIRED | safeguard.TopicRequiredSchemaIdPolicyPlugin |

### Data Security
| Enum | Plugin Class |
|------|-------------|
| FIELD_ENCRYPTION | EncryptPlugin |
| FIELD_DECRYPTION | DecryptPlugin |
| DATA_MASKING | FieldLevelDataMaskingPlugin |
| AUDIT | AuditPlugin |
| HEADER_INJECTION | DynamicHeaderInjectionPlugin |
| HEADER_REMOVAL | safeguard.MessageHeaderRemovalPlugin |

### Advanced Patterns
| Enum | Plugin Class |
|------|-------------|
| LARGE_MESSAGE_HANDLING | LargeMessageHandlingPlugin |
| SQL_TOPIC_FILTERING | VirtualSqlTopicPlugin |
| CEL_TOPIC_FILTERING | CelTopicPlugin |

### Chaos Testing
| Enum | Plugin Class |
|------|-------------|
| CHAOS_LATENCY | chaos.SimulateLatencyPlugin |
| CHAOS_SLOW_BROKER | chaos.SimulateSlowBrokerPlugin |
| CHAOS_SLOW_PRODUCERS_CONSUMERS | chaos.SimulateSlowProducersConsumersPlugin |
| CHAOS_BROKEN_BROKER | chaos.SimulateBrokenBrokersPlugin |
| CHAOS_LEADER_ELECTION | chaos.SimulateLeaderElectionsErrorsPlugin |
| CHAOS_MESSAGE_CORRUPTION | chaos.ProduceSimulateMessageCorruptionPlugin |
| CHAOS_DUPLICATE_MESSAGES | chaos.DuplicateMessagesPlugin |

## Transformer Logic

### Name Generation
Interceptor names are namespaced to avoid collisions:
```
{namespace}--{crd-name}
```

### Scope Resolution
1. `clusterRef` → lookup KafkaCluster → extract `clusterId` → `scope.vCluster`
2. `serviceAccountRef` → lookup ServiceAccount → extract `name` → `scope.username`
3. `groupRef` → pass-through → `scope.group`

### Config Pass-Through
The `config` map is passed through as-is. Validation is delegated to Conduktor CLI dry-run.

## Validation Rules

1. `applicationServiceRef` must exist
2. `clusterRef` must reference valid KafkaCluster owned by same ApplicationService
3. `serviceAccountRef` (if provided) must reference valid ServiceAccount
4. `policyType` must be valid enum value
5. `priority` must be > 0

## File Structure

### New Files
```
src/main/java/com/example/messaging/operator/
├── crd/
│   ├── GatewayPolicy.java
│   └── GatewayPolicySpec.java
├── conduktor/model/
│   ├── ConduktorInterceptor.java
│   ├── ConduktorInterceptorMetadata.java
│   ├── ConduktorInterceptorSpec.java
│   ├── InterceptorScope.java
│   └── PolicyType.java
└── conduktor/transformer/
    └── GatewayPolicyTransformer.java

src/test/java/.../conduktor/transformer/
├── GatewayPolicyTransformerTest.java
└── GatewayPolicyTransformationFT.java

src/test/resources/fixtures/conduktor/
├── input-gateway-policy.yaml
└── expected-interceptor.yaml
```

### Modified Files
- `CRDKind.java` - Add GATEWAY_POLICY entry
- `TransformationPage.jsx` - Add documentation section

## Testing Strategy

### Unit Tests
- Basic transformation
- Scope resolution (clusterRef, serviceAccountRef)
- Namespaced name generation
- Config pass-through
- Error cases (missing refs)

### Functional Tests
- YAML fixture-based tests
- Multiple policy types
- Various scoping combinations

## References

- [Conduktor Interceptors Concept](https://docs.conduktor.io/gateway/concepts/interceptors/)
- [Conduktor Gateway Reference](https://docs.conduktor.io/guide/reference/gateway-reference)
- [Conduktor Gateway Demos](https://github.com/conduktor/conduktor-gateway-demos)
