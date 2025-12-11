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
| 6969 | Gateway Kafka Bootstrap (mTLS) |

## Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                          Single Container                          │
│                                                                    │
│   ┌─────────────┐         ┌─────────────┐         ┌─────────────┐  │
│   │   Client    │  mTLS   │   Gateway   │Plaintext│  Redpanda   │  │
│   │ (with cert) │────────▶│   :6969     │────────▶│   :9092     │  │
│   └─────────────┘         └─────────────┘         └─────────────┘  │
│                                  ^                                 │
│   ┌─────────────┐         ┌──────|──────┐                          │
│   │   Console   │Plaintext│  Redpanda   │                          │
│   │   :8080     │────────▶│   :9092     │                          │
│   └─────────────┘         └─────────────┘                          │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

- **Gateway** exposes mTLS to clients on port 6969
- **Gateway** connects to Redpanda via plaintext internally
- **Console** connects to Redpanda directly via plaintext
- **Schema Registry** is HTTP (plaintext)

## Automatic Setup

On startup, the container automatically:
1. Generates mTLS certificates (CA, gateway, admin, user1)
2. Creates two Virtual Clusters in Gateway with mTLS authentication
3. Creates service accounts mapped to certificate CNs
4. Registers the vClusters in Console
5. Runs an ACL demonstration with mTLS

### Check Setup Progress

```sh
docker exec conduktor_quick_start_in_a_single_container cat /var/log/conduktor/setup.log
```

### View Generated Certificates

```sh
docker exec conduktor_quick_start_in_a_single_container ls -la /var/lib/conduktor/certs/
```

## Access

### Console UI

Open http://localhost:8080

| Field | Value |
|-------|-------|
| Login | `admin@demo.dev` |
| Password | `123_ABC_abc` |

### Virtual Clusters (mTLS Authentication)

Two vClusters are created automatically with **mTLS authentication**:

#### 1. `demo` (ACL disabled)

Full access for all authenticated users.

| Property | Value |
|----------|-------|
| Bootstrap | `localhost:6969` |
| Security | `SSL` (mTLS) |
| Certificate | `/var/lib/conduktor/certs/admin.keystore.jks` |
| Truststore | `/var/lib/conduktor/certs/admin.truststore.jks` |
| Password | `conduktor` |

#### 2. `demo-acl` (ACL enabled)

Access controlled by ACL rules.

**Admin account** - full access:
| Property | Value |
|----------|-------|
| Certificate CN | `CN=admin,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK` |
| Keystore | `/var/lib/conduktor/certs/admin.keystore.jks` |

**User1 account** - restricted access:
| Property | Value |
|----------|-------|
| Certificate CN | `CN=user1,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK` |
| Keystore | `/var/lib/conduktor/certs/user1.keystore.jks` |
| ACL | Can only READ topics prefixed with `click` |

### ACL Demo Results

The setup script demonstrates ACL enforcement with mTLS:

| User (Cert CN) | Action | Topic | Result | Reason |
|----------------|--------|-------|--------|--------|
| admin | CREATE | `click-stream` | ALLOWED | Admin is superuser |
| admin | WRITE | `click-stream` | ALLOWED | Admin is superuser |
| admin | READ | `click-stream` | ALLOWED | Admin is superuser |
| user1 | WRITE | `click-stream` | **DENIED** | No WRITE ACL for user1 |
| user1 | READ | `click-stream` | ALLOWED | Matches `click` prefix ACL |

## Using mTLS Certificates

### Copy Certificates from Container

```sh
# Create local directory
mkdir -p ./certs

# Copy certificates
docker cp conduktor_quick_start_in_a_single_container:/var/lib/conduktor/certs/admin.keystore.jks ./certs/
docker cp conduktor_quick_start_in_a_single_container:/var/lib/conduktor/certs/admin.truststore.jks ./certs/
docker cp conduktor_quick_start_in_a_single_container:/var/lib/conduktor/certs/user1.keystore.jks ./certs/
docker cp conduktor_quick_start_in_a_single_container:/var/lib/conduktor/certs/user1.truststore.jks ./certs/
docker cp conduktor_quick_start_in_a_single_container:/var/lib/conduktor/certs/ca.crt ./certs/
```

### Create Properties File

Create `admin.properties`:
```properties
security.protocol=SSL
ssl.truststore.location=./certs/admin.truststore.jks
ssl.truststore.password=conduktor
ssl.keystore.location=./certs/admin.keystore.jks
ssl.keystore.password=conduktor
ssl.key.password=conduktor
```

Create `user1.properties`:
```properties
security.protocol=SSL
ssl.truststore.location=./certs/user1.truststore.jks
ssl.truststore.password=conduktor
ssl.keystore.location=./certs/user1.keystore.jks
ssl.keystore.password=conduktor
ssl.key.password=conduktor
```

### Kafka CLI Examples

```sh
# List topics as admin
kafka-topics --bootstrap-server localhost:6969 \
  --command-config admin.properties --list

# Create a topic
kafka-topics --bootstrap-server localhost:6969 \
  --command-config admin.properties \
  --create --topic my-topic --partitions 3

# Produce messages
echo '{"message":"hello mTLS"}' | kafka-console-producer \
  --bootstrap-server localhost:6969 \
  --producer.config admin.properties \
  --topic my-topic

# Consume messages
kafka-console-consumer \
  --bootstrap-server localhost:6969 \
  --consumer.config admin.properties \
  --topic my-topic \
  --from-beginning --max-messages 1

# user1 can read from click.* topics
kafka-console-consumer \
  --bootstrap-server localhost:6969 \
  --consumer.config user1.properties \
  --topic click-stream \
  --group myconsumer-demo \
  --from-beginning --max-messages 1
```

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

### List Resources

```sh
conduktor get KafkaCluster
conduktor get VirtualCluster
conduktor get GatewayServiceAccount
```

## Certificate Details

All certificates are generated at container startup:

| Certificate | CN | Purpose |
|-------------|-----|---------|
| CA | `ca.conduktor.local` | Root CA for signing all certs |
| gateway | `gateway` | Gateway server certificate |
| admin | `admin` | Admin user mTLS authentication |
| user1 | `user1` | Restricted user mTLS authentication |
| client | `client` | Generic client certificate |
| console | `console` | Console service certificate |
| redpanda | `redpanda` | Redpanda certificate (unused - plaintext) |

Certificate properties:
- Algorithm: RSA
- Validity: 365 days
- Keystore password: `conduktor`
- Key password: `conduktor`
- Format: JKS (Java KeyStore) + PEM files

## Demo

[![asciicast](https://asciinema.org/a/BwMB9aeRhHC5kzkbFlFOpWCqd.svg)](https://asciinema.org/a/BwMB9aeRhHC5kzkbFlFOpWCqd)
