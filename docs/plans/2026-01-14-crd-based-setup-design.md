# CRD-Based Service Setup Design

## Overview

Replace the hardcoded `setupOrdersService()` method with a generic CRD parser that can provision Kafka resources (vClusters, topics, ACLs) declaratively from a YAML specification.

## Goals

- Parse CRD from embedded string constant
- Create vClusters dynamically if they don't exist
- Infer ACL settings from CRD structure
- Keep existing demo setup methods unchanged
- Generic approach that works for any service

## Architecture

### Overall Flow

1. Parse CRD YAML string into Java objects
2. Extract `virtualClusterId` from spec
3. Check if vCluster exists via Gateway API
4. Create vCluster if needed (ACL enabled/disabled based on CRD)
5. Create Gateway ServiceAccount for mTLS authentication
6. Create topics from CRD spec
7. Create Console ServiceAccount with ACLs (if ACLs defined in CRD)
8. Generate SSL properties file

### Code Changes

**Remove:**
- `setupOrdersService()` method

**Add:**
- `processCRD(String crdYaml)` - main orchestration
- `parseYaml(String yaml)` - YAML parsing
- `vClusterExists(String name)` - check vCluster existence
- `createVClusterFromCRD(String name, boolean hasAcls, String serviceName)` - dynamic vCluster creation
- `createGatewayServiceAccount(String vCluster, String serviceName)` - mTLS auth account
- `createTopicsFromCRD(String vCluster, List<TopicDef> topics)` - topic provisioning
- `createAclsFromCRD(String vCluster, String serviceName, List<AclDef> acls)` - ACL provisioning
- `addVClusterToConsole(String vCluster, String serviceName)` - Console registration

**Modify:**
- `run()` - call `processCRD(CRD)` instead of `setupOrdersService()`

## Data Model

### CRD Structure (YAML)
```yaml
apiVersion: messaging.example.com/v1
kind: MessagingDeclaration
metadata:
  name: orders-service
  namespace: orders
spec:
  serviceName: orders-service
  virtualClusterId: prod-cluster
  topics:
    - name: orders.events
      partitions: 12
      replicationFactor: 3
      config:
        retention.ms: "604800000"
        cleanup.policy: "delete"
    - name: orders.deadletter
      partitions: 3
  acls:
    - topic: orders.events
      operations: [READ, WRITE]
    - topic: orders.deadletter
      operations: [READ, WRITE]
    - topic: inventory.updates
      operations: [READ]
```

### Java POJOs
```java
class MessagingDeclaration {
    String apiVersion;
    String kind;
    Metadata metadata;
    Spec spec;
}

class Metadata {
    String name;
    String namespace;
}

class Spec {
    String serviceName;        // Required
    String virtualClusterId;   // Required
    List<TopicDef> topics;     // Optional, defaults to empty
    List<AclDef> acls;         // Optional, defaults to empty
}

class TopicDef {
    String name;                      // Required
    Integer partitions;               // Optional, defaults to 1
    Integer replicationFactor;        // Optional, defaults to 1
    Map<String, String> config;       // Optional, defaults to empty
}

class AclDef {
    String topic;                // Required
    List<String> operations;     // Required: READ, WRITE, etc.
}
```

## YAML Parsing

**Library:** SnakeYAML 2.0

**Dependency:**
```xml
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.0</version>
</dependency>
```

**Parsing:**
```java
private MessagingDeclaration parseYaml(String crdYaml) {
    Yaml yaml = new Yaml(new Constructor(MessagingDeclaration.class));
    return yaml.loadAs(crdYaml, MessagingDeclaration.class);
}
```

**Validation:**
- Check required fields: `serviceName`, `virtualClusterId`
- Throw clear error messages for missing fields
- Apply defaults for optional fields

## VirtualCluster Management

### Existence Check
```java
private boolean vClusterExists(String vClusterName) throws ApiException {
    try {
        virtualClusterApi.getAVirtualCluster(vClusterName);
        return true;
    } catch (ApiException e) {
        if (e.getCode() == 404) {
            return false;
        }
        throw e;
    }
}
```

### Creation Logic

**ACL Inference:**
- If CRD has `acls` section → create with `aclEnabled: true`
- If no `acls` section → create with `aclMode: KAFKA_API` (ACL disabled)

**Naming conventions:**
- ACL enabled: create admin account named `{serviceName}-admin`
- ACL disabled: service itself is superUser

**Console registration:**
- Always add vCluster to Console for visibility
- Use appropriate mTLS configuration
- Use admin account credentials for Console connection

## Topic Creation

```java
private void createTopicsFromCRD(String vClusterName, List<TopicDef> topics) {
    for (TopicDef topic : topics) {
        createTopic(
            vClusterName,
            topic.name,
            topic.partitions != null ? topic.partitions : 1,
            topic.replicationFactor,
            topic.config != null ? topic.config : Map.of()
        );
    }
}
```

**Behavior:**
- Use existing `createTopic()` method
- Apply defaults: 1 partition, 1 replication factor, empty config
- Console API handles upsert (topic already exists = no error)

## ACL Creation

