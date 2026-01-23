#!/bin/bash
# Operator Demo - Shows the messaging operator in action
# Demonstrates kubectl, conduktor CLI, and operator logs

set -e

NAMESPACE="demo-tenant"
OPERATOR_NS="test-1768463280"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

section() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    sleep 1
}

cmd() {
    echo -e "${GREEN}\$ $1${NC}"
    sleep 0.5
}

clear
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║     Kubernetes Messaging Operator - Live Demo                ║"
echo "║     Multi-tenant resource management with validation         ║"
echo "╚══════════════════════════════════════════════════════════════╝"
sleep 2

section "1. Check the Operator is Running"
cmd "kubectl get pods -n $OPERATOR_NS -l app.kubernetes.io/name=messaging-operator"
kubectl get pods -n $OPERATOR_NS -l app.kubernetes.io/name=messaging-operator
sleep 1

section "2. View Available CRDs"
cmd "kubectl get crd | grep messaging"
kubectl get crd | grep messaging
sleep 2

section "3. Create a Namespace for Our Demo Tenant"
cmd "kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -"
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
sleep 1

section "4. Apply the Full Resource Hierarchy"
echo "The messaging operator enforces an ownership chain:"
echo ""
echo "  ApplicationService (root)"
echo "       └── KafkaCluster (references AppService)"
echo "              └── ServiceAccount (references KafkaCluster + AppService)"
echo "                     └── Topic (references ServiceAccount + AppService)"
echo ""
sleep 2

# Create ApplicationService
echo -e "${YELLOW}Creating ApplicationService...${NC}"
cmd "kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: payments-app
  namespace: $NAMESPACE
spec:
  name: payments-app
EOF"

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: payments-app
  namespace: $NAMESPACE
spec:
  name: payments-app
EOF
sleep 1

# Create KafkaCluster
echo ""
echo -e "${YELLOW}Creating KafkaCluster...${NC}"
cmd "kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: payments-cluster
  namespace: $NAMESPACE
spec:
  clusterId: payments-cluster
  applicationServiceRef: payments-app
EOF"

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: payments-cluster
  namespace: $NAMESPACE
spec:
  clusterId: payments-cluster
  applicationServiceRef: payments-app
EOF
sleep 1

# Create ServiceAccount
echo ""
echo -e "${YELLOW}Creating ServiceAccount...${NC}"
cmd "kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: payments-producer
  namespace: $NAMESPACE
spec:
  name: payments-producer
  dn:
    - \"CN=payments-producer\"
  clusterRef: payments-cluster
  applicationServiceRef: payments-app
EOF"

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: payments-producer
  namespace: $NAMESPACE
spec:
  name: payments-producer
  dn:
    - "CN=payments-producer"
  clusterRef: payments-cluster
  applicationServiceRef: payments-app
EOF
sleep 1

# Create Topic
echo ""
echo -e "${YELLOW}Creating Topic...${NC}"
cmd "kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: payment-events
  namespace: $NAMESPACE
spec:
  name: payment-events
  serviceRef: payments-producer
  applicationServiceRef: payments-app
  partitions: 6
  replicationFactor: 3
  config:
    retention.ms: \"604800000\"
EOF"

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: payment-events
  namespace: $NAMESPACE
spec:
  name: payment-events
  serviceRef: payments-producer
  applicationServiceRef: payments-app
  partitions: 6
  replicationFactor: 3
  config:
    retention.ms: "604800000"
EOF
sleep 1

section "5. View Resources with kubectl"
cmd "kubectl get applicationservices,kafkaclusters,serviceaccounts.messaging.example.com,topics -n $NAMESPACE"
kubectl get applicationservices,kafkaclusters,serviceaccounts.messaging.example.com,topics -n $NAMESPACE
sleep 2

section "6. Check Kubernetes Events"
echo "Events show resource lifecycle activity:"
echo ""
cmd "kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp'"
kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' 2>/dev/null || echo "No events yet"
sleep 2

section "7. Check Operator Logs"
echo "The operator validates each resource creation:"
echo ""
cmd "kubectl logs deployment/messaging-operator-webhook -n $OPERATOR_NS --tail=15"
kubectl logs deployment/messaging-operator-webhook -n $OPERATOR_NS --tail=15
sleep 2

section "8. Try an Invalid Resource (Cross-Tenant Violation)"
echo "The operator prevents creating resources that reference"
echo "ApplicationServices from different tenants:"
echo ""

cmd "kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: hack-topic
  namespace: $NAMESPACE
spec:
  name: hack-topic
  serviceRef: other-tenant-sa
  applicationServiceRef: other-tenant-app
  partitions: 1
EOF"

kubectl apply -f - <<EOF 2>&1 || true
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: hack-topic
  namespace: $NAMESPACE
spec:
  name: hack-topic
  serviceRef: other-tenant-sa
  applicationServiceRef: other-tenant-app
  partitions: 1
EOF
sleep 2

section "9. View Resources in Conduktor Console (CLI)"
echo "Using conduktor CLI to see what's visible in Console:"
echo ""

export CDK_BASE_URL=http://localhost:8090
export CDK_API_KEY=$(CDK_BASE_URL=http://localhost:8090 CDK_USER=admin@demo.dev CDK_PASSWORD=123_ABC_abc ~/bin/conduktor login 2>/dev/null)

cmd "conduktor get KafkaCluster"
~/bin/conduktor get KafkaCluster 2>/dev/null || echo "(No clusters synced yet)"
sleep 1

cmd "conduktor get Topic"
~/bin/conduktor get Topic 2>/dev/null || echo "(Topics will appear after sync)"
sleep 2

section "10. Cleanup"
cmd "kubectl delete namespace $NAMESPACE"
kubectl delete namespace $NAMESPACE --wait=false
sleep 1

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                    Demo Complete!                            ║"
echo "║                                                              ║"
echo "║  Key takeaways:                                              ║"
echo "║  • Operator validates ownership chains                       ║"
echo "║  • Resources must reference valid parents                    ║"
echo "║  • Cross-tenant access is prevented                          ║"
echo "║  • kubectl and conduktor CLI work together                   ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
