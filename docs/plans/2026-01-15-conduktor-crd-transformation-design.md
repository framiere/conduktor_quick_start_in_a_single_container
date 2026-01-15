# Conduktor CRD Transformation Design

## Overview

Transform internal CRDs to Conduktor Gateway CRDs and validate via `conduktor apply --dry-run`.

## Architecture

```
Your CRD → Webhook validates → Transformer converts → YAML file → CLI dry-run
```

## CRD Mappings

| Internal CRD | Conduktor CRD | API Version |
|--------------|---------------|-------------|
| KafkaCluster | VirtualCluster | gateway/v2 |
| ServiceAccount | GatewayServiceAccount | gateway/v2 |
| Topic | Topic | kafka/v2 |
| ApplicationService | (tenant boundary only) | - |
| ACL | (future: Interceptor) | gateway/v2 |
| ConsumerGroup | (permissions-based) | - |

## Transformation Examples

### KafkaCluster → VirtualCluster

```yaml
# Input
apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: team-a-cluster
spec:
  clusterId: team-a-cluster
  applicationServiceRef: team-a-app

# Output
apiVersion: gateway/v2
kind: VirtualCluster
metadata:
  name: team-a-cluster
spec:
  aclEnabled: true
```

### ServiceAccount → GatewayServiceAccount

```yaml
# Input
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: team-a-sa
spec:
  name: team-a-sa
  dn: ["CN=team-a-sa"]
  clusterRef: team-a-cluster
  applicationServiceRef: team-a-app

# Output
apiVersion: gateway/v2
kind: GatewayServiceAccount
metadata:
  name: team-a-sa
  vCluster: team-a-cluster
spec:
  type: EXTERNAL
  externalNames:
    - team-a-sa
```

### Topic → Topic

```yaml
# Input
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: orders-topic
spec:
  name: orders
  partitions: 3
  replicationFactor: 1
  serviceRef: team-a-sa
  applicationServiceRef: team-a-app

# Output
apiVersion: kafka/v2
kind: Topic
metadata:
  cluster: team-a-cluster  # resolved via serviceRef → clusterRef
  name: orders
spec:
  partitions: 3
  replicationFactor: 1
```

## Package Structure

```
com.example.messaging.operator.conduktor/
├── model/                          # Conduktor CRD POJOs
│   ├── ConduktorResource.java      # Base with apiVersion, kind, metadata
│   ├── VirtualCluster.java
│   ├── VirtualClusterSpec.java
│   ├── GatewayServiceAccount.java
│   ├── GatewayServiceAccountSpec.java
│   ├── ConduktorTopic.java
│   └── ConduktorTopicSpec.java
│
├── transformer/                    # CRD → Conduktor converters
│   ├── CrdTransformer.java         # Interface
│   ├── KafkaClusterTransformer.java
│   ├── ServiceAccountTransformer.java
│   └── TopicTransformer.java
│
├── yaml/                           # YAML generation
│   └── ConduktorYamlWriter.java
│
└── cli/                            # CLI execution
    ├── ConduktorCli.java           # --dry-run only
    └── CliResult.java
```

## CLI Execution

**Dry-run only** - validate transformation without creating resources:

```java
public CliResult apply(ConduktorResource resource) {
    Path yamlFile = yamlWriter.writeToTempFile(resource);
    try {
        ProcessBuilder pb = new ProcessBuilder(
            conduktorBinary, "apply", "-f", yamlFile.toString(), "--dry-run"
        );
        Process process = pb.start();
        return new CliResult(exitCode, stdout, stderr);
    } finally {
        Files.deleteIfExists(yamlFile);
    }
}
```

## Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| CONDUKTOR_CLI_PATH | conduktor | Path to CLI binary |
| CONDUKTOR_CLI_TIMEOUT | 30 | Timeout in seconds |

## Testing Strategy

- **Unit tests**: Each transformer (input → expected YAML)
- **Integration tests**: Mocked CLI, verify YAML structure
- **E2E tests**: Real CLI dry-run in Minikube

## Error Handling

- CLI not found → Fail admission with clear error
- CLI timeout → Fail admission, log warning
- CLI dry-run fails → Fail admission, include stderr