```java
private void createAclsFromCRD(String vClusterName, String serviceName, List<AclDef> acls) {
    List<KafkaServiceAccountACL> kafkaAcls = new ArrayList<>();

    // Convert CRD ACLs to Kafka ACLs
    for (AclDef aclDef : acls) {
        List<Operation> ops = aclDef.operations.stream()
            .map(Operation::valueOf)
            .toList();

        kafkaAcls.add(new KafkaServiceAccountACL()
            .type(AclResourceType.TOPIC)
            .name(aclDef.topic)
            .patternType(ResourcePatternType.LITERAL)
            .operations(ops)
            .host("*")
            .permission(AclPermissionTypeForAccessControlEntry.ALLOW));
    }

    // Add consumer group ACL
    kafkaAcls.add(new KafkaServiceAccountACL()
        .type(AclResourceType.CONSUMER_GROUP)
        .name(serviceName + "-")
        .patternType(ResourcePatternType.PREFIXED)
        .operations(List.of(Operation.READ))
        .host("*")
        .permission(AclPermissionTypeForAccessControlEntry.ALLOW));

    // Create Console ServiceAccount
    serviceAccountApi.createOrUpdateServiceAccountV1(vClusterName,
        new ServiceAccountResourceV1()
            .apiVersion("v1")
            .kind(ServiceAccountKind.SERVICE_ACCOUNT)
            .metadata(new ServiceAccountMetadata()
                .name(serviceName)
                .cluster(vClusterName))
            .spec(new ServiceAccountSpec()
                .authorization(new ServiceAccountAuthorization(new KAFKAACL()
                    .type(KAFKAACL.TypeEnum.KAFKA_ACL)
                    .acls(kafkaAcls)))), null);
}
```

**ACL patterns:**
- Topic ACLs: LITERAL match (exact topic name)
- Consumer group: PREFIXED match (`{serviceName}-*`)
- Operations: map directly from CRD strings to enum values

**Consumer group convention:**
- Always added automatically with READ permission
- Prefix pattern allows flexibility: `orders-service-consumer-1`, `orders-service-worker`, etc.

## Service Account Creation

**Two types created:**

1. **Gateway ServiceAccount** (always created)
   - Purpose: mTLS authentication to Gateway
   - Type: EXTERNAL
   - External name: `CN={serviceName},OU=TEST,O=CONDUKTOR,L=LONDON,C=UK`

2. **Console ServiceAccount** (only if ACLs defined)
   - Purpose: ACL authorization on topics
   - Contains all topic ACLs + consumer group ACL
   - Skipped if CRD has no `acls` section

## Main Processing Flow

```java
private void processCRD(String crdYaml) throws Exception {
    // 1. Parse
    MessagingDeclaration crd = parseYaml(crdYaml);
    String serviceName = crd.spec.serviceName;
    String vClusterName = crd.spec.virtualClusterId;

    System.out.println("Processing CRD for service: " + serviceName);

    // 2. Ensure vCluster exists
    if (!vClusterExists(vClusterName)) {
        System.out.println("Creating vCluster: " + vClusterName);
        boolean hasAcls = crd.spec.acls != null && !crd.spec.acls.isEmpty();
        createVClusterFromCRD(vClusterName, hasAcls, serviceName);
    }

    // 3. Gateway ServiceAccount (mTLS)
    System.out.println("Creating Gateway ServiceAccount: " + serviceName);
    createGatewayServiceAccount(vClusterName, serviceName);

    // 4. Topics
    if (crd.spec.topics != null && !crd.spec.topics.isEmpty()) {
        System.out.println("Creating topics...");
        createTopicsFromCRD(vClusterName, crd.spec.topics);
    }

    // 5. Console ServiceAccount with ACLs (conditional)
    if (crd.spec.acls != null && !crd.spec.acls.isEmpty()) {
        System.out.println("Creating Console ServiceAccount with ACLs");
        createAclsFromCRD(vClusterName, serviceName, crd.spec.acls);
    }

    // 6. SSL properties
    generateSslProperties(serviceName);

    System.out.println("Service setup complete: " + serviceName);
}
```

## Error Handling

**Parsing errors:**
- Catch YAML parsing exceptions
- Throw with message: "Invalid CRD format: {details}"

**Validation errors:**
- Check required fields before processing
- Throw with specific field name: "Missing required field: serviceName"

**API errors:**
- Let exceptions bubble up (existing error handling)
- vCluster creation failure stops entire process
- Topic/ServiceAccount APIs handle upsert (no error if exists)

**Operation mapping:**
- Operation strings must match enum names exactly
- Invalid operation throws IllegalArgumentException

## Integration

**Modified `run()` method:**
```java
public void run() throws Exception {
    waitForService("Conduktor Console", cdkBaseUrl + "/platform/api/modules/resources/health/live");
    waitForGateway();
    authenticate();

    System.out.println("Setting up vClusters with mTLS: " + vCluster + ", " + vClusterAcl);

    setupDemoVCluster();      // Keep existing
    setupDemoAclVCluster();   // Keep existing

    processCRD(CRD);          // Replace setupOrdersService()

    System.out.println("Setup complete!");
}
```

## Benefits

1. **Declarative** - services defined in clear YAML format
2. **Dynamic** - creates infrastructure as needed
3. **Reusable** - same code works for any service
4. **Maintainable** - easy to add new services by adding CRDs
5. **Safe** - idempotent operations, no errors if resources exist

## Future Enhancements

- Load CRDs from external files
- Support multiple CRDs in one run
- Add CRD validation schema
- Support more ACL patterns (PREFIXED topics, etc.)
- Add interceptor configurations
- Support topic updates (partition changes, config updates)
