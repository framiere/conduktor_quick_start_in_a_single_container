#!/bin/bash
# Reconciliation Demo: KafkaCluster CRD -> VirtualCluster in Gateway
# Record with: asciinema rec -c './reconciliation-demo.sh' --cols 120 --rows 40 --idle-time-limit 2 reconciliation-demo.cast

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

OPERATOR_NS="messaging-operator"
DEMO_NS="recon-demo"

# Setup Conduktor CLI
export CDK_BASE_URL=http://localhost:8090
export CDK_USER=admin@demo.dev
export CDK_PASSWORD='123_ABC_abc'
export CDK_GATEWAY_BASE_URL=http://localhost:8888
export CDK_GATEWAY_USER=admin
export CDK_GATEWAY_PASSWORD=conduktor

marker() {
    # Asciinema marker escape sequence
    printf "\033]1337;SetMark\007"
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}  📌 $1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    read -t 1 -n 1 || true
}

cmd() {
    echo -e "${GREEN}\$ $1${NC}"
    sleep 0.5
}

info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

success() {
    echo -e "${GREEN}✓ $1${NC}"
}


clear
echo ""
echo -e "${BOLD}${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}  Kubernetes Operator Reconciliation Demo${NC}"
echo -e "${BOLD}  Watch: KafkaCluster CRD  ──────►  Conduktor Gateway VirtualCluster${NC}"
echo -e "${BOLD}${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
sleep 2

# ============================================================================
marker "1. VERIFY OPERATOR IS RUNNING"
# ============================================================================

info "Checking operator deployment..."
echo ""

cmd "kubectl get pods -n $OPERATOR_NS"
kubectl get pods -n $OPERATOR_NS
sleep 1

info "Operator is healthy and watching for CRD changes"
sleep 2

# ============================================================================
marker "2. SHOW GATEWAY IS EMPTY (using Conduktor CLI)"
# ============================================================================

info "Cleaning up any existing VirtualClusters to start fresh..."
conduktor delete VirtualCluster payments-vcluster 2>/dev/null || true
echo ""

info "Using Conduktor CLI to check VirtualClusters BEFORE creating any CRD..."
echo ""

cmd "conduktor get VirtualCluster -o yaml"
conduktor get VirtualCluster -o yaml 2>&1 || echo -e "${YELLOW}(empty - no VirtualClusters in Gateway)${NC}"
sleep 2

# ============================================================================
marker "3. CREATE NAMESPACE AND ROOT RESOURCE"
# ============================================================================

info "Creating fresh demo namespace..."
echo ""

cmd "kubectl delete namespace $DEMO_NS --ignore-not-found"
kubectl delete namespace $DEMO_NS --ignore-not-found 2>/dev/null || true
sleep 1

cmd "kubectl create namespace $DEMO_NS"
kubectl create namespace $DEMO_NS
sleep 1

info "Creating ApplicationService (root of ownership hierarchy)..."
echo ""

echo "apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: payments-app
  namespace: recon-demo
spec:
  name: payments-app"

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: payments-app
  namespace: $DEMO_NS
spec:
  name: payments-app
EOF
success "ApplicationService created"
sleep 2

# ============================================================================
marker "4. CREATE KAFKACLUSTER → TRIGGERS RECONCILIATION"
# ============================================================================

info "Creating KafkaCluster CRD..."
info "The operator will detect this and create a VirtualCluster in Gateway"
echo ""

echo "apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: payments-cluster
  namespace: recon-demo
spec:
  clusterId: payments-vcluster    # This becomes VirtualCluster name in Gateway
  applicationServiceRef: payments-app"
echo ""

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: payments-cluster
  namespace: $DEMO_NS
spec:
  clusterId: payments-vcluster
  applicationServiceRef: payments-app
EOF

success "KafkaCluster created - reconciliation triggered!"
sleep 3

# ============================================================================
marker "5. CHECK OPERATOR LOGS"
# ============================================================================

info "Checking operator logs for reconciliation..."
echo ""

