# Conduktor Quick Start (Single Container)

This repo builds and runs a single Docker image that bundles Conduktor Console, Gateway, Redpanda, and supporting services.

> [!WARNING]
> DO NOT DO THIS AT HOME.

## Build

```sh
docker build . -t conduktor_quick_start_in_a_single_container
```

## Run

```sh
docker run -d --name conduktor_quick_start_in_a_single_container \
  -p 8080:8080 \
  -p 8888:8888 \
  -p 6969:6969 \
  conduktor_quick_start_in_a_single_container
```

### Exposed Ports

| Port | Service |
|------|---------|
| 8080 | Conduktor Console |
| 8888 | Gateway Admin API |
| 6969 | Gateway Kafka Bootstrap |

## Automatic Setup

On startup, the container automatically:
1. Creates two Virtual Clusters in Gateway
2. Creates service accounts with tokens
3. Registers the vClusters in Console
4. Runs an ACL demonstration

### Check Setup Progress

```sh
docker exec conduktor_quick_start_in_a_single_container cat /var/log/conduktor/setup.log
```

## Access

### Console UI

Open http://localhost:8080

| Field | Value |
|-------|-------|
| Login | `admin@demo.dev` |
| Password | `123=ABC_abc` |

### Virtual Clusters

Two vClusters are created automatically:

#### 1. `demo` (ACL disabled)

Full access for all authenticated users.

| Property | Value |
|----------|-------|
| Bootstrap | `localhost:6969` |
| Security | `SASL_PLAINTEXT` |
| SASL Mechanism | `PLAIN` |
| Username | `admin` |
| Password | *(generated token - see setup.log)* |

#### 2. `demo-acl` (ACL enabled)

Access controlled by ACL rules.

**Admin account** - full access:
| Property | Value |
|----------|-------|
| Username | `admin` |
| Password | *(generated token - see setup.log)* |

**User1 account** - restricted access:
| Property | Value |
|----------|-------|
| Username | `user1` |
| Password | *(generated token - see setup.log)* |
| ACL | Can only access topics prefixed with `click.` |

### ACL Demo Results

The setup script demonstrates ACL enforcement:

| User | Topic | Result | Reason |
|------|-------|--------|--------|
| admin | `click-stream` | ALLOWED | Admin has full access |
| user1 | `click-stream` | **DENIED** | `click-stream` doesn't match `click.` prefix |
| user1 | `click.events` | ALLOWED | `click.events` matches `click.` prefix |

## CLI Usage

### Install Conduktor CLI

https://docs.conduktor.io/guide/conduktor-in-production/automate/cli-automation

Or: https://github.com/conduktor/ctl

### Configure CLI

```sh
export CDK_USER=admin@demo.dev
export CDK_PASSWORD=123_ABC_abc
export CDK_BASE_URL=http://localhost:8080
export CDK_GATEWAY_USER=admin
export CDK_GATEWAY_PASSWORD=conduktor
export CDK_GATEWAY_BASE_URL=http://localhost:8888
```

### Create a Token

```sh
conduktor token create admin myToken
```

### List Resources

```sh
conduktor get KafkaCluster
conduktor get VirtualCluster
conduktor get GatewayServiceAccount
```

or 

## Demo

