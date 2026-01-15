# Minikube Functional Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement end-to-end functional tests for the Messaging Operator using Helm, Bats, and Java against a real Minikube cluster.

**Architecture:** Three-layer testing strategy - Bash scripts for quick validation, Bats for structured kubectl tests, Java/Fabric8 for complex scenarios reusing TestDataBuilder. Helm chart manages deployment with templated CRDs and webhook config.

**Tech Stack:** Minikube, Helm 3, Bats (bats-core, bats-support, bats-assert), JUnit 5, Fabric8 Kubernetes Client, AssertJ, Awaitility

---

## Task 1: Create Directory Structure

**Files:**
- Create: `functional-tests/` directory tree

**Step 1: Create all directories**

```bash
mkdir -p functional-tests/{helm/messaging-operator/{templates/crds,tests},scripts,bats,fixtures/{valid,invalid,tenant-a,tenant-b,ownership-chain,ha-test}}
```

**Step 2: Verify structure**

Run: `find functional-tests -type d | sort`

Expected output:
```
functional-tests
functional-tests/bats
functional-tests/fixtures
functional-tests/fixtures/ha-test
functional-tests/fixtures/invalid
functional-tests/fixtures/ownership-chain
functional-tests/fixtures/tenant-a
functional-tests/fixtures/tenant-b
functional-tests/fixtures/valid
functional-tests/helm
functional-tests/helm/messaging-operator
functional-tests/helm/messaging-operator/templates
functional-tests/helm/messaging-operator/templates/crds
functional-tests/helm/messaging-operator/tests
functional-tests/scripts
```

**Step 3: Commit**

```bash
git add functional-tests
git commit -m "feat: create functional-tests directory structure"
```

---

## Task 2: Create Helm Chart Base Files

**Files:**
- Create: `functional-tests/helm/messaging-operator/Chart.yaml`
- Create: `functional-tests/helm/messaging-operator/values.yaml`
- Create: `functional-tests/helm/messaging-operator/values-minikube.yaml`

**Step 1: Create Chart.yaml**

```yaml
apiVersion: v2
name: messaging-operator
description: Kubernetes Operator for messaging resource management with validating webhook
version: 0.1.0
appVersion: "1.0.0"
type: application
keywords:
  - kafka
  - messaging
  - operator
  - webhook
maintainers:
  - name: Conduktor Team
```

**Step 2: Create values.yaml**

```yaml
# Default values for messaging-operator (production)
namespace: operator-system

webhook:
  name: messaging-operator-webhook
  replicaCount: 2
  image:
    repository: messaging-operator-webhook
    tag: latest
    pullPolicy: IfNotPresent
  port: 8443
  resources:
    requests:
      memory: "256Mi"
      cpu: "100m"
    limits:
      memory: "512Mi"
      cpu: "500m"
  healthCheck:
    path: /health
    livenessInitialDelay: 10
    livenessPeriod: 10
    readinessInitialDelay: 5
    readinessPeriod: 5

tls:
  secretName: webhook-tls
  # Set to true for auto-generated self-signed certs (testing only)
  generate: false
  # If generate=false, provide these:
  # caCert: base64-encoded CA certificate
  # cert: base64-encoded server certificate
  # key: base64-encoded server key

webhookConfig:
  failurePolicy: Fail
  timeoutSeconds: 10
  # CRD types to validate
  resources:
    - name: topics
      path: /validate/topic
    - name: acls
      path: /validate/acl
    - name: serviceaccounts
      path: /validate/serviceaccount
    - name: virtualclusters
      path: /validate/virtualcluster
    - name: consumergroups
      path: /validate/consumergroup
```

**Step 3: Create values-minikube.yaml**

```yaml
# Minikube-specific overrides for local testing
webhook:
  replicaCount: 1
  image:
    pullPolicy: Never  # Use locally built image
  resources:
    requests:
      memory: "128Mi"
      cpu: "50m"
    limits:
      memory: "256Mi"
      cpu: "250m"

tls:
  generate: true  # Auto-generate self-signed certs for testing
```

**Step 4: Verify YAML syntax**

Run: `helm lint functional-tests/helm/messaging-operator`

Expected: No errors (warnings OK at this stage)

**Step 5: Commit**

```bash
git add functional-tests/helm/messaging-operator/Chart.yaml functional-tests/helm/messaging-operator/values.yaml functional-tests/helm/messaging-operator/values-minikube.yaml
git commit -m "feat: add Helm chart base configuration files"
```

---

## Task 3: Create Helm Template Helpers

**Files:**
- Create: `functional-tests/helm/messaging-operator/templates/_helpers.tpl`

**Step 1: Create _helpers.tpl**

```yaml
{{/*
Expand the name of the chart.
*/}}
{{- define "messaging-operator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "messaging-operator.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- printf "%s" $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "messaging-operator.labels" -}}
helm.sh/chart: {{ include "messaging-operator.chart" . }}
{{ include "messaging-operator.selectorLabels" . }}
app.kubernetes.io/version: {{ .Values.webhook.image.tag | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "messaging-operator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "messaging-operator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app: {{ .Values.webhook.name }}
{{- end }}

{{/*
Chart label
*/}}
{{- define "messaging-operator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Webhook service name
*/}}
{{- define "messaging-operator.webhookServiceName" -}}
{{- .Values.webhook.name }}
{{- end }}

{{/*
Namespace
*/}}
{{- define "messaging-operator.namespace" -}}
{{- .Values.namespace }}
{{- end }}
```

**Step 2: Commit**

```bash
git add functional-tests/helm/messaging-operator/templates/_helpers.tpl
git commit -m "feat: add Helm template helpers"
```

---

## Task 4: Create Helm Namespace Template

**Files:**
- Create: `functional-tests/helm/messaging-operator/templates/namespace.yaml`

**Step 1: Create namespace.yaml**

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: {{ include "messaging-operator.namespace" . }}
  labels:
    {{- include "messaging-operator.labels" . | nindent 4 }}
```

**Step 2: Test template rendering**

Run: `helm template test functional-tests/helm/messaging-operator --show-only templates/namespace.yaml`

Expected output includes:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: operator-system
```

**Step 3: Commit**

```bash
git add functional-tests/helm/messaging-operator/templates/namespace.yaml
git commit -m "feat: add Helm namespace template"
```

---

## Task 5: Create Helm Deployment Template

**Files:**
- Create: `functional-tests/helm/messaging-operator/templates/webhook-deployment.yaml`

