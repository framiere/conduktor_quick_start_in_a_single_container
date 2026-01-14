# Webhook Deployment

## Prerequisites

1. Generate TLS certificates for webhook:

```bash
# Generate CA and server certificates
openssl genrsa -out ca.key 2048
openssl req -x509 -new -nodes -key ca.key -subj "/CN=messaging-operator-ca" -days 3650 -out ca.crt

openssl genrsa -out server.key 2048
openssl req -new -key server.key -subj "/CN=messaging-operator-webhook.operator-system.svc" -out server.csr
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 365

# Create K8s secret
kubectl create secret tls webhook-tls --cert=server.crt --key=server.key -n operator-system
```

2. Update webhook-config.yaml with CA bundle:

```bash
CA_BUNDLE=$(cat ca.crt | base64 | tr -d '\n')
sed -i "s/\${CA_BUNDLE}/$CA_BUNDLE/g" webhook-config.yaml
```

## Deploy

```bash
# Create namespace
kubectl create namespace operator-system

# Deploy webhook
kubectl apply -f webhook-service.yaml
kubectl apply -f webhook-deployment.yaml

# Wait for webhook to be ready
kubectl wait --for=condition=ready pod -l app=messaging-operator-webhook -n operator-system --timeout=60s

# Deploy webhook configuration
kubectl apply -f webhook-config.yaml
```

## Test

```bash
# Try to change ownership (should FAIL)
kubectl patch topic my-topic -p '{"spec":{"applicationServiceRef":"hacker-app"}}'

# Expected error:
# Error from server: admission webhook "topic.validate.example.com" denied the request:
# Cannot change applicationServiceRef from 'my-app' to 'hacker-app'
```
