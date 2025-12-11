# Conduktor Quick Start (Single Container)

This repo builds and runs a single Docker image that bundles Conduktor Console, Gateway, Redpanda, and supporting services.

> [!WARNING]
> DO NOT DO THIS AT HOME.

## Quick Start

```sh
make all
```

This will build, run, generate certificates, and set up vClusters with mTLS automatically.

## Manual Build & Run

### Build

```sh
docker build . -t conduktor_quick_start_in_a_single_container
```

### Run

```sh
docker run -d --name conduktor_quick_start_in_a_single_container \
  -p 8080:8080 \
  -p 8888:8888 \
  -p 6969:6969 \
  -v $(PWD)/certs:/var/lib/conduktor/certs \
  conduktor_quick_start_in_a_single_container
```

### Setup Gateway

```sh
./setup_gateway.sh
```

## Architecture

### Step 1: Container Startup

When the container starts, it launches all internal services:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            Docker Container                             │
│                                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │   Redpanda   │  │   Gateway    │  │   Console    │  │  Schema     │  │
│  │    :9092     │  │ :6969  :8888 │  │    :8080     │  │  Registry   │  │
│  │   (Kafka)    │  │ :kafka :api  │  │    (UI)      │  │   :8081     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘  │
│         │                 │                 │                │          │
│         └─────────────────┴────────┬────────┴────────────────┘          │
│                                    │                                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  /var/lib/conduktor/certs/  (mounted from ./certs/)              │   │
│  │  - ca.crt, gateway.keystore.jks, demo-admin.keystore.jks, ...    │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### Step 2: Certificate Generation

Certificates are generated at container startup by `certs.sh`:

```
                    ┌─────────────────────┐
                    │   CA Certificate    │
                    │ ca.conduktor.local  │
                    └─────────┬───────────┘
                              │ signs
                    ┌─────────────────────┐
                    │                     │
                    ▼                     ▼
            ┌───────────────┐     ┌───────────────┐
            │  demo-admin   │     │demo-acl-admin │
            │ (client cert) │     │ (client cert) │
            │CN=demo-admin  │     │CN=demo-acl-   │
            │               │     │     admin     │
            └───────────────┘     └───────────────┘
                    │                     │
                    │                     │
                    ▼                     ▼
            ┌───────────────┐     ┌───────────────┐
            │demo-admin.    │     │demo-acl-admin.│
            │keystore.jks   │     │ keystore.jks  │
            │demo-admin.    │     │demo-acl-admin.│
            │truststore.jks │     │truststore.jks │
            └───────────────┘     └───────────────┘
                                          │
                                          ▼
                                  ┌───────────────┐
                                  │demo-acl-user  │
                                  │ (client cert) │
                                  │CN=demo-acl-   │
                                  │     user      │
                                  └───────────────┘
                                          │
                                          ▼
                                  ┌───────────────┐
                                  │demo-acl-user. │
                                  │ keystore.jks  │
                                  │demo-acl-user. │
                                  │truststore.jks │
                                  └───────────────┘
```

### Step 3: Gateway Setup (setup_gateway.sh)

The setup script creates Virtual Clusters and Service Accounts:

```
  Host Machine                           Container
 ┌─────────────────────────────────┐    ┌─────────────────────────────┐
 │                                 │    │                             │
 │  ./setup_gateway.sh             │    │  Gateway Admin API :8888    │
 │         │                       │    │         │                   │
 │         │ 1. Wait for ready     │    │         │                   │
 │         ├──────────────────────────▶ │         │                   │
 │         │                       │    │         │                   │
 │         │ 2. Create vCluster    │    │         ▼                   │
 │         │    "demo"             │    │  ┌─────────────────────┐    │
 │         ├──────────────────────────▶ │  │  demo vCluster      │    │
 │         │                       │    │  │  - ACL: disabled    │    │
 │         │ 3. Create vCluster    │    │  │  - superUser:       │    │
 │         │    "demo-acl"         │    │  │    demo-admin       │    │
 │         ├──────────────────────────▶ │  └─────────────────────┘    │
 │         │                       │    │                             │
 │         │ 4. Create Service     │    │  ┌─────────────────────┐    │
 │         │    Accounts           │    │  │  demo-acl vCluster  │    │
 │         ├──────────────────────────▶ │  │  - ACL: enabled     │    │
 │         │                       │    │  │  - superUser:       │    │
 │         │ 5. Register in        │    │  │    demo-acl-admin   │    │
 │         │    Console :8080      │    │  └─────────────────────┘    │
 │         ├──────────────────────────▶ │                             │
 │         │                       │    │                             │
 │         ▼                       │    │                             │
 │  demo-admin.properties          │    │                             │
 │  demo-acl-admin.properties      │    │                             │
 │  demo-acl-user.properties       │    │                             │
 │                                 │    │                             │
 └─────────────────────────────────┘    └─────────────────────────────┘
```