[![asciicast](https://asciinema.org/a/kVLKsgcBU3XMettxvflF0F9Rz.svg)](https://asciinema.org/a/kVLKsgcBU3XMettxvflF0F9Rz)


```sh
$ conduktor get all
---
apiVersion: v1
kind: DataQualityRule
metadata:
    name: enforce_avro
    createdAt: "2025-12-10T18:44:57.306814Z"
    updatedAt: "2025-12-10T18:44:57.306814Z"
    createdBy: admin
    updatedBy: admin
    attachedPolicies: []
    builtIn: true
spec:
    displayName: Enforce Avro
    description: Ensures that Kafka messages have an Avro schema registered in a Schema Registry
    customErrorMessage: Message is not Avro-encoded
    type: EnforceAvro
---
apiVersion: v1
kind: DataQualityRule
metadata:
    name: enforce_schema_id
    createdAt: "2025-12-10T18:44:57.597649Z"
    updatedAt: "2025-12-10T18:44:57.597649Z"
    createdBy: admin
    updatedBy: admin
    attachedPolicies: []
    builtIn: true
spec:
    displayName: Enforce schema ID
    description: Ensures that Kafka messages start with a magic byte and a schemaId without calling the schema registry
    customErrorMessage: Message is missing a valid schema ID
    type: EnforceSchemaId
---
apiVersion: gateway/v2
kind: GatewayServiceAccount
metadata:
    name: admin
    vCluster: demo
spec:
    type: LOCAL
---
apiVersion: gateway/v2
kind: GatewayServiceAccount
metadata:
    name: admin
    vCluster: demo-acl
spec:
    type: LOCAL
---
apiVersion: gateway/v2
kind: GatewayServiceAccount
metadata:
    name: user1
    vCluster: demo-acl
spec:
    type: LOCAL
---
apiVersion: v2
kind: Group
metadata:
    name: admin
spec:
    displayName: admin
    description: Built-in group with admin level access
    members:
        - admin@demo.dev
---
apiVersion: gateway/v2
kind: Interceptor
metadata:
    name: encrypt-full-message-on-produce
    scope:
        vCluster: passthrough
        group: null
        username: null
spec:
    comment: Encrypt the payload using an in-memory kms (do not use in production)
    pluginClass: io.conduktor.gateway.interceptor.EncryptPlugin
    priority: 100
    config:
        topic: .*_encrypted$
        payload:
            keySecretId: in-memory-kms://myKeySecretId
            algorithm:
                type: AES128_GCM
                kms: IN_MEMORY
---
apiVersion: gateway/v2
kind: Interceptor
metadata:
    name: guard-create-project-topics
    scope:
        vCluster: passthrough
        group: null
        username: null
spec:
    comment: Make sure we do not overuse partitions
    pluginClass: io.conduktor.gateway.interceptor.safeguard.CreateTopicPolicyPlugin
    priority: 100
    config:
        topic: project-.*
        numPartition:
            min: 1
            max: 3
            action: BLOCK
---
apiVersion: gateway/v2
kind: Interceptor
metadata:
    name: decrypt-full-message-on-consume
    scope:
        vCluster: passthrough
        group: null
        username: null
spec:
    comment: Decrypt
    pluginClass: io.conduktor.gateway.interceptor.DecryptPlugin
    priority: 100
    config:
        topic: .*_encrypted$
---
apiVersion: gateway/v2
kind: Interceptor
metadata:
    name: guard-produce-policy
    scope:
        vCluster: passthrough
        group: null
        username: null
spec:
    comment: Prevent data loss and require compression
    pluginClass: io.conduktor.gateway.interceptor.safeguard.ProducePolicyPlugin
    priority: 100
    config:
        acks:
            value:
                - -1
            action: BLOCK
        compressions:
            value:
                - GZIP
                - LZ4
                - ZSTD
                - SNAPPY
            action: INFO
---
apiVersion: gateway/v2
kind: Interceptor
metadata:
    name: mask-sensitive-fields
    scope:
        vCluster: passthrough
        group: null
        username: null
spec:
    comment: Mask sensitive data
    pluginClass: io.conduktor.gateway.interceptor.FieldLevelDataMaskingPlugin
    priority: 100
    config:
        topic: ^[A-Za-z]*_masked$
        schemaRegistryConfig:
            host: http://redpanda-0:8081
        policies:
            - name: Mask credit card
              rule:
                type: MASK_ALL
              fields:
                - profile.creditCardNumber
                - contact.email
            - name: Partial mask phone
              rule:
                type: MASK_FIRST_N
                maskingChar: '*'
                numberOfChars: 9
              fields:
                - contact.phone
---
apiVersion: v2
kind: KafkaCluster
metadata:
    name: local-kafka
spec:
    displayName: local-kafka
    bootstrapServers: localhost:9092
    color: '#6A57C8'
    icon: kafka
    schemaRegistry:
        url: http://localhost:8081
        security:
            type: NoSecurity
        ignoreUntrustedCertificate: false
        type: ConfluentLike
---
apiVersion: v2
kind: KafkaCluster
metadata:
    name: demo
spec:
    displayName: demo
    bootstrapServers: localhost:6969
    properties:
        security.protocol: SASL_PLAINTEXT
        sasl.mechanism: PLAIN
        sasl.jaas.config: org.apache.kafka.common.security.plain.PlainLoginModule required username='admin' password='eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6ImFkbWluIiwidmNsdXN0ZXIiOiJkZW1vIiwiZXhwIjoxNzczMTY4NDMzfQ.CfSK_zC3gIhSO40qJ1tBdv6muONrKfGEwX8S2dsnGMA';
    kafkaFlavor:
        url: http://localhost:8888
        user: admin
        password: conduktor
        virtualCluster: demo
        ignoreUntrustedCertificate: false
        type: Gateway
---
apiVersion: v2
kind: KafkaCluster
metadata:
    name: demo-acl
spec:
    displayName: demo-acl (ACL enabled)
    bootstrapServers: localhost:6969
    properties:
        security.protocol: SASL_PLAINTEXT
        sasl.mechanism: PLAIN
        sasl.jaas.config: org.apache.kafka.common.security.plain.PlainLoginModule required username='admin' password='eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6ImFkbWluIiwidmNsdXN0ZXIiOiJkZW1vLWFjbCIsImV4cCI6MTc3MzE2ODQzNH0.VkR4ewvtF14_y8zZyT3tlsbAjxH-vwg9KdM3F6rQXr0';
    kafkaFlavor:
        url: http://localhost:8888
        user: admin
        password: conduktor
        virtualCluster: demo-acl
        ignoreUntrustedCertificate: false
        type: Gateway
---
apiVersion: v2
kind: User
metadata:
    name: admin@demo.dev
    lastLoginDate: "2025-12-10T18:48:58.064594Z"
spec: {}
---
apiVersion: gateway/v2
kind: VirtualCluster
metadata:
    name: demo
spec:
    aclEnabled: false
    aclMode: KAFKA_API
    superUsers:
        - admin
    type: Standard
    bootstrapServers: localhost:6969
    clientProperties:
        PLAIN:
            security.protocol: SASL_PLAINTEXT
            sasl.mechanism: PLAIN
            sasl.jaas.config: org.apache.kafka.common.security.plain.PlainLoginModule required username='{{username}}' password='{{password}}';
---
apiVersion: gateway/v2
kind: VirtualCluster
metadata:
    name: demo-acl
spec:
    aclEnabled: true
    aclMode: KAFKA_API
    superUsers:
        - admin
    type: Standard
    bootstrapServers: localhost:6969
    clientProperties:
        PLAIN:
            security.protocol: SASL_PLAINTEXT
            sasl.mechanism: PLAIN
            sasl.jaas.config: org.apache.kafka.common.security.plain.PlainLoginModule required username='{{username}}' password='{{password}}';

```
