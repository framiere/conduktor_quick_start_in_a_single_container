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

Eight CRD types with strict ownership chain enforcement:

```
ApplicationService (root - no parent)
├── KafkaCluster (owned by ApplicationService)
├── ServiceAccount (owned by ApplicationService, references KafkaCluster)
│   ├── Topic (owned by ApplicationService, references ServiceAccount)
│   ├── ACL (owned by ApplicationService, references ServiceAccount + Topic/ConsumerGroup)
│   └── ConsumerGroup (owned by ApplicationService, references ServiceAccount)
├── Scope (owned by ApplicationService, references KafkaCluster, optionally ServiceAccount/group)
└── GatewayPolicy (references Scope via scopeRef + keeps policyType, priority, config)
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
- `/validate/{topic,acl,serviceaccount,kafkacluster,consumergroup}` - Admission validation

### mTLS Authentication Flow

Gateway extracts CN from client certificates to map to Service Accounts:
- `CN=demo-admin` → `demo` kCluster (ACL disabled)
- `CN=demo-acl-admin` → `demo-acl` kCluster (superuser)
- `CN=demo-acl-user` → `demo-acl` kCluster (restricted by ACL)

## Java Coding Standards

### Type Safety

- **Prefer enums over strings** for fixed sets of values (e.g., `CRDKind` instead of `String kind`)
- **Typed ID wrappers** for domain identifiers: `UserId.of(UUID.randomUUID())` not raw `UUID`
- **Use `Objects.requireNonNull()`** instead of if-null-throw patterns
- **Leverage Java 21 features**: text blocks, pattern matching, records where appropriate

### Code Style

- **Lombok annotations**: `@Getter`, `@Builder`, `@AllArgsConstructor`, `@RequiredArgsConstructor`
- **No empty lines before `@Builder.Default`** - keep annotations compact
- **Builder pattern for 3+ parameters** - improves API clarity
- **Java auto-calls `toString()`** in String contexts - no explicit `.toString()` needed
- **Functional style preferred**: `.stream().map().filter().collect()`, `.forEach()`, `.anyMatch()` over imperative loops

### Enums

Document enums with description fields, not Javadoc:
```java
@Getter
@RequiredArgsConstructor
public enum CRDKind {
    TOPIC("Topic", Topic.class),
    ACL("ACL", ACL.class);

    private final String value;
    private final Class<?> resourceClass;
}
```

### Comments & Documentation

- Comments explain **WHY** (intent), not **WHAT** (self-evident code)
- Never add comments that restate what code does
- Never add type enforcement comments (`// TYPED not int`)
- Javadoc only for public APIs that aren't self-documenting

### Security

- Security-sensitive value objects (`Password`, `Token`, `Secret`, `ApiKey`) must override `toString()` to return `"***REDACTED***"`

## Testing Standards

### AssertJ (Required)

Use AssertJ for all assertions - never JUnit assertions:
```java
// Correct
assertThat(result).isNotNull();
assertThat(result.getName()).isEqualTo("expected");
assertThat(list).hasSize(3).contains("item");

// Wrong - don't use JUnit assertions
assertEquals("expected", result.getName());
assertNotNull(result);
```

### Fluent Assertion Chains

Split assertion chains for readability - one assertion aspect per line:
```java
// Correct
assertThat(response).isNotNull();
assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
assertThat(response.getBody()).contains("success");

// Avoid - long chains
assertThat(response).isNotNull().extracting(Response::getStatus).isEqualTo(HttpStatus.OK);
```

### Test Data

- Use `TestDataBuilder` for creating test CRD resources with fluent API
- Use `HttpStatus` enum instead of raw integers for HTTP status codes
- Place YAML fixtures in `src/test/resources/fixtures/`

### Exception Handling in Tests

When base class methods don't declare `throws`, wrap checked exceptions:
```java
@Override
protected void setup() {
    try {
        riskyOperation();
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
}
```

## Code Quality Patterns

- **Never use box characters with right-side lines** in shell scripts or terminal output (║ on the right side gets misaligned). Use left-only borders or simple separators instead.
- **Code is optimized for human reading** - code is read 10x more than written:
  - Clarity over cleverness
  - Explicit over implicit
  - Self-documenting names over comments
  - Consistent patterns over "smart" shortcuts
- Remove obvious/redundant comments - they add zero value:
  - No `// TYPED not UUID` (type is self-evident)
  - No `// Subscribe to user creation events` (method name/code is obvious)
  - No empty Javadoc like `/** Execute a query. */` (adds nothing)
  - Most @Param javadoc are useless, only keep the one that are obviously relevant and add value
- No copyright headers in files
- Modern line length: up to 120 characters (don't artificially break at 80)
- **NEVER write stub implementations** - implement fully or don't write at all:
  - No `throw new UnsupportedOperationException("Not yet implemented")`
  - No `// Implementation would...` placeholder comments
  - No empty method bodies with TODO comments
  - If you write a method signature, write the actual implementation
- **EVERY non-trivial code MUST have a unit test** - no exceptions:
  - If it has logic (conditionals, loops, calculations), it needs a test
  - If it can fail, it needs a test
  - No "I'll add tests later" - tests come with the code or the code doesn't ship
- **When optimizing algorithms, try simplifying first** - removing complex multi-phase logic often improves results more than adding sophistication
- **For compression/indexing algorithms**: use break-even threshold (net_savings >= 0) to capture more useful patterns instead of arbitrary minimums
- **For string replacement algorithms**: sort tokens by length descending before replacing to prevent partial matches (e.g., "TOPIC" before "TOPIC_VIEW")

## Testing

### Test Base Classes

- `KubernetesITBase` - Sets up Fabric8 mock Kubernetes server
- `ScenarioITBase` - Extends K8s base with scenario fixtures
- `ComponentITBase` - Component/unit test setup
- `TestDataBuilder` - Fluent builder for creating test CRD resources

### Test Resources

- `src/test/resources/fixtures/` - YAML fixtures for multi-tenant and ownership chain scenarios

## Development Workflow

- All tests follow TDD: RED → GREEN → REFACTOR → COMMIT
- Each task must pass all tests before marking complete
- Update metadata with actual durations after completion
- Commit after each completed task
- **DO use**: Professional commit messages following established conventions

### The Seven Rules of a Great Git Commit Message

1. **Separate subject from body with a blank line**
2. **Limit the subject line to 50 characters**
3. **Capitalize the subject line**
4. **Do not end the subject line with a period**
5. **Use the imperative mood in the subject line** ("Add feature" not "Added feature")
6. **Wrap the body at 120 characters**
7. **Use the body to explain what and why vs. how**
8. **Add metadata** Duration of the task, duration of the tests, files added/removed/updated

### Modern Conventions (Recommended)

Combine the classic rules with conventional commit format:

- `feat(scope): add new feature` - new functionality
- `fix(scope): resolve issue description` - bug fixes
- `docs(scope): update documentation` - documentation changes
- `refactor(scope): restructure without changing behavior` - code refactoring
- `test(scope): add or update tests` - test changes
- `chore(scope): maintenance tasks` - build, dependencies, etc.

### Learning Section

Capture implicit knowledge discovered during implementation:
- If you had known this earlier, it would have saved time
- If spec was wrong, fix it and note the correction here
- Transform implicit knowledge into explicit for future reference

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
