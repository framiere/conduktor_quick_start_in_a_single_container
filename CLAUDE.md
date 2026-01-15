# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kubernetes Messaging Operator for Conduktor - implements a Kubernetes Operator with Custom Resource Definitions (CRDs) for multi-tenant messaging infrastructure. The project also includes a Docker container bundling Conduktor Console, Gateway, Redpanda, and supporting services for local development.

## Build Commands

```bash
# Build Java SDK
mvn compile

# Run unit tests (*Test.java)
mvn test

# Run single unit test
mvn test -Dtest=WebhookValidatorTest

# Run integration tests (*IT.java)
mvn verify

# Run single integration test
mvn verify -Dit.test=CRDStoreIT

# Format code (Spotless with Eclipse formatter)
mvn spotless:apply

# Check formatting without applying
mvn spotless:check

# Full Docker build and run
make all

# Individual make targets
make build    # Build Docker image
make run      # Start container
make rm       # Stop and remove container
make clean    # Full cleanup
make logs     # Follow container logs
```

## Architecture

### CRD Hierarchy (Group: `messaging.example.com/v1`)

Six CRD types with strict ownership chain enforcement:

```
ApplicationService (root - no parent)
├── VirtualCluster (owned by ApplicationService)
├── ServiceAccount (owned by ApplicationService, references VirtualCluster)
│   ├── Topic (owned by ApplicationService, references ServiceAccount)
│   ├── ACL (owned by ApplicationService, references ServiceAccount + Topic/ConsumerGroup)
│   └── ConsumerGroup (owned by ApplicationService, references ServiceAccount)
```

### Package Structure (`com.example.messaging.operator`)

- **crd/** - Custom Resource Definition classes with Fabric8 annotations
- **validation/** - `OwnershipValidator` enforces ownership chains, `ValidationResult` wraps outcomes
- **webhook/** - Kubernetes ValidatingWebhook implementation using Java HttpServer (port 8443)
- **store/** - `CRDStore` is thread-safe in-memory storage with event publishing
- **events/** - `ReconciliationEvent` publishes BEFORE/AFTER events for CREATE/UPDATE/DELETE operations

### Key Design Decisions

- **CRDKind enum** - Type-safe CRD kind handling (use instead of raw strings)
- **Ownership immutability** - `applicationServiceRef` cannot be changed after creation
- **Reference validation** - Referenced resources must exist and belong to the same owner
- **Event-driven observability** - All store operations publish reconciliation events

### Webhook Endpoints

- `/health` - Health check
- `/validate/{topic,acl,serviceaccount,virtualcluster,consumergroup}` - Admission validation

### mTLS Authentication Flow

Gateway extracts CN from client certificates to map to Service Accounts:
- `CN=demo-admin` → `demo` vCluster (ACL disabled)
- `CN=demo-acl-admin` → `demo-acl` vCluster (superuser)
- `CN=demo-acl-user` → `demo-acl` vCluster (restricted by ACL)

## Testing

### Test Base Classes

- `KubernetesITBase` - Sets up Fabric8 mock Kubernetes server
- `ScenarioITBase` - Extends K8s base with scenario fixtures
- `ComponentITBase` - Component/unit test setup
- `TestDataBuilder` - Fluent builder for creating test CRD resources

### Test Resources

- `src/test/resources/fixtures/` - YAML fixtures for multi-tenant and ownership chain scenarios

## Docker Container Services

| Port | Service |
|------|---------|
| 8080 | Console UI |
| 8888 | Gateway Admin API (HTTP) |
| 6969 | Gateway Kafka (mTLS) |
| 8081 | Schema Registry |

## Console Access

- URL: http://localhost:8080
- Login: `admin@demo.dev`
- Password: `123_ABC_abc`

## Kubernetes Resources

Deployment manifests in `k8s/`:
- `webhook-deployment.yaml` - HA deployment (2 replicas)
- `webhook-service.yaml` - Service for webhook
- `webhook-config.yaml` - ValidatingWebhookConfiguration

Generated CRD manifests: `src/main/resources/crds-deployment.yaml`