### Step 4: mTLS Authentication Flow

Client connects with certificate, Gateway extracts CN for identity:

```
  Client                              Gateway                         Redpanda
    │                                    │                                │
    │ 1. TLS Handshake                   │                                │
    │    (present certificate)           │                                │
    ├───────────────────────────────────▶│                                │
    │                                    │                                │
    │ 2. Gateway validates cert          │                                │
    │    against CA                      │                                │
    │◀───────────────────────────────────┤                                │
    │                                    │                                │
    │ 3. Extract CN from cert            │                                │
    │    CN=demo-admin                   │                                │
    │     ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│                                │
    │                                    │                                │
    │ 4. Map CN to Service Account       │                                │
    │    demo-admin → demo vCluster      │                                │
    │     ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│                                │
    │                                    │                                │
    │ 5. Kafka Request                   │ 6. Forward to                  │
    │    (encrypted)                     │    backing cluster             │
    ├───────────────────────────────────▶├────────────────────────────────▶
    │                                    │    (plaintext)                 │
    │                                    │                                │
    │                                    │ 7. Response                    │
    │ 8. Response                        │◀────────────────────────────────
    │◀───────────────────────────────────┤                                │
    │                                    │                                │
```

### Step 5: Virtual Cluster Routing

Different certificates route to different Virtual Clusters:

```
                                    ┌─────────────────────────────────────┐
                                    │           Gateway :6969             │
                                    │                                     │
┌──────────────────┐                │  ┌───────────────────────────────┐  │
│  demo-admin      │   mTLS         │  │     Service Account Mapping   │  │
│  certificate     │───────────────▶│  │                               │  │
│  CN=demo-admin   │                │  │  CN=demo-admin                │  │
└──────────────────┘                │  │    └──▶ demo vCluster         │  │
                                    │  │                               │  │
┌──────────────────┐                │  │  CN=demo-acl-admin            │  │
│  demo-acl-admin  │   mTLS         │  │    └──▶ demo-acl vCluster     │  │
│  certificate     │───────────────▶│  │                               │  │
│  CN=demo-acl-    │                │  │  CN=demo-acl-user             │  │
│       admin      │                │  │    └──▶ demo-acl vCluster     │  │
└──────────────────┘                │  │                               │  │
                                    │  └───────────────────────────────┘  │
┌──────────────────┐                │                                     │
│  demo-acl-user   │   mTLS         │        ┌─────────┐  ┌─────────┐     │
│  certificate     │───────────────▶│        │  demo   │  │demo-acl │     │
│  CN=demo-acl-    │                │        │vCluster │  │vCluster │     │
│       user       │                │        │         │  │  +ACL   │     │
└──────────────────┘                │        └────┬────┘  └────┬────┘     │
                                    │             │            │          │
                                    │             └─────┬──────┘          │
                                    │                   │                 │
                                    │                   ▼                 │
                                    │           ┌─────────────┐           │
                                    │           │  Redpanda   │           │
                                    │           │    :9092    │           │
                                    │           └─────────────┘           │
                                    └─────────────────────────────────────┘
```

### Step 6: ACL Enforcement (demo-acl vCluster)