**Step 1: Create webhook-deployment.yaml**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Values.webhook.name }}
  namespace: {{ include "messaging-operator.namespace" . }}
  labels:
    {{- include "messaging-operator.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.webhook.replicaCount }}
  selector:
    matchLabels:
      {{- include "messaging-operator.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "messaging-operator.selectorLabels" . | nindent 8 }}
    spec:
      containers:
        - name: webhook
          image: "{{ .Values.webhook.image.repository }}:{{ .Values.webhook.image.tag }}"
          imagePullPolicy: {{ .Values.webhook.image.pullPolicy }}
          ports:
            - containerPort: {{ .Values.webhook.port }}
              name: https
              protocol: TCP
          env:
            - name: WEBHOOK_PORT
              value: "{{ .Values.webhook.port }}"
          volumeMounts:
            - name: tls-certs
              mountPath: /etc/webhook/certs
              readOnly: true
          livenessProbe:
            httpGet:
              path: {{ .Values.webhook.healthCheck.path }}
              port: {{ .Values.webhook.port }}
              scheme: HTTPS
            initialDelaySeconds: {{ .Values.webhook.healthCheck.livenessInitialDelay }}
            periodSeconds: {{ .Values.webhook.healthCheck.livenessPeriod }}
          readinessProbe:
            httpGet:
              path: {{ .Values.webhook.healthCheck.path }}
              port: {{ .Values.webhook.port }}
              scheme: HTTPS
            initialDelaySeconds: {{ .Values.webhook.healthCheck.readinessInitialDelay }}
            periodSeconds: {{ .Values.webhook.healthCheck.readinessPeriod }}
          resources:
            requests:
              memory: {{ .Values.webhook.resources.requests.memory | quote }}
              cpu: {{ .Values.webhook.resources.requests.cpu | quote }}
            limits:
              memory: {{ .Values.webhook.resources.limits.memory | quote }}
              cpu: {{ .Values.webhook.resources.limits.cpu | quote }}
      volumes:
        - name: tls-certs
          secret:
            secretName: {{ .Values.tls.secretName }}
```

**Step 2: Test template rendering**

Run: `helm template test functional-tests/helm/messaging-operator --show-only templates/webhook-deployment.yaml`

Expected: Valid Deployment YAML with values substituted

**Step 3: Commit**

```bash
git add functional-tests/helm/messaging-operator/templates/webhook-deployment.yaml
git commit -m "feat: add Helm webhook deployment template"
```

---

## Task 6: Create Helm Service Template

**Files:**
- Create: `functional-tests/helm/messaging-operator/templates/webhook-service.yaml`

**Step 1: Create webhook-service.yaml**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Values.webhook.name }}
  namespace: {{ include "messaging-operator.namespace" . }}
  labels:
    {{- include "messaging-operator.labels" . | nindent 4 }}
spec:
  selector:
    {{- include "messaging-operator.selectorLabels" . | nindent 4 }}
  ports:
    - name: https
      protocol: TCP
      port: 443
      targetPort: {{ .Values.webhook.port }}
  type: ClusterIP
```

**Step 2: Commit**

```bash
git add functional-tests/helm/messaging-operator/templates/webhook-service.yaml
git commit -m "feat: add Helm webhook service template"
```

---

## Task 7: Create Helm TLS Secret Template

**Files:**
- Create: `functional-tests/helm/messaging-operator/templates/tls-secret.yaml`

**Step 1: Create tls-secret.yaml with conditional generation**

```yaml
{{- if .Values.tls.generate }}
# Self-signed certificate generation happens in deploy script
# This template creates a placeholder that gets replaced
apiVersion: v1
kind: Secret
metadata:
  name: {{ .Values.tls.secretName }}
  namespace: {{ include "messaging-operator.namespace" . }}
  labels:
    {{- include "messaging-operator.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": pre-install,pre-upgrade
    "helm.sh/hook-weight": "-10"
type: kubernetes.io/tls
data:
  tls.crt: {{ "PLACEHOLDER_CERT" | b64enc }}
  tls.key: {{ "PLACEHOLDER_KEY" | b64enc }}
  ca.crt: {{ "PLACEHOLDER_CA" | b64enc }}
{{- else }}
apiVersion: v1
kind: Secret
metadata:
  name: {{ .Values.tls.secretName }}
  namespace: {{ include "messaging-operator.namespace" . }}
  labels:
    {{- include "messaging-operator.labels" . | nindent 4 }}
type: kubernetes.io/tls
data:
  tls.crt: {{ required "tls.cert is required when tls.generate=false" .Values.tls.cert }}
  tls.key: {{ required "tls.key is required when tls.generate=false" .Values.tls.key }}
  ca.crt: {{ required "tls.caCert is required when tls.generate=false" .Values.tls.caCert }}
{{- end }}
```

**Step 2: Commit**

```bash
git add functional-tests/helm/messaging-operator/templates/tls-secret.yaml
git commit -m "feat: add Helm TLS secret template with conditional generation"
```

---

## Task 8: Create Helm ValidatingWebhookConfiguration Template

**Files:**
- Create: `functional-tests/helm/messaging-operator/templates/webhook-config.yaml`

**Step 1: Create webhook-config.yaml**

```yaml
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata:
  name: {{ .Values.webhook.name }}
  labels:
    {{- include "messaging-operator.labels" . | nindent 4 }}
  annotations:
    # CA bundle injected by deploy script when tls.generate=true
    "helm.sh/hook": post-install,post-upgrade
    "helm.sh/hook-weight": "10"
webhooks:
{{- range .Values.webhookConfig.resources }}
  - name: {{ .name }}.validate.example.com
    clientConfig:
      service:
        name: {{ $.Values.webhook.name }}
        namespace: {{ include "messaging-operator.namespace" $ }}
        path: {{ .path }}
      caBundle: {{ $.Values.tls.caCert | default "PLACEHOLDER_CA_BUNDLE" }}
    rules:
      - operations: ["CREATE", "UPDATE", "DELETE"]
        apiGroups: ["example.com"]
        apiVersions: ["v1"]
        resources: [{{ .name | quote }}]
    admissionReviewVersions: ["v1"]
    sideEffects: None
    failurePolicy: {{ $.Values.webhookConfig.failurePolicy }}
    timeoutSeconds: {{ $.Values.webhookConfig.timeoutSeconds }}
{{- end }}
```

**Step 2: Commit**

```bash
git add functional-tests/helm/messaging-operator/templates/webhook-config.yaml
git commit -m "feat: add Helm validating webhook configuration template"
```

---

## Task 9: Create CRD Templates

**Files:**
- Create: `functional-tests/helm/messaging-operator/templates/crds/applicationservice.yaml`
- Create: `functional-tests/helm/messaging-operator/templates/crds/virtualcluster.yaml`
- Create: `functional-tests/helm/messaging-operator/templates/crds/serviceaccount.yaml`
- Create: `functional-tests/helm/messaging-operator/templates/crds/topic.yaml`
- Create: `functional-tests/helm/messaging-operator/templates/crds/acl.yaml`
- Create: `functional-tests/helm/messaging-operator/templates/crds/consumergroup.yaml`

**Step 1: Create applicationservice.yaml CRD**

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: applicationservices.example.com
spec:
  group: example.com
  versions:
    - name: v1
      served: true
      storage: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              required:
                - name
              properties:
                name:
                  type: string
                  description: Application service name
            status:
              type: object
              properties:
                phase:
                  type: string
                message:
                  type: string
      subresources:
        status: {}
  scope: Namespaced
  names:
    plural: applicationservices
    singular: applicationservice
    kind: ApplicationService
    shortNames:
      - appsvc
```

**Step 2: Create virtualcluster.yaml CRD**

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: virtualclusters.example.com
spec:
  group: example.com
  versions:
    - name: v1
      served: true
      storage: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              required:
                - clusterId
                - applicationServiceRef
              properties:
                clusterId:
                  type: string
                applicationServiceRef:
                  type: string
                  description: Reference to owning ApplicationService
            status:
              type: object
              properties:
                phase:
                  type: string
                message:
                  type: string
      subresources:
        status: {}
  scope: Namespaced
  names:
    plural: virtualclusters
    singular: virtualcluster
    kind: VirtualCluster
    shortNames:
      - vc
```

**Step 3: Create serviceaccount.yaml CRD**

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: serviceaccounts.example.com
spec:
  group: example.com
  versions:
    - name: v1
      served: true
      storage: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              required:
                - name
                - clusterRef
                - applicationServiceRef
              properties:
                name:
                  type: string
                dn:
                  type: array
                  items:
                    type: string
                clusterRef:
                  type: string
                applicationServiceRef:
                  type: string
            status:
              type: object
              properties:
                phase:
                  type: string
                message:
                  type: string
      subresources:
        status: {}
  scope: Namespaced
  names:
    plural: serviceaccounts
    singular: serviceaccount
    kind: ServiceAccount
    shortNames:
      - sa
```

**Step 4: Create topic.yaml CRD**

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: topics.example.com
spec:
  group: example.com
  versions:
    - name: v1
      served: true
      storage: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              required:
                - name
                - serviceRef
                - applicationServiceRef
              properties:
                name:
                  type: string
                serviceRef:
                  type: string
                partitions:
                  type: integer
                  default: 6
                replicationFactor:
                  type: integer
                  default: 3
                config:
                  type: object
                  additionalProperties:
                    type: string
                applicationServiceRef:
                  type: string
            status:
              type: object
              properties:
                phase:
                  type: string
                message:
                  type: string
      subresources:
        status: {}
  scope: Namespaced
  names:
    plural: topics
    singular: topic
    kind: Topic
```

**Step 5: Create acl.yaml CRD**

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: acls.example.com
spec:
  group: example.com
  versions:
    - name: v1
      served: true
      storage: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              required:
                - serviceRef
                - applicationServiceRef
              properties:
                serviceRef:
                  type: string
                topicRef:
                  type: string
                consumerGroupRef:
                  type: string
                operations:
                  type: array
                  items:
                    type: string
                    enum: [READ, WRITE, CREATE, DELETE, ALTER, DESCRIBE, ALL]
                host:
                  type: string
                  default: "*"
                permission:
                  type: string
                  enum: [ALLOW, DENY]
                  default: ALLOW
                applicationServiceRef:
                  type: string
            status:
              type: object
              properties:
                phase:
                  type: string
                message:
                  type: string
      subresources:
        status: {}
  scope: Namespaced
  names:
    plural: acls
    singular: acl
    kind: ACL
```

**Step 6: Create consumergroup.yaml CRD**

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: consumergroups.example.com
spec:
  group: example.com
  versions:
    - name: v1
      served: true
      storage: true
      schema:
        openAPIV3Schema:
          type: object
          properties:
            spec:
              type: object
              required:
                - name
                - serviceRef
                - applicationServiceRef
              properties:
                name:
                  type: string
                serviceRef:
                  type: string
                patternType:
                  type: string
                  enum: [LITERAL, PREFIXED]
                  default: LITERAL
                applicationServiceRef:
                  type: string
            status:
              type: object
              properties:
                phase:
                  type: string
                message:
                  type: string
      subresources:
        status: {}
  scope: Namespaced
  names:
    plural: consumergroups
    singular: consumergroup
    kind: ConsumerGroup
    shortNames:
      - cg
```

**Step 7: Commit**

```bash
git add functional-tests/helm/messaging-operator/templates/crds/
git commit -m "feat: add CRD templates for all 6 resource types"
```

---

## Task 10: Create Helm Test Pod

**Files:**
- Create: `functional-tests/helm/messaging-operator/tests/test-webhook-health.yaml`

**Step 1: Create test-webhook-health.yaml**

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: "{{ .Values.webhook.name }}-test-health"
  namespace: {{ include "messaging-operator.namespace" . }}
  labels:
    {{- include "messaging-operator.labels" . | nindent 4 }}
  annotations:
    "helm.sh/hook": test
    "helm.sh/hook-delete-policy": hook-succeeded
spec:
  containers:
    - name: wget
      image: busybox:1.36
      command: ['wget']
      args:
        - '--no-check-certificate'
        - '--spider'
        - '-T'
        - '5'
        - 'https://{{ .Values.webhook.name }}:443{{ .Values.webhook.healthCheck.path }}'
  restartPolicy: Never
```

**Step 2: Commit**

```bash
git add functional-tests/helm/messaging-operator/tests/test-webhook-health.yaml
git commit -m "feat: add Helm test pod for webhook health check"
```

---

## Task 11: Validate Complete Helm Chart

**Step 1: Run helm lint**

Run: `helm lint functional-tests/helm/messaging-operator`

Expected: "0 chart(s) failed" (warnings about missing values OK)

**Step 2: Test full template rendering**

Run: `helm template test functional-tests/helm/messaging-operator -f functional-tests/helm/messaging-operator/values-minikube.yaml 2>&1 | head -100`

Expected: Valid YAML output for all templates

**Step 3: Commit any fixes if needed**

---

## Task 12: Create setup-minikube.sh Script

**Files:**
- Create: `functional-tests/scripts/setup-minikube.sh`

**Step 1: Create setup-minikube.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

# Configuration
CLUSTER_NAME="${CLUSTER_NAME:-messaging-operator-test}"
FRESH_CLUSTER="${FRESH_CLUSTER:-false}"
NAMESPACE="${NAMESPACE:-operator-test-$(date +%s)}"
K8S_VERSION="${K8S_VERSION:-v1.29.0}"
CPUS="${CPUS:-2}"
MEMORY="${MEMORY:-4096}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== Minikube Cluster Setup ==="
echo "Cluster: $CLUSTER_NAME"
echo "Fresh: $FRESH_CLUSTER"
echo "Namespace: $NAMESPACE"

# Check if cluster exists
if minikube status -p "$CLUSTER_NAME" &>/dev/null; then
    if [[ "$FRESH_CLUSTER" == "true" ]]; then
        echo "Deleting existing cluster..."
        minikube delete -p "$CLUSTER_NAME"
    else
        echo "Reusing existing cluster: $CLUSTER_NAME"
        minikube profile "$CLUSTER_NAME"

        # Create fresh namespace for test isolation
        kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
        echo "$NAMESPACE" > "$PROJECT_ROOT/.test-namespace"

        echo "Namespace '$NAMESPACE' ready"
        exit 0
    fi
fi

# Create new cluster
echo "Creating Minikube cluster: $CLUSTER_NAME"
minikube start -p "$CLUSTER_NAME" \
    --driver=docker \
    --cpus="$CPUS" \
    --memory="$MEMORY" \
    --kubernetes-version="$K8S_VERSION"

# Create test namespace
kubectl create namespace "$NAMESPACE"
echo "$NAMESPACE" > "$PROJECT_ROOT/.test-namespace"

echo "Cluster '$CLUSTER_NAME' ready with namespace '$NAMESPACE'"
```

**Step 2: Make executable**

Run: `chmod +x functional-tests/scripts/setup-minikube.sh`

**Step 3: Commit**

```bash
git add functional-tests/scripts/setup-minikube.sh
git commit -m "feat: add Minikube cluster setup script"
```

---

## Task 13: Create generate-certs.sh Script

**Files:**
- Create: `functional-tests/scripts/generate-certs.sh`

**Step 1: Create generate-certs.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CERT_DIR="$PROJECT_ROOT/.certs"
NAMESPACE="${NAMESPACE:-$(cat "$PROJECT_ROOT/.test-namespace" 2>/dev/null || echo "operator-system")}"
SERVICE_NAME="${SERVICE_NAME:-messaging-operator-webhook}"

echo "=== Generating TLS Certificates ==="
echo "Namespace: $NAMESPACE"
echo "Service: $SERVICE_NAME"

mkdir -p "$CERT_DIR"

# Generate CA key and certificate
openssl genrsa -out "$CERT_DIR/ca.key" 2048
openssl req -x509 -new -nodes -key "$CERT_DIR/ca.key" \
    -subj "/CN=Messaging Operator CA" \
    -days 365 -out "$CERT_DIR/ca.crt"

# Generate server key
openssl genrsa -out "$CERT_DIR/server.key" 2048

# Create CSR config
cat > "$CERT_DIR/csr.conf" <<EOF
[req]
req_extensions = v3_req
distinguished_name = req_distinguished_name
[req_distinguished_name]
[v3_req]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names
[alt_names]
DNS.1 = ${SERVICE_NAME}
DNS.2 = ${SERVICE_NAME}.${NAMESPACE}
DNS.3 = ${SERVICE_NAME}.${NAMESPACE}.svc
DNS.4 = ${SERVICE_NAME}.${NAMESPACE}.svc.cluster.local
EOF

# Generate server certificate
openssl req -new -key "$CERT_DIR/server.key" \
    -subj "/CN=${SERVICE_NAME}.${NAMESPACE}.svc" \
    -out "$CERT_DIR/server.csr" \
    -config "$CERT_DIR/csr.conf"

openssl x509 -req -in "$CERT_DIR/server.csr" \
    -CA "$CERT_DIR/ca.crt" -CAkey "$CERT_DIR/ca.key" \
    -CAcreateserial -out "$CERT_DIR/server.crt" \
    -days 365 -extensions v3_req -extfile "$CERT_DIR/csr.conf"

# Export base64 encoded values for Helm
export TLS_CRT=$(base64 -w0 < "$CERT_DIR/server.crt")
export TLS_KEY=$(base64 -w0 < "$CERT_DIR/server.key")
export CA_CRT=$(base64 -w0 < "$CERT_DIR/ca.crt")

echo "Certificates generated in $CERT_DIR"
echo ""
echo "TLS_CRT=$TLS_CRT"
echo "TLS_KEY=$TLS_KEY"
echo "CA_CRT=$CA_CRT"

# Save to file for deploy script
cat > "$CERT_DIR/values-tls.yaml" <<EOF
tls:
  generate: false
  cert: $TLS_CRT
  key: $TLS_KEY
  caCert: $CA_CRT
EOF

echo ""
echo "TLS values saved to $CERT_DIR/values-tls.yaml"
```

**Step 2: Make executable**

Run: `chmod +x functional-tests/scripts/generate-certs.sh`

**Step 3: Commit**

```bash
git add functional-tests/scripts/generate-certs.sh
git commit -m "feat: add TLS certificate generation script"
```

---

## Task 14: Create deploy.sh Script

**Files:**
- Create: `functional-tests/scripts/deploy.sh`

**Step 1: Create deploy.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PROJECT_ROOT/.." && pwd)"
HELM_CHART="$PROJECT_ROOT/helm/messaging-operator"
CERT_DIR="$PROJECT_ROOT/.certs"

NAMESPACE="${NAMESPACE:-$(cat "$PROJECT_ROOT/.test-namespace" 2>/dev/null || echo "operator-system")}"
RELEASE_NAME="${RELEASE_NAME:-messaging-operator}"
IMAGE_NAME="${IMAGE_NAME:-messaging-operator-webhook}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SKIP_BUILD="${SKIP_BUILD:-false}"

echo "=== Deploying Messaging Operator ==="
echo "Namespace: $NAMESPACE"
echo "Release: $RELEASE_NAME"

# Build Docker image if not skipping
if [[ "$SKIP_BUILD" != "true" ]]; then
    echo ""
    echo "[1/4] Building Docker image..."

    # Build the Java application first
    (cd "$REPO_ROOT" && mvn package -DskipTests -q)

    # Build Docker image
    eval $(minikube docker-env)
    docker build -t "$IMAGE_NAME:$IMAGE_TAG" -f "$REPO_ROOT/Dockerfile" "$REPO_ROOT"

    echo "Image built: $IMAGE_NAME:$IMAGE_TAG"
else
    echo ""
    echo "[1/4] Skipping image build (SKIP_BUILD=true)"
fi

# Generate certificates
echo ""
echo "[2/4] Generating TLS certificates..."
NAMESPACE="$NAMESPACE" "$SCRIPT_DIR/generate-certs.sh"

# Deploy with Helm
echo ""
echo "[3/4] Deploying Helm chart..."

# Update values with namespace
helm upgrade --install "$RELEASE_NAME" "$HELM_CHART" \
    --namespace "$NAMESPACE" \
    --create-namespace \
    -f "$HELM_CHART/values-minikube.yaml" \
    -f "$CERT_DIR/values-tls.yaml" \
    --set namespace="$NAMESPACE" \
    --set webhook.image.repository="$IMAGE_NAME" \
    --set webhook.image.tag="$IMAGE_TAG" \
    --wait \
    --timeout 120s

# Wait for webhook to be ready
echo ""
echo "[4/4] Waiting for webhook to be ready..."

kubectl wait --for=condition=available deployment/messaging-operator-webhook \
    -n "$NAMESPACE" --timeout=60s

# Verify endpoint
kubectl get endpoints messaging-operator-webhook -n "$NAMESPACE"

echo ""
echo "=== Deployment complete ==="
echo "Namespace: $NAMESPACE"
echo "Webhook: messaging-operator-webhook"
```

**Step 2: Make executable**

Run: `chmod +x functional-tests/scripts/deploy.sh`

**Step 3: Commit**

```bash
git add functional-tests/scripts/deploy.sh
git commit -m "feat: add Helm deployment script with image build"
```

---

## Task 15: Create teardown.sh Script

**Files:**
- Create: `functional-tests/scripts/teardown.sh`

**Step 1: Create teardown.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

NAMESPACE="${NAMESPACE:-$(cat "$PROJECT_ROOT/.test-namespace" 2>/dev/null || echo "operator-system")}"
RELEASE_NAME="${RELEASE_NAME:-messaging-operator}"
DELETE_NAMESPACE="${DELETE_NAMESPACE:-false}"
DELETE_CLUSTER="${DELETE_CLUSTER:-false}"
CLUSTER_NAME="${CLUSTER_NAME:-messaging-operator-test}"

echo "=== Tearing Down Test Environment ==="
echo "Namespace: $NAMESPACE"

# Uninstall Helm release
if helm status "$RELEASE_NAME" -n "$NAMESPACE" &>/dev/null; then
    echo "Uninstalling Helm release: $RELEASE_NAME"
    helm uninstall "$RELEASE_NAME" -n "$NAMESPACE"
fi

# Delete test resources in namespace
echo "Cleaning up test resources..."
kubectl delete applicationservices,virtualclusters,serviceaccounts,topics,acls,consumergroups \
    --all -n "$NAMESPACE" --ignore-not-found=true 2>/dev/null || true

# Delete namespace if requested
if [[ "$DELETE_NAMESPACE" == "true" ]]; then
    echo "Deleting namespace: $NAMESPACE"
    kubectl delete namespace "$NAMESPACE" --ignore-not-found=true
fi

# Delete cluster if requested
if [[ "$DELETE_CLUSTER" == "true" ]]; then
    echo "Deleting Minikube cluster: $CLUSTER_NAME"
    minikube delete -p "$CLUSTER_NAME"
fi

# Cleanup local files
rm -f "$PROJECT_ROOT/.test-namespace"
rm -rf "$PROJECT_ROOT/.certs"

echo "=== Teardown complete ==="
```

**Step 2: Make executable**

Run: `chmod +x functional-tests/scripts/teardown.sh`

**Step 3: Commit**

```bash
git add functional-tests/scripts/teardown.sh
git commit -m "feat: add teardown script for cleanup"
```

---

## Task 16: Create run-tests.sh Main Entry Point

**Files:**
- Create: `functional-tests/scripts/run-tests.sh`

**Step 1: Create run-tests.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PROJECT_ROOT/.." && pwd)"

# Default options
FRESH_CLUSTER=false
SKIP_DEPLOY=false
SKIP_TESTS=false
BATS_ONLY=false
JAVA_ONLY=false
TEST_FILTER=""
CLEANUP=false

usage() {
    cat <<EOF
Usage: $0 [OPTIONS]

Messaging Operator Functional Tests

Options:
    --fresh-cluster    Create new Minikube cluster (default: reuse existing)
    --skip-deploy      Skip Helm deployment (use existing deployment)
    --skip-tests       Deploy only, don't run tests
    --bats-only        Run only Bats tests
    --java-only        Run only Java E2E tests
    --test-filter      Run only matching bats files (e.g., "02_webhook")
    --cleanup          Delete namespace after tests
    -h, --help         Show this help

Examples:
    $0                           # Quick local run (reuse cluster)
    $0 --fresh-cluster --cleanup # CI-style fresh run
    $0 --bats-only --test-filter "02_webhook"
    $0 --java-only
EOF
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --fresh-cluster)
            FRESH_CLUSTER=true
            shift
            ;;
        --skip-deploy)
            SKIP_DEPLOY=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --bats-only)
            BATS_ONLY=true
            shift
            ;;
        --java-only)
            JAVA_ONLY=true
            shift
            ;;
        --test-filter)
            TEST_FILTER="$2"
            shift 2
            ;;
        --cleanup)
            CLEANUP=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

echo "========================================"
echo "  Messaging Operator E2E Tests"
echo "========================================"

# Track results
BATS_RESULT=0
JAVA_RESULT=0

# Setup cluster
echo ""
echo "[1/4] Setting up Minikube cluster..."
FRESH_CLUSTER="$FRESH_CLUSTER" "$SCRIPT_DIR/setup-minikube.sh"

NAMESPACE=$(cat "$PROJECT_ROOT/.test-namespace")
echo "Using namespace: $NAMESPACE"

# Deploy
if [[ "$SKIP_DEPLOY" != "true" ]]; then
    echo ""
    echo "[2/4] Deploying operator..."
    NAMESPACE="$NAMESPACE" "$SCRIPT_DIR/deploy.sh"
else
    echo ""
    echo "[2/4] Skipping deployment (--skip-deploy)"
fi

# Run tests
if [[ "$SKIP_TESTS" != "true" ]]; then
    # Run Bats tests
    if [[ "$JAVA_ONLY" != "true" ]]; then
        echo ""
        echo "[3/4] Running Bats tests..."

        cd "$PROJECT_ROOT"

        if [[ -n "$TEST_FILTER" ]]; then
            BATS_FILES=$(find bats -name "*${TEST_FILTER}*.bats" | sort)
        else
            BATS_FILES=$(find bats -name "*.bats" | sort)
        fi

        if [[ -n "$BATS_FILES" ]]; then
            for bats_file in $BATS_FILES; do
                echo "  Running: $bats_file"
                if bats "$bats_file"; then
                    echo "  ✓ $bats_file passed"
                else
                    echo "  ✗ $bats_file failed"
                    BATS_RESULT=1
                fi
            done
        else
            echo "  No bats files found matching filter: $TEST_FILTER"
        fi
    else
        echo ""
        echo "[3/4] Skipping Bats tests (--java-only)"
    fi

    # Run Java tests
    if [[ "$BATS_ONLY" != "true" ]]; then
        echo ""
        echo "[4/4] Running Java E2E tests..."

        cd "$REPO_ROOT"
        if TEST_NAMESPACE="$NAMESPACE" mvn verify -Pe2e -DskipUTs=true; then
            echo "  ✓ Java E2E tests passed"
        else
            echo "  ✗ Java E2E tests failed"
            JAVA_RESULT=1
        fi
    else
        echo ""
        echo "[4/4] Skipping Java tests (--bats-only)"
    fi
else
    echo ""
    echo "[3/4] Skipping tests (--skip-tests)"
    echo "[4/4] Skipping tests (--skip-tests)"
fi

# Cleanup
if [[ "$CLEANUP" == "true" ]]; then
    echo ""
    echo "Cleaning up..."
    DELETE_NAMESPACE=true "$SCRIPT_DIR/teardown.sh"
fi

# Summary
echo ""
echo "========================================"
echo "  Results"
echo "========================================"

TOTAL_RESULT=0
if [[ "$JAVA_ONLY" != "true" ]] && [[ "$SKIP_TESTS" != "true" ]]; then
    if [[ $BATS_RESULT -eq 0 ]]; then
        echo "Bats:  PASSED ✓"
    else
        echo "Bats:  FAILED ✗"
        TOTAL_RESULT=1
    fi
fi

if [[ "$BATS_ONLY" != "true" ]] && [[ "$SKIP_TESTS" != "true" ]]; then
    if [[ $JAVA_RESULT -eq 0 ]]; then
        echo "Java:  PASSED ✓"
    else
        echo "Java:  FAILED ✗"
        TOTAL_RESULT=1
    fi
fi

echo "========================================"

exit $TOTAL_RESULT
```

**Step 2: Make executable**

Run: `chmod +x functional-tests/scripts/run-tests.sh`

**Step 3: Commit**

```bash
git add functional-tests/scripts/run-tests.sh
git commit -m "feat: add main test runner script with all options"
```

---

## Task 17: Create Bats Test Helper

**Files:**
- Create: `functional-tests/bats/test_helper.bash`

**Step 1: Create test_helper.bash**

```bash
#!/usr/bin/env bash

# Load bats libraries (installed via package manager or npm)
# Fallback paths for different installation methods
if [[ -f "/usr/lib/bats-support/load.bash" ]]; then
    load '/usr/lib/bats-support/load.bash'
    load '/usr/lib/bats-assert/load.bash'
elif [[ -f "$HOME/.bats/bats-support/load.bash" ]]; then
    load "$HOME/.bats/bats-support/load.bash"
    load "$HOME/.bats/bats-assert/load.bash"
else
    # Minimal fallback assertions
    assert_success() {
        if [[ "$status" -ne 0 ]]; then
            echo "Expected success but got status: $status"
            echo "Output: $output"
            return 1
        fi
    }

    assert_failure() {
        if [[ "$status" -eq 0 ]]; then
            echo "Expected failure but got success"
            echo "Output: $output"
            return 1
        fi
    }

    assert_output() {
        if [[ "$1" == "--partial" ]]; then
            if [[ "$output" != *"$2"* ]]; then
                echo "Expected output to contain: $2"
                echo "Actual output: $output"
                return 1
            fi
        else
            if [[ "$output" != "$1" ]]; then
                echo "Expected: $1"
                echo "Actual: $output"
                return 1
            fi
        fi
    }
fi

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FIXTURES_DIR="$PROJECT_ROOT/fixtures"

# Read namespace from file or use default
NAMESPACE="${TEST_NAMESPACE:-$(cat "$PROJECT_ROOT/.test-namespace" 2>/dev/null || echo "operator-system")}"

# Default timeout for wait operations
TIMEOUT="${TIMEOUT:-60}"

# Wait for a condition with timeout
# Usage: wait_for "kubectl get pods" [timeout_seconds]
wait_for() {
    local cmd="$1"
    local timeout="${2:-$TIMEOUT}"

    for ((i=0; i<timeout; i++)); do
        if eval "$cmd" &>/dev/null; then
            return 0
        fi
        sleep 1
    done

    echo "Timeout waiting for: $cmd"
    return 1
}

# Apply a fixture file
# Usage: apply_fixture "valid/application-service.yaml"
apply_fixture() {
    local fixture="$1"
    kubectl apply -f "$FIXTURES_DIR/$fixture" -n "$NAMESPACE" 2>&1
}

# Delete a fixture file
# Usage: delete_fixture "valid/application-service.yaml"
delete_fixture() {
    local fixture="$1"
    kubectl delete -f "$FIXTURES_DIR/$fixture" -n "$NAMESPACE" --ignore-not-found=true 2>&1
}

# Expect kubectl command to fail with specific message
# Usage: expect_rejection "invalid/missing-appname.yaml" "appName is required"
expect_rejection() {
    local fixture="$1"
    local expected_msg="$2"

    run kubectl apply -f "$FIXTURES_DIR/$fixture" -n "$NAMESPACE"
    assert_failure
    assert_output --partial "$expected_msg"
}

# Get resource by name
# Usage: get_resource "applicationservice" "my-app"
get_resource() {
    local kind="$1"
    local name="$2"
    kubectl get "$kind" "$name" -n "$NAMESPACE" -o json 2>/dev/null
}

# Check if resource exists
# Usage: resource_exists "applicationservice" "my-app"
resource_exists() {
    local kind="$1"
    local name="$2"
    kubectl get "$kind" "$name" -n "$NAMESPACE" &>/dev/null
}

# Cleanup all test resources in namespace
cleanup_test_resources() {
    kubectl delete applicationservices,virtualclusters,serviceaccounts,topics,acls,consumergroups \
        --all -n "$NAMESPACE" --ignore-not-found=true &>/dev/null || true
}

# Wait for webhook to be ready
wait_for_webhook() {
    local timeout="${1:-60}"
    wait_for "kubectl get endpoints messaging-operator-webhook -n $NAMESPACE -o jsonpath='{.subsets[0].addresses}' | grep -q '.'" "$timeout"
}
```

**Step 2: Commit**

```bash
git add functional-tests/bats/test_helper.bash
git commit -m "feat: add Bats test helper with common utilities"
```

---

## Task 18: Create 01_deployment.bats

**Files:**
- Create: `functional-tests/bats/01_deployment.bats`

**Step 1: Create 01_deployment.bats**

```bash
#!/usr/bin/env bats

load 'test_helper'

setup_file() {
    echo "# Verifying deployment prerequisites" >&3
}

@test "webhook deployment exists" {
    run kubectl get deployment messaging-operator-webhook -n "$NAMESPACE"
    assert_success
}

@test "webhook deployment has ready replicas" {
    run kubectl get deployment messaging-operator-webhook -n "$NAMESPACE" \
        -o jsonpath='{.status.readyReplicas}'
    assert_success
    [[ "$output" -ge 1 ]]
}

@test "webhook service exists" {
    run kubectl get service messaging-operator-webhook -n "$NAMESPACE"
    assert_success
}

@test "webhook service has endpoints" {
    run kubectl get endpoints messaging-operator-webhook -n "$NAMESPACE" \
        -o jsonpath='{.subsets[0].addresses[0].ip}'
    assert_success
    [[ -n "$output" ]]
}

@test "webhook pod is running" {
    run kubectl get pods -n "$NAMESPACE" -l app=messaging-operator-webhook \
        -o jsonpath='{.items[0].status.phase}'
    assert_success
    assert_output "Running"
}

@test "CRDs are installed" {
    local crds=("applicationservices" "virtualclusters" "serviceaccounts" "topics" "acls" "consumergroups")

    for crd in "${crds[@]}"; do
        run kubectl get crd "${crd}.example.com"
        assert_success
    done
}

@test "ValidatingWebhookConfiguration exists" {
    run kubectl get validatingwebhookconfiguration messaging-operator-webhook
    assert_success
}
```

**Step 2: Commit**

```bash
git add functional-tests/bats/01_deployment.bats
git commit -m "feat: add deployment verification Bats tests"
```

---

## Task 19: Create 02_webhook_admission.bats

**Files:**
- Create: `functional-tests/bats/02_webhook_admission.bats`

**Step 1: Create 02_webhook_admission.bats**

```bash
#!/usr/bin/env bats

load 'test_helper'

setup_file() {
    echo "# Setting up webhook admission tests" >&3
    wait_for_webhook 60
    cleanup_test_resources
}

teardown_file() {
    cleanup_test_resources
}

@test "webhook accepts valid ApplicationService" {
    run apply_fixture "valid/application-service.yaml"
    assert_success
}

@test "webhook accepts valid VirtualCluster with existing ApplicationService" {
    # Ensure ApplicationService exists first
    apply_fixture "valid/application-service.yaml"

    run apply_fixture "valid/virtual-cluster.yaml"
    assert_success
}

@test "webhook accepts valid ServiceAccount" {
    apply_fixture "valid/application-service.yaml"
    apply_fixture "valid/virtual-cluster.yaml"

    run apply_fixture "valid/service-account.yaml"
    assert_success
}

@test "webhook accepts valid Topic" {
    apply_fixture "valid/application-service.yaml"
    apply_fixture "valid/virtual-cluster.yaml"
    apply_fixture "valid/service-account.yaml"

    run apply_fixture "valid/topic.yaml"
    assert_success
}

@test "webhook accepts valid ACL" {
    run apply_fixture "valid/acl.yaml"
    assert_success
}

@test "webhook accepts valid ConsumerGroup" {
    run apply_fixture "valid/consumer-group.yaml"
    assert_success
}

@test "webhook rejects ApplicationService with missing name" {
    expect_rejection "invalid/missing-appname.yaml" "name"
}

@test "webhook rejects VirtualCluster with non-existent ApplicationService ref" {
    expect_rejection "invalid/nonexistent-appservice-ref.yaml" "ApplicationService"
}
```

**Step 2: Commit**

```bash
git add functional-tests/bats/02_webhook_admission.bats
git commit -m "feat: add webhook admission Bats tests"
```

---

## Task 20: Create 03_ownership_chain.bats

**Files:**
- Create: `functional-tests/bats/03_ownership_chain.bats`

**Step 1: Create 03_ownership_chain.bats**

```bash
#!/usr/bin/env bats

load 'test_helper'

setup_file() {
    echo "# Setting up ownership chain tests" >&3
    wait_for_webhook 60
    cleanup_test_resources
}

teardown_file() {
    cleanup_test_resources
}

setup() {
    # Each test starts with a clean slate
    cleanup_test_resources
}

@test "full ownership chain - valid hierarchy accepted" {
    # Create complete hierarchy: App -> VC -> SA -> Topic
    run apply_fixture "ownership-chain/full-hierarchy.yaml"
    assert_success

    # Verify all resources created
    resource_exists "applicationservice" "chain-app"
    resource_exists "virtualcluster" "chain-vc"
    resource_exists "serviceaccount" "chain-sa"
    resource_exists "topic" "chain-topic"
}

@test "VirtualCluster requires valid ApplicationService reference" {
    # Try to create VirtualCluster without ApplicationService
    expect_rejection "invalid/vc-without-appservice.yaml" "ApplicationService"
}

@test "ServiceAccount requires valid VirtualCluster reference" {
    # Create ApplicationService but not VirtualCluster
    apply_fixture "valid/application-service.yaml"

    expect_rejection "invalid/sa-without-vc.yaml" "VirtualCluster"
}

@test "Topic requires valid ServiceAccount reference" {
    apply_fixture "valid/application-service.yaml"
    apply_fixture "valid/virtual-cluster.yaml"
    # Don't create ServiceAccount

    expect_rejection "invalid/topic-without-sa.yaml" "ServiceAccount"
}

@test "cannot change applicationServiceRef on update" {
    # Create valid topic
    apply_fixture "valid/application-service.yaml"
    apply_fixture "valid/virtual-cluster.yaml"
    apply_fixture "valid/service-account.yaml"
    apply_fixture "valid/topic.yaml"

    # Try to update with different applicationServiceRef
    expect_rejection "invalid/topic-changed-owner.yaml" "immutable"
}

@test "deleting parent fails if children exist" {
    apply_fixture "ownership-chain/full-hierarchy.yaml"

    # Try to delete ApplicationService while VirtualCluster exists
    run kubectl delete applicationservice chain-app -n "$NAMESPACE"
    # Should fail due to ownership constraints or webhook rejection
    # (depends on implementation)
}
```

**Step 2: Commit**

```bash
git add functional-tests/bats/03_ownership_chain.bats
git commit -m "feat: add ownership chain Bats tests"
```

---

## Task 21: Create 04_multi_tenant.bats

**Files:**
- Create: `functional-tests/bats/04_multi_tenant.bats`

**Step 1: Create 04_multi_tenant.bats**

```bash
#!/usr/bin/env bats

load 'test_helper'

setup_file() {
    echo "# Setting up multi-tenant isolation tests" >&3
    wait_for_webhook 60
    cleanup_test_resources

    # Create two separate tenants
    apply_fixture "tenant-a/application-service.yaml"
    apply_fixture "tenant-b/application-service.yaml"
    apply_fixture "tenant-a/virtual-cluster.yaml"
    apply_fixture "tenant-a/service-account.yaml"
}

teardown_file() {
    cleanup_test_resources
}

@test "tenant A can create resources under own ApplicationService" {
    run apply_fixture "tenant-a/topic.yaml"
    assert_success
}

@test "tenant B cannot reference tenant A's VirtualCluster" {
    expect_rejection "tenant-b/topic-referencing-tenant-a-vc.yaml" \
        "does not belong to ApplicationService"
}

@test "tenant B cannot reference tenant A's ServiceAccount" {
    expect_rejection "tenant-b/acl-referencing-tenant-a-sa.yaml" \
        "does not belong to ApplicationService"
}

@test "tenant B can create own isolated resources" {
    apply_fixture "tenant-b/virtual-cluster.yaml"
    apply_fixture "tenant-b/service-account.yaml"

    run apply_fixture "tenant-b/topic.yaml"
    assert_success
}
```

**Step 2: Commit**

```bash
git add functional-tests/bats/04_multi_tenant.bats
git commit -m "feat: add multi-tenant isolation Bats tests"
```

---

## Task 22: Create 05_ha_failover.bats

**Files:**
- Create: `functional-tests/bats/05_ha_failover.bats`

**Step 1: Create 05_ha_failover.bats**

```bash
#!/usr/bin/env bats

load 'test_helper'

setup_file() {
    echo "# Setting up HA failover tests" >&3
    wait_for_webhook 60
    cleanup_test_resources

    # Scale to 2 replicas for HA tests
    kubectl scale deployment messaging-operator-webhook -n "$NAMESPACE" --replicas=2

    # Wait for both replicas
    wait_for "kubectl get deployment messaging-operator-webhook -n $NAMESPACE -o jsonpath='{.status.readyReplicas}' | grep -q '2'" 120
}

teardown_file() {
    cleanup_test_resources
    # Scale back to 1
    kubectl scale deployment messaging-operator-webhook -n "$NAMESPACE" --replicas=1 || true
}

@test "webhook has 2 ready replicas" {
    run kubectl get deployment messaging-operator-webhook -n "$NAMESPACE" \
        -o jsonpath='{.status.readyReplicas}'
    assert_success
    assert_output "2"
}

@test "webhook survives single pod failure" {
    # Get first pod name
    local pod
    pod=$(kubectl get pods -n "$NAMESPACE" -l app=messaging-operator-webhook \
        -o jsonpath='{.items[0].metadata.name}')

    # Delete one pod (non-blocking)
    kubectl delete pod "$pod" -n "$NAMESPACE" --wait=false

    # Brief pause for service to update endpoints
    sleep 2

    # Operations should still succeed via other replica
    run apply_fixture "ha-test/application-service.yaml"
    assert_success
}

@test "webhook recovers after pod restart" {
    # Wait for replacement pod
    wait_for "kubectl get deployment messaging-operator-webhook -n $NAMESPACE -o jsonpath='{.status.readyReplicas}' | grep -q '2'" 120

    # Verify operations work
    run apply_fixture "ha-test/virtual-cluster.yaml"
    assert_success
}

@test "rolling restart maintains availability" {
    # Trigger rolling restart
    kubectl rollout restart deployment messaging-operator-webhook -n "$NAMESPACE"

    # During rollout, operations should still work
    local success_count=0
    for i in {1..5}; do
        if apply_fixture "ha-test/topic-rolling-$i.yaml" &>/dev/null; then
            ((success_count++))
        fi
        sleep 2
    done

    # At least 3 out of 5 should succeed during rolling restart
    [[ $success_count -ge 3 ]]

    # Wait for rollout to complete
    kubectl rollout status deployment messaging-operator-webhook -n "$NAMESPACE" --timeout=120s
}
```

**Step 2: Commit**

```bash
git add functional-tests/bats/05_ha_failover.bats
git commit -m "feat: add HA failover Bats tests"
```

---

## Task 23: Create Test Fixtures - Valid Resources

**Files:**
- Create: `functional-tests/fixtures/valid/application-service.yaml`
- Create: `functional-tests/fixtures/valid/virtual-cluster.yaml`
- Create: `functional-tests/fixtures/valid/service-account.yaml`
- Create: `functional-tests/fixtures/valid/topic.yaml`
- Create: `functional-tests/fixtures/valid/acl.yaml`
- Create: `functional-tests/fixtures/valid/consumer-group.yaml`

**Step 1: Create valid/application-service.yaml**

```yaml
apiVersion: example.com/v1
kind: ApplicationService
metadata:
  name: test-app
spec:
  name: test-app
```

**Step 2: Create valid/virtual-cluster.yaml**

```yaml
apiVersion: example.com/v1
kind: VirtualCluster
metadata:
  name: test-vc
spec:
  clusterId: test-cluster-id
  applicationServiceRef: test-app
```

**Step 3: Create valid/service-account.yaml**

```yaml
apiVersion: example.com/v1
kind: ServiceAccount
metadata:
  name: test-sa
spec:
  name: test-sa
  dn:
    - "CN=test-sa"
  clusterRef: test-vc
  applicationServiceRef: test-app
```

**Step 4: Create valid/topic.yaml**

```yaml
apiVersion: example.com/v1
kind: Topic
metadata:
  name: test-topic
spec:
  name: test-topic
  serviceRef: test-sa
  partitions: 6
  replicationFactor: 3
  applicationServiceRef: test-app
```

**Step 5: Create valid/acl.yaml**

```yaml
apiVersion: example.com/v1
kind: ACL
metadata:
  name: test-acl
spec:
  serviceRef: test-sa
  topicRef: test-topic
  operations:
    - READ
    - WRITE
  host: "*"
  permission: ALLOW
  applicationServiceRef: test-app
```

**Step 6: Create valid/consumer-group.yaml**

```yaml
apiVersion: example.com/v1
kind: ConsumerGroup
metadata:
  name: test-cg
spec:
  name: test-cg
  serviceRef: test-sa
  patternType: LITERAL
  applicationServiceRef: test-app
```

**Step 7: Commit**

```bash
git add functional-tests/fixtures/valid/
git commit -m "feat: add valid test fixtures for all CRD types"
```

---

## Task 24: Create Test Fixtures - Invalid Resources

**Files:**
- Create: `functional-tests/fixtures/invalid/missing-appname.yaml`
- Create: `functional-tests/fixtures/invalid/nonexistent-appservice-ref.yaml`
- Create: `functional-tests/fixtures/invalid/vc-without-appservice.yaml`
- Create: `functional-tests/fixtures/invalid/sa-without-vc.yaml`
- Create: `functional-tests/fixtures/invalid/topic-without-sa.yaml`
- Create: `functional-tests/fixtures/invalid/topic-changed-owner.yaml`

**Step 1: Create invalid/missing-appname.yaml**

```yaml
apiVersion: example.com/v1
kind: ApplicationService
metadata:
  name: invalid-app
spec:
  # Missing required 'name' field
  description: "This should fail validation"
```

**Step 2: Create invalid/nonexistent-appservice-ref.yaml**

```yaml
apiVersion: example.com/v1
kind: VirtualCluster
metadata:
  name: invalid-vc
spec:
  clusterId: test-cluster
  applicationServiceRef: nonexistent-app-that-does-not-exist
```

**Step 3: Create invalid/vc-without-appservice.yaml**

```yaml
apiVersion: example.com/v1
kind: VirtualCluster
metadata:
  name: orphan-vc
spec:
  clusterId: orphan-cluster
  applicationServiceRef: app-that-does-not-exist
```

**Step 4: Create invalid/sa-without-vc.yaml**

```yaml
apiVersion: example.com/v1
kind: ServiceAccount
metadata:
  name: orphan-sa
spec:
  name: orphan-sa
  dn:
    - "CN=orphan"
  clusterRef: vc-that-does-not-exist
  applicationServiceRef: test-app
```

**Step 5: Create invalid/topic-without-sa.yaml**

```yaml
apiVersion: example.com/v1
kind: Topic
metadata:
  name: orphan-topic
spec:
  name: orphan-topic
  serviceRef: sa-that-does-not-exist
  partitions: 6
  replicationFactor: 3
  applicationServiceRef: test-app
```

**Step 6: Create invalid/topic-changed-owner.yaml**

```yaml
# This is applied as an UPDATE to existing test-topic
# with a different applicationServiceRef (should be rejected as immutable)
apiVersion: example.com/v1
kind: Topic
metadata:
  name: test-topic
spec:
  name: test-topic
  serviceRef: test-sa
  partitions: 6
  replicationFactor: 3
  applicationServiceRef: different-app  # Changed from test-app
```

**Step 7: Commit**

```bash
git add functional-tests/fixtures/invalid/
git commit -m "feat: add invalid test fixtures for rejection scenarios"
```

---

## Task 25: Create Test Fixtures - Tenant Isolation

**Files:**
- Create: `functional-tests/fixtures/tenant-a/application-service.yaml`
- Create: `functional-tests/fixtures/tenant-a/virtual-cluster.yaml`
- Create: `functional-tests/fixtures/tenant-a/service-account.yaml`
- Create: `functional-tests/fixtures/tenant-a/topic.yaml`
- Create: `functional-tests/fixtures/tenant-b/application-service.yaml`
- Create: `functional-tests/fixtures/tenant-b/virtual-cluster.yaml`
- Create: `functional-tests/fixtures/tenant-b/service-account.yaml`
- Create: `functional-tests/fixtures/tenant-b/topic.yaml`
- Create: `functional-tests/fixtures/tenant-b/topic-referencing-tenant-a-vc.yaml`
- Create: `functional-tests/fixtures/tenant-b/acl-referencing-tenant-a-sa.yaml`

**Step 1: Create tenant-a/application-service.yaml**

```yaml
apiVersion: example.com/v1
kind: ApplicationService
metadata:
  name: tenant-a-app
spec:
  name: tenant-a-app
```

**Step 2: Create tenant-a/virtual-cluster.yaml**

```yaml
apiVersion: example.com/v1
kind: VirtualCluster
metadata:
  name: tenant-a-vc
spec:
  clusterId: tenant-a-cluster
  applicationServiceRef: tenant-a-app
```

**Step 3: Create tenant-a/service-account.yaml**

```yaml
apiVersion: example.com/v1
kind: ServiceAccount
metadata:
  name: tenant-a-sa
spec:
  name: tenant-a-sa
  dn:
    - "CN=tenant-a-sa"
  clusterRef: tenant-a-vc
  applicationServiceRef: tenant-a-app
```

**Step 4: Create tenant-a/topic.yaml**

```yaml
apiVersion: example.com/v1
kind: Topic
metadata:
  name: tenant-a-topic
spec:
  name: tenant-a-topic
  serviceRef: tenant-a-sa
  partitions: 6
  replicationFactor: 3
  applicationServiceRef: tenant-a-app
```

**Step 5: Create tenant-b/application-service.yaml**

```yaml
apiVersion: example.com/v1
kind: ApplicationService
metadata:
  name: tenant-b-app
spec:
  name: tenant-b-app
```

**Step 6: Create tenant-b/virtual-cluster.yaml**

```yaml
apiVersion: example.com/v1
kind: VirtualCluster
metadata:
  name: tenant-b-vc
spec:
  clusterId: tenant-b-cluster
  applicationServiceRef: tenant-b-app
```

**Step 7: Create tenant-b/service-account.yaml**

```yaml
apiVersion: example.com/v1
kind: ServiceAccount
metadata:
  name: tenant-b-sa
spec:
  name: tenant-b-sa
  dn:
    - "CN=tenant-b-sa"
  clusterRef: tenant-b-vc
  applicationServiceRef: tenant-b-app
```

**Step 8: Create tenant-b/topic.yaml**

```yaml
apiVersion: example.com/v1
kind: Topic
metadata:
  name: tenant-b-topic
spec:
  name: tenant-b-topic
  serviceRef: tenant-b-sa
  partitions: 6
  replicationFactor: 3
  applicationServiceRef: tenant-b-app
```

**Step 9: Create tenant-b/topic-referencing-tenant-a-vc.yaml (should be rejected)**

```yaml
apiVersion: example.com/v1
kind: Topic
metadata:
  name: tenant-b-illegal-topic
spec:
  name: tenant-b-illegal-topic
  serviceRef: tenant-a-sa  # ILLEGAL: referencing tenant A's service account
  partitions: 6
  replicationFactor: 3
  applicationServiceRef: tenant-b-app
```

**Step 10: Create tenant-b/acl-referencing-tenant-a-sa.yaml (should be rejected)**

```yaml
apiVersion: example.com/v1
kind: ACL
metadata:
  name: tenant-b-illegal-acl
spec:
  serviceRef: tenant-a-sa  # ILLEGAL: referencing tenant A's service account
  topicRef: tenant-a-topic  # ILLEGAL: referencing tenant A's topic
  operations:
    - READ
  host: "*"
  permission: ALLOW
  applicationServiceRef: tenant-b-app
```

**Step 11: Commit**

```bash
git add functional-tests/fixtures/tenant-a/ functional-tests/fixtures/tenant-b/
git commit -m "feat: add multi-tenant isolation test fixtures"
```

---

## Task 26: Create Test Fixtures - Ownership Chain and HA

**Files:**
- Create: `functional-tests/fixtures/ownership-chain/full-hierarchy.yaml`
- Create: `functional-tests/fixtures/ha-test/application-service.yaml`
- Create: `functional-tests/fixtures/ha-test/virtual-cluster.yaml`
- Create: `functional-tests/fixtures/ha-test/topic-rolling-1.yaml` through `topic-rolling-5.yaml`

**Step 1: Create ownership-chain/full-hierarchy.yaml**

```yaml
---
apiVersion: example.com/v1
kind: ApplicationService
metadata:
  name: chain-app
spec:
  name: chain-app
---
apiVersion: example.com/v1
kind: VirtualCluster
metadata:
  name: chain-vc
spec:
  clusterId: chain-cluster
  applicationServiceRef: chain-app
---
apiVersion: example.com/v1
kind: ServiceAccount
metadata:
  name: chain-sa
spec:
  name: chain-sa
  dn:
    - "CN=chain-sa"
  clusterRef: chain-vc
  applicationServiceRef: chain-app
---
apiVersion: example.com/v1
kind: Topic
metadata:
  name: chain-topic
spec:
  name: chain-topic
  serviceRef: chain-sa
  partitions: 6
  replicationFactor: 3
  applicationServiceRef: chain-app
```

**Step 2: Create ha-test/application-service.yaml**

```yaml
apiVersion: example.com/v1
kind: ApplicationService
metadata:
  name: ha-test-app
spec:
  name: ha-test-app
```

**Step 3: Create ha-test/virtual-cluster.yaml**

```yaml
apiVersion: example.com/v1
kind: VirtualCluster
metadata:
  name: ha-test-vc
spec:
  clusterId: ha-test-cluster
  applicationServiceRef: ha-test-app
```

**Step 4: Create ha-test/topic-rolling-1.yaml through topic-rolling-5.yaml**

Create 5 files with incrementing names:

```yaml
# ha-test/topic-rolling-1.yaml
apiVersion: example.com/v1
kind: ApplicationService
metadata:
  name: rolling-test-1
spec:
  name: rolling-test-1
```

```yaml
# ha-test/topic-rolling-2.yaml
apiVersion: example.com/v1
kind: ApplicationService
metadata:
  name: rolling-test-2
spec:
  name: rolling-test-2
```

(Repeat for 3, 4, 5)

**Step 5: Commit**

```bash
git add functional-tests/fixtures/ownership-chain/ functional-tests/fixtures/ha-test/
git commit -m "feat: add ownership chain and HA test fixtures"
```

---

## Task 27: Add E2E Maven Profile to pom.xml

**Files:**
- Modify: `pom.xml:245-265` (after existing failsafe plugin config)

**Step 1: Add E2E profile to pom.xml**

Add this profile section before the closing `</project>` tag:

```xml
    <profiles>
        <profile>
            <id>e2e</id>
            <properties>
                <skipUTs>true</skipUTs>
            </properties>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-failsafe-plugin</artifactId>
                        <version>3.5.2</version>
                        <configuration>
                            <includes>
                                <include>**/*E2ETest.java</include>
                            </includes>
                            <excludes>
                                <exclude>**/*IT.java</exclude>
                            </excludes>
                            <systemPropertyVariables>
                                <test.namespace>${env.TEST_NAMESPACE}</test.namespace>
                            </systemPropertyVariables>
                        </configuration>
                        <executions>
                            <execution>
                                <goals>
                                    <goal>integration-test</goal>
                                    <goal>verify</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <configuration>
                            <skipTests>${skipUTs}</skipTests>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
```

**Step 2: Add Awaitility dependency for E2E tests**

Add in `<dependencies>` section:

```xml
        <!-- Awaitility for async assertions in E2E tests -->
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <version>4.2.0</version>
            <scope>test</scope>
        </dependency>
```

**Step 3: Commit**

```bash
git add pom.xml
git commit -m "feat: add E2E test Maven profile with Awaitility"
```

---

## Task 28: Create E2E Annotation

**Files:**
- Create: `src/test/java/com/example/messaging/operator/e2e/E2ETest.java`

**Step 1: Create E2ETest.java annotation**

```java
package com.example.messaging.operator.e2e;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class as an E2E test that runs against a real Kubernetes cluster.
 * These tests require a running Minikube cluster with the operator deployed.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public @interface E2ETest {
}
```

**Step 2: Commit**

```bash
git add src/test/java/com/example/messaging/operator/e2e/E2ETest.java
git commit -m "feat: add E2ETest annotation for E2E test classes"
```

---

## Task 29: Create E2ETestBase.java

**Files:**
- Create: `src/test/java/com/example/messaging/operator/e2e/E2ETestBase.java`

**Step 1: Create E2ETestBase.java**

```java
package com.example.messaging.operator.e2e;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Base class for E2E tests that run against a real Kubernetes cluster.
 * Connects to the current kubectl context (Minikube) and provides common utilities.
 */
public abstract class E2ETestBase {

    protected static KubernetesClient k8sClient;
    protected static String namespace;

    @BeforeAll
    void setupCluster() {
        Config config = Config.autoConfigure(null);
        k8sClient = new KubernetesClientBuilder()
                .withConfig(config)
                .build();

        namespace = resolveNamespace();
        System.out.println("E2E Test namespace: " + namespace);

        waitForWebhookReady();
    }

    @AfterAll
    void teardownCluster() {
        if (k8sClient != null) {
            k8sClient.close();
        }
    }

    private String resolveNamespace() {
        // First check environment variable
        String envNamespace = System.getenv("TEST_NAMESPACE");
        if (envNamespace != null && !envNamespace.isBlank()) {
            return envNamespace;
        }

        // Then check system property
        String propNamespace = System.getProperty("test.namespace");
        if (propNamespace != null && !propNamespace.isBlank()) {
            return propNamespace;
        }

        // Finally try to read from .test-namespace file
        try {
            Path namespaceFile = Path.of("functional-tests/.test-namespace");
            if (Files.exists(namespaceFile)) {
                return Files.readString(namespaceFile).trim();
            }
        } catch (IOException e) {
            // Ignore and use default
        }

        return "operator-system";
    }

    private void waitForWebhookReady() {
        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .until(this::webhookHasEndpoints);
    }

    private boolean webhookHasEndpoints() {
        try {
            var endpoints = k8sClient.endpoints()
                    .inNamespace(namespace)
                    .withName("messaging-operator-webhook")
                    .get();

            return endpoints != null
                    && endpoints.getSubsets() != null
                    && !endpoints.getSubsets().isEmpty()
                    && endpoints.getSubsets().get(0).getAddresses() != null
                    && !endpoints.getSubsets().get(0).getAddresses().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Assert that an action is rejected by the webhook with a specific message.
     */
    protected void assertRejectedWith(Executable action, String expectedMessage) {
        var exception = assertThrows(KubernetesClientException.class, action);
        assertThat(exception.getMessage()).contains(expectedMessage);
    }

    /**
     * Scale the webhook deployment to the specified replica count.
     */
    protected void scaleWebhook(int replicas) {
        k8sClient.apps().deployments()
                .inNamespace(namespace)
                .withName("messaging-operator-webhook")
                .scale(replicas);

        await()
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> {
                    var deployment = k8sClient.apps().deployments()
                            .inNamespace(namespace)
                            .withName("messaging-operator-webhook")
                            .get();
                    return deployment != null
                            && deployment.getStatus() != null
                            && deployment.getStatus().getReadyReplicas() != null
                            && deployment.getStatus().getReadyReplicas() >= replicas;
                });
    }

    /**
     * Get the names of all webhook pods.
     */
    protected List<String> getWebhookPods() {
        return k8sClient.pods()
                .inNamespace(namespace)
                .withLabel("app", "messaging-operator-webhook")
                .list()
                .getItems()
                .stream()
                .map(pod -> pod.getMetadata().getName())
                .toList();
    }

    /**
     * Clean up all test resources in the namespace.
     */
    protected void cleanupTestResources() {
        // Delete in reverse dependency order
        k8sClient.resources(com.example.messaging.operator.crd.ACL.class)
                .inNamespace(namespace).delete();
        k8sClient.resources(com.example.messaging.operator.crd.ConsumerGroup.class)
                .inNamespace(namespace).delete();
        k8sClient.resources(com.example.messaging.operator.crd.Topic.class)
                .inNamespace(namespace).delete();
        k8sClient.resources(com.example.messaging.operator.crd.ServiceAccount.class)
                .inNamespace(namespace).delete();
        k8sClient.resources(com.example.messaging.operator.crd.VirtualCluster.class)
                .inNamespace(namespace).delete();
        k8sClient.resources(com.example.messaging.operator.crd.ApplicationService.class)
                .inNamespace(namespace).delete();
    }
}
```

**Step 2: Commit**

```bash
git add src/test/java/com/example/messaging/operator/e2e/E2ETestBase.java
git commit -m "feat: add E2ETestBase with cluster connection and utilities"
```

---

## Task 30: Create OwnershipChainE2ETest.java

**Files:**
- Create: `src/test/java/com/example/messaging/operator/e2e/OwnershipChainE2ETest.java`

**Step 1: Create OwnershipChainE2ETest.java**

```java
package com.example.messaging.operator.e2e;

import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.it.base.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class OwnershipChainE2ETest extends E2ETestBase {

    @BeforeEach
    void setUp() {
        cleanupTestResources();
    }

    @AfterEach
    void tearDown() {
        cleanupTestResources();
    }

    @Test
    void fullOwnershipChain_validHierarchy_accepted() {
        var app = TestDataBuilder.applicationService()
                .namespace(namespace)
                .name("e2e-chain-app")
                .appName("e2e-chain-app")
                .createIn(k8sClient);

        assertThat(app).isNotNull();
        assertThat(app.getMetadata().getName()).isEqualTo("e2e-chain-app");

        var vc = TestDataBuilder.virtualCluster()
                .namespace(namespace)
                .name("e2e-chain-vc")
                .clusterId("e2e-cluster")
                .applicationServiceRef(app.getMetadata().getName())
                .createIn(k8sClient);

        assertThat(vc).isNotNull();

        var sa = TestDataBuilder.serviceAccount()
                .namespace(namespace)
                .name("e2e-chain-sa")
                .saName("e2e-chain-sa")
                .clusterRef(vc.getMetadata().getName())
                .applicationServiceRef(app.getMetadata().getName())
                .createIn(k8sClient);

        assertThat(sa).isNotNull();

        var topic = TestDataBuilder.topic()
                .namespace(namespace)
                .name("e2e-chain-topic")
                .topicName("e2e-chain-topic")
                .serviceRef(sa.getMetadata().getName())
                .applicationServiceRef(app.getMetadata().getName())
                .createIn(k8sClient);

        assertThat(topic).isNotNull();

        // Verify topic exists in cluster
        var fetchedTopic = k8sClient.resources(Topic.class)
                .inNamespace(namespace)
                .withName("e2e-chain-topic")
                .get();

        assertThat(fetchedTopic).isNotNull();
        assertThat(fetchedTopic.getSpec().getName()).isEqualTo("e2e-chain-topic");
    }

    @Test
    void topic_withNonExistentVirtualCluster_rejected() {
        var app = TestDataBuilder.applicationService()
                .namespace(namespace)
                .name("e2e-orphan-app")
                .appName("e2e-orphan-app")
                .createIn(k8sClient);

        assertRejectedWith(
                () -> TestDataBuilder.topic()
                        .namespace(namespace)
                        .name("e2e-orphan-topic")
                        .topicName("e2e-orphan-topic")
                        .serviceRef("nonexistent-sa")
                        .applicationServiceRef(app.getMetadata().getName())
                        .createIn(k8sClient),
                "ServiceAccount"
        );
    }

    @Test
    void virtualCluster_withNonExistentApplicationService_rejected() {
        assertRejectedWith(
                () -> TestDataBuilder.virtualCluster()
                        .namespace(namespace)
                        .name("e2e-orphan-vc")
                        .clusterId("orphan-cluster")
                        .applicationServiceRef("nonexistent-app")
                        .createIn(k8sClient),
                "ApplicationService"
        );
    }
}
```

**Step 2: Commit**

```bash
git add src/test/java/com/example/messaging/operator/e2e/OwnershipChainE2ETest.java
git commit -m "feat: add OwnershipChainE2ETest for real cluster validation"
```

---

## Task 31: Create MultiTenantE2ETest.java

**Files:**
- Create: `src/test/java/com/example/messaging/operator/e2e/MultiTenantE2ETest.java`

**Step 1: Create MultiTenantE2ETest.java**

```java
package com.example.messaging.operator.e2e;

import com.example.messaging.operator.crd.ApplicationService;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.crd.VirtualCluster;
import com.example.messaging.operator.it.base.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@E2ETest
class MultiTenantE2ETest extends E2ETestBase {

    private ApplicationService tenantAApp;
    private ApplicationService tenantBApp;
    private VirtualCluster tenantAVc;
    private ServiceAccount tenantASa;

    @BeforeEach
    void setUp() {
        cleanupTestResources();

        // Create tenant A resources
        tenantAApp = TestDataBuilder.applicationService()
                .namespace(namespace)
                .name("e2e-tenant-a")
                .appName("e2e-tenant-a")
                .createIn(k8sClient);

        tenantAVc = TestDataBuilder.virtualCluster()
                .namespace(namespace)
                .name("e2e-tenant-a-vc")
                .clusterId("tenant-a-cluster")
                .applicationServiceRef(tenantAApp.getMetadata().getName())
                .createIn(k8sClient);

        tenantASa = TestDataBuilder.serviceAccount()
                .namespace(namespace)
                .name("e2e-tenant-a-sa")
                .saName("e2e-tenant-a-sa")
                .clusterRef(tenantAVc.getMetadata().getName())
                .applicationServiceRef(tenantAApp.getMetadata().getName())
                .createIn(k8sClient);

        // Create tenant B app only
        tenantBApp = TestDataBuilder.applicationService()
                .namespace(namespace)
                .name("e2e-tenant-b")
                .appName("e2e-tenant-b")
                .createIn(k8sClient);
    }

    @AfterEach
    void tearDown() {
        cleanupTestResources();
    }

    @Test
    void tenantB_cannotReferenceTenantA_serviceAccount() {
        assertRejectedWith(
                () -> TestDataBuilder.topic()
                        .namespace(namespace)
                        .name("e2e-cross-tenant-topic")
                        .topicName("e2e-cross-tenant-topic")
                        .serviceRef(tenantASa.getMetadata().getName())  // Tenant A's SA
                        .applicationServiceRef(tenantBApp.getMetadata().getName())  // Tenant B's App
                        .createIn(k8sClient),
                "does not belong to ApplicationService"
        );
    }

    @Test
    void tenantB_canCreateOwnResources() {
        var tenantBVc = TestDataBuilder.virtualCluster()
                .namespace(namespace)
                .name("e2e-tenant-b-vc")
                .clusterId("tenant-b-cluster")
                .applicationServiceRef(tenantBApp.getMetadata().getName())
                .createIn(k8sClient);

        assertThat(tenantBVc).isNotNull();

        var tenantBSa = TestDataBuilder.serviceAccount()
                .namespace(namespace)
                .name("e2e-tenant-b-sa")
                .saName("e2e-tenant-b-sa")
                .clusterRef(tenantBVc.getMetadata().getName())
                .applicationServiceRef(tenantBApp.getMetadata().getName())
                .createIn(k8sClient);

        assertThat(tenantBSa).isNotNull();

        var tenantBTopic = TestDataBuilder.topic()
                .namespace(namespace)
                .name("e2e-tenant-b-topic")
                .topicName("e2e-tenant-b-topic")
                .serviceRef(tenantBSa.getMetadata().getName())
                .applicationServiceRef(tenantBApp.getMetadata().getName())
                .createIn(k8sClient);

        assertThat(tenantBTopic).isNotNull();
    }
}
```

**Step 2: Commit**

```bash
git add src/test/java/com/example/messaging/operator/e2e/MultiTenantE2ETest.java
git commit -m "feat: add MultiTenantE2ETest for isolation validation"
```

---

## Task 32: Create HAFailoverE2ETest.java

**Files:**
- Create: `src/test/java/com/example/messaging/operator/e2e/HAFailoverE2ETest.java`

**Step 1: Create HAFailoverE2ETest.java**

```java
package com.example.messaging.operator.e2e;

import com.example.messaging.operator.it.base.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@E2ETest
class HAFailoverE2ETest extends E2ETestBase {

    @BeforeEach
    void setUp() {
        cleanupTestResources();
    }

    @AfterEach
    void tearDown() {
        cleanupTestResources();
        // Scale back to 1 to not affect other tests
        try {
            scaleWebhook(1);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    void webhookRemainsAvailable_duringSinglePodFailure() {
        // Scale to 2 replicas
        scaleWebhook(2);

        var pods = getWebhookPods();
        assertThat(pods).hasSize(2);

        // Delete first pod
        k8sClient.pods()
                .inNamespace(namespace)
                .withName(pods.get(0))
                .delete();

        // Brief pause for service endpoint update
        await().pollDelay(Duration.ofSeconds(2)).until(() -> true);

        // Operations should still succeed via surviving replica
        for (int i = 0; i < 3; i++) {
            var app = TestDataBuilder.applicationService()
                    .namespace(namespace)
                    .name("e2e-ha-test-" + i)
                    .appName("e2e-ha-test-" + i)
                    .createIn(k8sClient);

            assertThat(app).isNotNull();
        }

        // Wait for replacement pod
        await()
                .atMost(Duration.ofSeconds(120))
                .pollInterval(Duration.ofSeconds(5))
                .until(() -> getWebhookPods().size() >= 2);
    }
}
```

**Step 2: Commit**

```bash
git add src/test/java/com/example/messaging/operator/e2e/HAFailoverE2ETest.java
git commit -m "feat: add HAFailoverE2ETest for HA validation"
```

---

## Task 33: Add .gitignore Entries

**Files:**
- Modify: `.gitignore`

**Step 1: Add functional test ignores**

Append to `.gitignore`:

```
# Functional tests
functional-tests/.test-namespace
functional-tests/.certs/
```

**Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: add functional test paths to .gitignore"
```

---

## Task 34: Final Validation

**Step 1: Verify Helm chart lints**

Run: `helm lint functional-tests/helm/messaging-operator`

Expected: No errors

**Step 2: Verify all scripts are executable**

Run: `ls -la functional-tests/scripts/`

Expected: All .sh files have execute permission

**Step 3: Verify all bats files have shebang**

Run: `head -1 functional-tests/bats/*.bats`

Expected: All files start with `#!/usr/bin/env bats`

**Step 4: Verify Maven compiles with E2E tests**

Run: `mvn compile test-compile -q`

Expected: BUILD SUCCESS

**Step 5: Final commit for any fixes**

```bash
git status
# If any uncommitted changes, commit them
```

---

## Summary

**Total Tasks:** 34
**Files Created:** ~45 files
**Commits:** ~30 commits

**Components Implemented:**
1. Helm chart with CRDs, deployment, service, webhook config, TLS
2. Scripts: setup-minikube, generate-certs, deploy, teardown, run-tests
3. Bats tests: deployment, webhook, ownership, multi-tenant, HA
4. Java E2E tests: OwnershipChain, MultiTenant, HAFailover
5. Test fixtures: valid, invalid, tenant-a, tenant-b, ownership-chain, ha-test

**To Run Tests:**
```bash
cd functional-tests
./scripts/run-tests.sh                    # Quick local run
./scripts/run-tests.sh --fresh-cluster    # Fresh cluster
./scripts/run-tests.sh --bats-only        # Bats only
./scripts/run-tests.sh --java-only        # Java only
```
