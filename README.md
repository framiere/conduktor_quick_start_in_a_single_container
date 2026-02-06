# Kubernetes Messaging Operator for Conduktor

A production-ready Kubernetes Operator that brings GitOps-style declarative management to your Kafka infrastructure through Conduktor Gateway.

<p align="center">
  <a href="https://framiere.github.io/conduktor_quick_start_in_a_single_container/">
    <img src="https://img.shields.io/badge/Documentation-Website-blue?style=for-the-badge&logo=github" alt="Documentation Website"/>
  </a>
</p>

> **[View Full Documentation](https://framiere.github.io/conduktor_quick_start_in_a_single_container/)** - Interactive guides, architecture diagrams, and comprehensive API reference

---

## What This Project Does

Transform your Kafka governance from manual Console clicks to **declarative Kubernetes manifests**:

```yaml
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: payments-events
  namespace: payments-team
spec:
  serviceAccountRef: payments-processor
  topicName: payments.events.v1
  partitions: 12
  replicationFactor: 3
```

The operator watches these CRDs and automatically provisions the corresponding resources in Conduktor Gateway.

## Key Features

| Feature | Description |
|---------|-------------|
| **8 CRD Types** | ApplicationService, KafkaCluster, ServiceAccount, Topic, ACL, ConsumerGroup, Scope, GatewayPolicy |
| **27 Gateway Policies** | Traffic control, data masking, encryption, chaos testing |
| **Ownership Validation** | Kubernetes-native ownership chains with ValidatingWebhook |
| **Multi-Tenant** | Namespace-based isolation with strict resource boundaries |
| **mTLS Authentication** | Certificate-based identity mapping to Virtual Clusters |

## Quick Start

### Option 1: Docker (Local Development)

```sh
make all
```

This builds and runs a single container bundling Conduktor Console, Gateway, Redpanda, and generates mTLS certificates automatically.

**Access Console:** http://localhost:8080
**Login:** `admin@demo.dev` / `123_ABC_abc`

### Option 2: Minikube (Kubernetes)

```sh
# Start Minikube with required resources
minikube start --cpus=4 --memory=8192

# Deploy the operator
kubectl apply -f k8s/

# Create your first resources
kubectl apply -f examples/
```

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                              │
│                                                                     │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────────────┐│
│  │   Your CRDs  │────▶│   Operator   │────▶│  Conduktor Gateway   ││
│  │              │     │              │     │                      ││
│  │ - Topic      │     │ - Watches    │     │ - VirtualCluster     ││
│  │ - ACL        │     │ - Validates  │     │ - ServiceAccount     ││
│  │ - Policy     │     │ - Transforms │     │ - Topic              ││
│  └──────────────┘     └──────────────┘     │ - Interceptor        ││
│                                            └──────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

## CRD Hierarchy

```
ApplicationService (root - no parent)
├── KafkaCluster → VirtualCluster
├── ServiceAccount → GatewayServiceAccount
│   ├── Topic → ConduktorTopic
│   ├── ACL
│   └── ConsumerGroup
├── Scope (bundles cluster/serviceAccount/group targeting)
└── GatewayPolicy → Interceptor (references Scope via scopeRef)
```

## Documentation

The **[full documentation website](https://framiere.github.io/conduktor_quick_start_in_a_single_container/)** includes:

- **Architecture** - System design, data flow diagrams, component interactions
- **Security** - mTLS setup, ownership validation, access control patterns
- **CRDs** - Complete reference for all Custom Resource Definitions
- **Gateway Policies** - 27 policy types with transformation examples
- **Testing** - Unit, integration, and E2E test strategies
- **Operations** - Deployment guides, monitoring, troubleshooting

## Development

```sh
# Build
mvn compile

# Run tests
mvn test           # Unit tests (201 tests)
mvn verify         # Integration tests (39 tests)

# Format code
mvn spotless:apply

# Kubernetes deployment (JKube)
mvn k8s:build      # Build Docker image
mvn k8s:resource   # Generate Kubernetes manifests
mvn k8s:apply      # Deploy to Kubernetes cluster
mvn k8s:deploy     # Build, resource, and apply in one step
```

## Services & Ports

| Port | Service | Protocol |
|------|---------|----------|
| 8080 | Console UI | HTTP |
| 8888 | Gateway Admin API | HTTP |
| 6969 | Gateway Kafka | mTLS |
| 8081 | Schema Registry | HTTP |

## Contributing

1. Fork the repository
2. Create a feature branch
3. Follow the coding standards in `CLAUDE.md`
4. Ensure all tests pass
5. Submit a pull request

## License

This project is for demonstration and educational purposes.

---

<p align="center">
  <strong>
    <a href="https://framiere.github.io/conduktor_quick_start_in_a_single_container/">
      Explore the Full Documentation
    </a>
  </strong>
</p>