The `demo-acl` vCluster enforces ACLs based on Service Account:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         demo-acl vCluster                               │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    Service Accounts                             │    │
│  │                                                                 │    │
│  │  ┌─────────────────────────┐    ┌─────────────────────────────┐ │    │
│  │  │    demo-acl-admin       │    │      demo-acl-user          │ │    │
│  │  │    (superUser)          │    │      (restricted)           │ │    │
│  │  │                         │    │                             │ │    │
│  │  │  ✓ All operations       │    │  ACLs:                      │ │    │
│  │  │  ✓ All topics           │    │  ✓ READ topic:click*        │ │    │
│  │  │  ✓ No ACL checks        │    │  ✓ READ group:myconsumer-*  │ │    │
│  │  │                         │    │  ✗ Everything else          │ │    │
│  │  └─────────────────────────┘    └─────────────────────────────┘ │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                     ACL Enforcement Examples                    │    │
│  │                                                                 │    │
│  │   demo-acl-admin                   demo-acl-user                │    │
│  │   ──────────────                   ──────────────               │    │
│  │   CREATE  click-stream  ──▶ ✓       CREATE click-stream  ──▶ ✗  │    │
│  │   PRODUCE click-stream  ──▶ ✓       WRITE  click-stream  ──▶ ✗  │    │
│  │   READ    click-stream  ──▶ ✓       READ   click-stream  ──▶ ✓  │    │
│  │                                                                 │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

### Overall System View

```
┌─────────────────────────────────────────-----------------------------─--─────┐
│                              Host Machine                                    │
│                                                                              │
│   ./certs/                         ┌──────────────────────────────────--───┐ │
│   ├── ca.crt                       │         Docker Container              │ │
│   ├── demo-admin.keystore.jks      │                                       │ │
│   ├── demo-acl-admin.keystore.jks  │  :8080 ──▶ Console (UI)               │ │
│   └── demo-acl-user.keystore.jks   │                                       │ │
│            │                       │  :8888 ──▶ Gateway Admin API (HTTP)   │ │
│            │  volume mount         │                                       │ │
│            └──────────────────────▶│  :6969 ──▶ Gateway Kafka (Kafka mTLS) │ │
│                                    │               │                       │ │
│   demo-admin.properties            │               ▼                       │ │
│   demo-acl-admin.properties        │         ┌──────────┐ ┌───────────┐    │ │
│   demo-acl-user.properties         │         │  demo    │ │ demo-acl  │    │ │
│            │                       │         │ vCluster │ │ vCluster  │    │ │
│            │                       │         └────┬─────┘ └─────┬─────┘    │ │
│            │                       │              │             │          │ │
│            │                       │              └──────┬──────┘          │ │
│            │                       │               Kafka │ Plaintext       │ │
│            │                       │                     ▼                 │ │
│            │                       │              ┌────────────┐           │ │
│            ▼                       │              │  Redpanda  │           │ │
│   kafka-topics --bootstrap-server  │              │   :9092    │           │ │
│     localhost:6969                 │              └────────────┘           │ │
│     --command-config               │                                       │ │
│     demo-admin.properties          └────────────────────────────────────--─┘ │
│                                                                              │
└───────────────────────────────────────────────────────────────────────────--─┘
```

### Summary

- **Gateway** exposes mTLS to kafka clients on port 6969
- **Gateway** connects to Redpanda via plaintext internally
- **Console** connects to Redpanda directly via plaintext
- **Console** connects to Gateway HTTP admin API on port 8888
- **Console** connects to Gateway mtls Kafka on port 6969
- **Schema Registry** is HTTP (plaintext)
- **Certificate CN** determines which vCluster and Service Account is used
- **ACLs** are enforced per-vCluster (only `demo-acl` has ACL enabled)

## Makefile Targets

| Target | Description |
|--------|-------------|
| `make all` | Build, run, copy certs, and setup (default) |
| `make build` | Build Docker image |
| `make run` | Run container |
| `make stop` | Stop container |
| `make rm` | Stop and remove container |
| `make clean` | Remove container, image, and local certs |
| `make logs` | Follow container logs |
| `make setup-logs` | View setup script logs |
| `make setup` | Run setup_gateway.sh |
| `make certs` | Copy certificates from container |
| `make status` | Show connection information |
| `make test` | Test mTLS connection with kafka-topics |
| `make help` | Show help |

## Automatic Setup