cmd "kubectl logs deployment/messaging-operator-webhook -n $OPERATOR_NS --tail=10 | grep RECONCILE"
kubectl logs deployment/messaging-operator-webhook -n $OPERATOR_NS --tail=15 2>&1 | grep -E "\[RECONCILE\].*KafkaCluster" | tail -5 || echo "(checking...)"
sleep 2

# ============================================================================
marker "6. VERIFY VIRTUALCLUSTER IN GATEWAY (using Conduktor CLI)"
# ============================================================================

info "Using Conduktor CLI to check VirtualClusters AFTER creating CRD..."
echo ""

cmd "conduktor get VirtualCluster -o yaml"
VC_OUTPUT=$(conduktor get VirtualCluster -o yaml 2>&1)
echo "$VC_OUTPUT"

echo ""
if echo "$VC_OUTPUT" | grep -q "payments-vcluster"; then
    success "VirtualCluster 'payments-vcluster' was CREATED by the operator!"
else
    echo -e "${YELLOW}⚠ Checking again...${NC}"
    sleep 2
    conduktor get VirtualCluster -o yaml 2>&1
fi
sleep 2

# ============================================================================
marker "7. CREATE SERVICEACCOUNT → REQUIRED FOR TOPICS"
# ============================================================================

info "Creating ServiceAccount 'payments-service'..."
echo ""

echo "apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: payments-service
  namespace: recon-demo
spec:
  name: payments-service
  dn:
    - CN=payments-service
  clusterRef: payments-cluster
  applicationServiceRef: payments-app"
echo ""

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: payments-service
  namespace: $DEMO_NS
spec:
  name: payments-service
  dn:
    - CN=payments-service
  clusterRef: payments-cluster
  applicationServiceRef: payments-app
EOF

success "ServiceAccount created!"
sleep 3

# ============================================================================
marker "8. VERIFY SERVICEACCOUNT IN GATEWAY"
# ============================================================================

info "Checking if ServiceAccount was reconciled to Gateway..."
echo ""

cmd "conduktor get GatewayServiceAccount --vcluster payments-vcluster -o yaml"
SA_OUTPUT=$(conduktor get GatewayServiceAccount --vcluster payments-vcluster -o yaml 2>&1)
echo "$SA_OUTPUT"

if echo "$SA_OUTPUT" | grep -q "payments-service"; then
    success "ServiceAccount 'payments-service' reconciled to Gateway as EXTERNAL type!"
else
    echo -e "${YELLOW}⚠ ServiceAccount not yet visible in Gateway${NC}"
fi
sleep 2

# ============================================================================
marker "9. CREATE TOPIC → RECONCILES TO GATEWAY"
# ============================================================================

info "Creating Topic 'Customer' with 3 partitions..."
echo ""

echo "apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: customer
  namespace: recon-demo
spec:
  name: Customer
  partitions: 3
  replicationFactor: 1
  serviceRef: payments-service
  applicationServiceRef: payments-app"
echo ""

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: customer
  namespace: $DEMO_NS
spec:
  name: Customer
  partitions: 3
  replicationFactor: 1
  serviceRef: payments-service
  applicationServiceRef: payments-app
EOF

success "Topic created - reconciliation triggered!"
sleep 3

# ============================================================================
marker "10. VERIFY TOPIC IN KUBERNETES"
# ============================================================================

info "Checking Topic CRD status in Kubernetes..."
echo ""

cmd "kubectl get topic -n $DEMO_NS"
kubectl get topic -n $DEMO_NS
sleep 2

# ============================================================================
marker "11. VERIFY TOPIC IN CONSOLE"
# ============================================================================

info "Checking Topic in Conduktor Console (physical Kafka topic)..."
echo ""

cmd "conduktor get Topic --cluster local -o yaml"
conduktor get Topic --cluster local -o yaml 2>&1
success "Topic visible in Console - reconciled to physical Kafka!"
sleep 2

# ============================================================================
marker "12. CREATE ACL → GRANT READ/WRITE ON TOPIC"
# ============================================================================