The `setup_gateway.sh` script:
1. Waits for Console and Gateway to be ready
2. Creates two Virtual Clusters in Gateway with mTLS authentication
3. Creates service accounts mapped to certificate CNs
4. Registers the vClusters in Console
5. Generates properties files for Kafka CLI
6. Runs an ACL demonstration with mTLS

### Check Setup Progress

```sh
make setup-logs
```

### View Generated Certificates

```sh
ls -la ./certs/
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
| Certificate | `./certs/demo-admin.keystore.jks` |
| Truststore | `./certs/demo-admin.truststore.jks` |
| Password | `conduktor` |
| Properties file | `demo-admin.properties` |

#### 2. `demo-acl` (ACL enabled)

Access controlled by ACL rules.

**demo-acl-admin** - full access (superuser):
| Property | Value |
|----------|-------|
| Certificate CN | `CN=demo-acl-admin,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK` |
| Keystore | `./certs/demo-acl-admin.keystore.jks` |
| Properties file | `demo-acl-admin.properties` |

**demo-acl-user** - restricted access:
| Property | Value |
|----------|-------|
| Certificate CN | `CN=demo-acl-user,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK` |
| Keystore | `./certs/demo-acl-user.keystore.jks` |
| Properties file | `demo-acl-user.properties` |
| ACL | Can only READ topics prefixed with `click` |

### ACL Demo Results

The setup script demonstrates ACL enforcement with mTLS on the `demo-acl` vCluster:

| User | Action | Topic | Result | Reason |
|------|--------|-------|--------|--------|
| demo-acl-admin | CREATE | `click-stream` | ALLOWED | Admin is superuser |
| demo-acl-admin | WRITE | `click-stream` | ALLOWED | Admin is superuser |
| demo-acl-admin | READ | `click-stream` | ALLOWED | Admin is superuser |
| demo-acl-user | WRITE | `click-stream` | **DENIED** | No WRITE ACL |
| demo-acl-user | READ | `click-stream` | ALLOWED | Matches `click` prefix ACL |

## Using mTLS Certificates

Certificates are mounted via volume to `./certs/` and properties files are generated automatically by `setup_gateway.sh`.

### Generated Properties Files

After running `make all` or `./setup_gateway.sh`, these files are created:

| File | User | vCluster |
|------|------|----------|
| `demo-admin.properties` | demo-admin | demo |
| `demo-acl-admin.properties` | demo-acl-admin | demo-acl |
| `demo-acl-user.properties` | demo-acl-user | demo-acl |

Example properties file content:
```properties
security.protocol=SSL
ssl.truststore.location=certs/demo-admin.truststore.jks
ssl.truststore.password=conduktor
ssl.keystore.location=certs/demo-admin.keystore.jks
ssl.keystore.password=conduktor
ssl.key.password=conduktor
```

### Kafka CLI Examples

```sh
# List topics on demo vCluster
kafka-topics --bootstrap-server localhost:6969 \
  --command-config demo-admin.properties --list

# Create a topic on demo vCluster
kafka-topics --bootstrap-server localhost:6969 \
  --command-config demo-admin.properties \
  --create --topic my-topic --partitions 3

# Produce messages
echo '{"message":"hello mTLS"}' | kafka-console-producer \
  --bootstrap-server localhost:6969 \
  --producer.config demo-admin.properties \
  --topic my-topic

# Consume messages
kafka-console-consumer \
  --bootstrap-server localhost:6969 \
  --consumer.config demo-admin.properties \
  --topic my-topic \
  --from-beginning --max-messages 1

# On demo-acl vCluster: demo-acl-user can read from click.* topics
kafka-console-consumer \
  --bootstrap-server localhost:6969 \
  --consumer.config demo-acl-user.properties \
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

Certificates are generated at container startup and mounted to `./certs/`:

| Certificate | CN | Purpose |
|-------------|-----|---------|
| CA | `ca.conduktor.local` | Root CA for signing all certs |
| gateway | `gateway` | Gateway server certificate |
| demo-admin | `demo-admin` | Admin for demo vCluster |
| demo-acl-admin | `demo-acl-admin` | Admin for demo-acl vCluster |
| demo-acl-user | `demo-acl-user` | Restricted user for demo-acl vCluster |
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