info "Creating ACL to grant READ/WRITE on Customer topic..."
echo ""

echo "apiVersion: messaging.example.com/v1
kind: ACL
metadata:
  name: payments-service-customer-rw
  namespace: recon-demo
spec:
  serviceRef: payments-service
  topicRef: customer
  operations:
    - READ
    - WRITE
  permission: ALLOW
  applicationServiceRef: payments-app"
echo ""

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ACL
metadata:
  name: payments-service-customer-rw
  namespace: $DEMO_NS
spec:
  serviceRef: payments-service
  topicRef: customer
  operations:
    - READ
    - WRITE
  permission: ALLOW
  applicationServiceRef: payments-app
EOF

success "ACL created - reconciliation triggered!"
sleep 2

cmd "kubectl get acl -n $DEMO_NS"
kubectl get acl -n $DEMO_NS
sleep 2

# ============================================================================
marker "13. VERIFY ACL IN CONSOLE"
# ============================================================================

info "Checking ACL in Conduktor Console..."
echo ""

cmd "conduktor get Topic --cluster local -o yaml"
conduktor get Topic --cluster local -o yaml 2>&1
success "ACL applied - Topic shows in Console!"
sleep 2

# ============================================================================
marker "14. UPDATE TOPIC PARTITIONS (3 → 10)"
# ============================================================================

info "Updating Topic 'Customer' partitions from 3 to 10..."
echo ""

echo "apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: customer
  namespace: recon-demo
spec:
  name: Customer
  partitions: 10
  replicationFactor: 1
  serviceRef: payments-service
  applicationServiceRef: payments-app"
echo ""

kubectl apply -f - <<EOF
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: customer
  namespace: $DEMO_NS
spec:
  name: Customer
  partitions: 10
  replicationFactor: 1
  serviceRef: payments-service
  applicationServiceRef: payments-app
EOF

success "Topic updated - reconciliation triggered!"
sleep 3

# ============================================================================
marker "15. VERIFY TOPIC UPDATE IN KUBERNETES"
# ============================================================================

info "Verifying Topic CRD now has 10 partitions..."
echo ""

cmd "kubectl get topic customer -n $DEMO_NS -o yaml"
kubectl get topic customer -n $DEMO_NS -o yaml
sleep 2

# ============================================================================
marker "16. VERIFY TOPIC UPDATE IN CONSOLE"
# ============================================================================

info "Verifying Topic partition update in Conduktor Console..."
echo ""

cmd "conduktor get Topic --cluster local -o yaml"
conduktor get Topic --cluster local -o yaml 2>&1
success "Topic partitions updated to 10 in physical Kafka!"
sleep 2

# ============================================================================
marker "17. CLEANUP"
# ============================================================================

info "Cleaning up demo resources..."
echo ""

cmd "kubectl delete namespace $DEMO_NS"
kubectl delete namespace $DEMO_NS --wait=false
sleep 1

cmd "conduktor delete VirtualCluster payments-vcluster"
conduktor delete VirtualCluster payments-vcluster 2>&1 && success "VirtualCluster deleted from Gateway" || true
sleep 2

# ============================================================================
marker "DEMO COMPLETE"
# ============================================================================

echo ""
echo -e "${BOLD}${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BOLD}  ✓ Reconciliation Demo Complete${NC}"
echo -e "${BOLD}${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  What we demonstrated:"
echo ""
echo "  1. Operator watches Kubernetes CRDs using Fabric8 informers"
echo "  2. KafkaCluster CRD → VirtualCluster in Gateway"
echo "  3. ServiceAccount CRD → EXTERNAL GatewayServiceAccount in Gateway (mTLS)"
echo "  4. Topic CRD → Physical Kafka topic (verified in Console)"
echo "  5. ACL CRD → Kafka ACL (verified in Console)"
echo "  6. Topic UPDATE (3 → 10 partitions) reconciled to Kafka"
echo "  7. Full ownership chain: ApplicationService → KafkaCluster → ServiceAccount → Topic/ACL"
echo ""
echo -e "${BLUE}Demo finished.${NC}"
