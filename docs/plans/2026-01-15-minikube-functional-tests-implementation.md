# Minikube Functional Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement end-to-end functional tests for the Messaging Operator using Helm, Bats, and Java against a real Minikube cluster.

**Architecture:** Three-layer testing strategy - Bash scripts for quick validation, Bats for structured kubectl tests, Java/Fabric8 for complex scenarios reusing TestDataBuilder. Helm chart manages deployment with templated CRDs and webhook config.

**Tech Stack:** Minikube, Helm 3, Bats (bats-core, bats-support, bats-assert), JUnit 5, Fabric8 Kubernetes Client, AssertJ, Awaitility

---

## Phase 1: Project Structure & Helm Chart Foundation

- [x] Create directory structure `[FUNC_01 §1.1]`
  - [x] **Objectives:**
    - [x] Create functional-tests root directory
    - [x] Create helm/messaging-operator with templates/crds and tests subdirs
    - [x] Create scripts, bats, fixtures directories
    - [x] Create fixtures subdirs: valid, invalid, tenant-a, tenant-b, ownership-chain, ha-test
  - [x] **Tests:**
    - [x] Verify structure with `find functional-tests -type d | sort`
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A
  - [x] **Learning:**
    - Helm 3 auto-installs CRDs from templates/crds before other resources

- [x] Create Helm Chart base files `[FUNC_01 §1.2]`
  - [x] **Objectives:**
    - [x] Create Chart.yaml with apiVersion v2, name, version, appVersion
    - [x] Create values.yaml with production defaults (2 replicas, 256Mi-512Mi memory)
    - [x] Create values-minikube.yaml with local overrides (1 replica, pullPolicy: Never)
  - [x] **Tests:**
    - [x] helmLintShouldPassWithNoErrors
  - [x] **Metadata:**
    - Task duration: ~2 min (actual)
    - Tests duration: <1s
  - [x] **Learning:**
    - Use pullPolicy: Never in Minikube to use locally-built images

- [x] Create Helm template helpers `[FUNC_01 §1.3]`
  - [x] **Objectives:**
    - [x] Create _helpers.tpl with name, fullname, labels, selectorLabels functions
    - [x] Add chart label helper for helm.sh/chart annotation
    - [x] Add webhookServiceName and namespace helpers
  - [x] **Tests:**
    - [x] helmTemplateShouldRenderLabelsCorrectly
  - [x] **Metadata:**
    - Task duration: ~2 min (actual)
    - Tests duration: <1s
  - [x] **Learning:**
    - Use nindent for proper YAML indentation in templates
    - Added webhookServiceFQDN and webhookServiceFullFQDN for TLS SAN

---

## Phase 2: Helm Kubernetes Resource Templates

- [x] Create namespace template `[FUNC_02 §2.1]`
  - [x] **Objectives:**
    - [x] Create templates/namespace.yaml with namespace resource
    - [x] Include standard labels from helpers
  - [x] **Tests:**
    - [x] helmTemplateNamespaceShouldRenderOperatorSystem
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: <1s

- [x] Create webhook deployment template `[FUNC_02 §2.2]`
  - [x] **Objectives:**
    - [x] Create templates/webhook-deployment.yaml
    - [x] Template replicas, image, resources from values
    - [x] Add TLS volume mount from secret
    - [x] Configure liveness and readiness probes with HTTPS
    - [x] Set WEBHOOK_PORT environment variable
  - [x] **Tests:**
    - [x] helmTemplateDeploymentShouldHaveCorrectReplicas
    - [x] helmTemplateDeploymentShouldMountTlsSecret
    - [x] helmTemplateDeploymentShouldHaveHealthProbes
  - [x] **Metadata:**
    - Task duration: ~2 min (actual)
    - Tests duration: <1s
  - [x] **Learning:**
    - Use scheme: HTTPS for probes when container serves TLS

- [x] Create webhook service template `[FUNC_02 §2.3]`
  - [x] **Objectives:**
    - [x] Create templates/webhook-service.yaml with ClusterIP type
    - [x] Map port 443 to targetPort 8443
    - [x] Use selector labels from helpers
  - [x] **Tests:**
    - [x] helmTemplateServiceShouldExposePort443
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: <1s

- [x] Create TLS secret template `[FUNC_02 §2.4]`
  - [x] **Objectives:**
    - [x] Create templates/tls-secret.yaml with conditional logic
    - [x] When tls.generate=true, create placeholder (replaced by script)
    - [x] When tls.generate=false, require tls.cert, tls.key, tls.caCert
    - [x] Set type: kubernetes.io/tls
  - [x] **Tests:**
    - [x] helmTemplateTlsSecretShouldRequireCertsWhenGenerateFalse
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: <1s
  - [x] **Learning:**
    - Use Helm `required` function for mandatory values

- [x] Create ValidatingWebhookConfiguration template `[FUNC_02 §2.5]`
  - [x] **Objectives:**
    - [x] Create templates/webhook-config.yaml
    - [x] Loop over webhookConfig.resources to generate webhook entries
    - [x] Configure rules for CREATE, UPDATE, DELETE operations
    - [x] Set failurePolicy and timeoutSeconds from values
    - [x] Add caBundle placeholder for script injection
  - [x] **Tests:**
    - [x] helmTemplateWebhookConfigShouldHave5Webhooks
    - [x] helmTemplateWebhookConfigShouldHaveFailurePolicyFail
  - [x] **Metadata:**
    - Task duration: ~2 min (actual)
    - Tests duration: <1s
  - [x] **Learning:**
    - ValidatingWebhookConfiguration is cluster-scoped (no namespace)

---

## Phase 3: CRD Templates

- [x] Create ApplicationService CRD `[FUNC_03 §3.1]`
  - [x] **Objectives:**
    - [x] Create templates/crds/applicationservice.yaml
    - [x] Define spec.name as required string field
    - [x] Add status subresource with phase and message
    - [x] Set scope: Namespaced with shortName appsvc
  - [x] **Tests:**
    - [x] kubectlGetCrdApplicationservicesShouldExist
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A (tested during deployment)

- [x] Create VirtualCluster CRD `[FUNC_03 §3.2]`
  - [x] **Objectives:**
    - [x] Create templates/crds/virtualcluster.yaml
    - [x] Define spec.clusterId and spec.applicationServiceRef as required
    - [x] Add status subresource
    - [x] Set shortName vc
  - [x] **Tests:**
    - [x] kubectlGetCrdVirtualclustersShouldExist
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A

- [x] Create ServiceAccount CRD `[FUNC_03 §3.3]`
  - [x] **Objectives:**
    - [x] Create templates/crds/serviceaccount.yaml
    - [x] Define spec.name, spec.dn (array), spec.clusterRef, spec.applicationServiceRef
    - [x] Add status subresource
    - [x] Set shortName sa
  - [x] **Tests:**
    - [x] kubectlGetCrdServiceaccountsShouldExist
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A

- [x] Create Topic CRD `[FUNC_03 §3.4]`
  - [x] **Objectives:**
    - [x] Create templates/crds/topic.yaml
    - [x] Define spec.name, spec.serviceRef, spec.applicationServiceRef as required
    - [x] Add spec.partitions (default 6), spec.replicationFactor (default 3)
    - [x] Add spec.config as additionalProperties map
    - [x] Add status subresource
  - [x] **Tests:**
    - [x] kubectlGetCrdTopicsShouldExist
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A

- [x] Create ACL CRD `[FUNC_03 §3.5]`
  - [x] **Objectives:**
    - [x] Create templates/crds/acl.yaml
    - [x] Define spec.serviceRef, spec.applicationServiceRef as required
    - [x] Add spec.topicRef, spec.consumerGroupRef as optional
    - [x] Add spec.operations array with enum validation
    - [x] Add spec.host (default "*"), spec.permission (enum ALLOW/DENY)
    - [x] Add status subresource
  - [x] **Tests:**
    - [x] kubectlGetCrdAclsShouldExist
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A

- [x] Create ConsumerGroup CRD `[FUNC_03 §3.6]`
  - [x] **Objectives:**
    - [x] Create templates/crds/consumergroup.yaml
    - [x] Define spec.name, spec.serviceRef, spec.applicationServiceRef as required
    - [x] Add spec.patternType enum (LITERAL, PREFIXED) with default LITERAL
    - [x] Add status subresource
    - [x] Set shortName cg
  - [x] **Tests:**
    - [x] kubectlGetCrdConsumergroupsShouldExist
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A

- [x] Create Helm test pod `[FUNC_03 §3.7]`
  - [x] **Objectives:**
    - [x] Create tests/test-webhook-health.yaml
    - [x] Use busybox wget to check webhook health endpoint
    - [x] Add helm.sh/hook: test annotation
    - [x] Add hook-delete-policy: hook-succeeded
  - [x] **Tests:**
    - [x] helmTestShouldPassAfterDeployment
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A

---

## Phase 4: Shell Scripts

- [x] Create setup-minikube.sh `[FUNC_04 §4.1]`
  - [x] **Objectives:**
    - [x] Accept CLUSTER_NAME, FRESH_CLUSTER, NAMESPACE env vars
    - [x] Check if cluster exists with minikube status
    - [x] Delete existing cluster if FRESH_CLUSTER=true
    - [x] Reuse existing cluster and create fresh namespace if FRESH_CLUSTER=false
    - [x] Create new cluster with docker driver, 2 CPUs, 4GB memory
    - [x] Write namespace to .test-namespace file
  - [x] **Tests:**
    - [x] setupMinikubeShouldCreateCluster
    - [x] setupMinikubeShouldReuseExistingCluster
    - [x] setupMinikubeShouldWriteNamespaceFile
  - [x] **Metadata:**
    - Task duration: ~3 min (actual)
    - Tests duration: ~90s (cluster creation)
  - [x] **Learning:**
    - Use minikube profile to switch between clusters

- [x] Create generate-certs.sh `[FUNC_04 §4.2]`
  - [x] **Objectives:**
    - [x] Generate CA key and self-signed certificate
    - [x] Generate server key and CSR with SAN for service DNS names
    - [x] Sign server certificate with CA
    - [x] Export base64-encoded values for Helm
    - [x] Write values-tls.yaml file for deploy script
  - [x] **Tests:**
    - [x] generateCertsShouldCreateCaKey
    - [x] generateCertsShouldCreateServerCert
    - [x] generateCertsShouldWriteValuesTlsYaml
  - [x] **Metadata:**
    - Task duration: ~3 min (actual)
    - Tests duration: <1s
  - [x] **Learning:**
    - SAN must include service.namespace.svc.cluster.local for K8s DNS

- [x] Create deploy.sh `[FUNC_04 §4.3]`
  - [x] **Objectives:**
    - [x] Build Java application with mvn package -DskipTests
    - [x] Build Docker image in Minikube context (eval minikube docker-env)
    - [x] Call generate-certs.sh for TLS certificates
    - [x] Deploy with helm upgrade --install using values-minikube.yaml and values-tls.yaml
    - [x] Wait for deployment ready with kubectl wait
    - [x] Verify endpoints exist
  - [x] **Tests:**
    - [x] deployShouldBuildDockerImage
    - [x] deployShouldInstallHelmRelease
    - [x] deployShouldWaitForWebhookReady
  - [x] **Metadata:**
    - Task duration: ~4 min (actual)
    - Tests duration: ~60s
  - [x] **Learning:**
    - Use helm upgrade --install for idempotent deployments

- [x] Create teardown.sh `[FUNC_04 §4.4]`
  - [x] **Objectives:**
    - [x] Uninstall Helm release if exists
    - [x] Delete all CRD instances in namespace
    - [x] Optionally delete namespace (DELETE_NAMESPACE=true)
    - [x] Optionally delete cluster (DELETE_CLUSTER=true)
    - [x] Cleanup .test-namespace and .certs files
  - [x] **Tests:**
    - [x] teardownShouldUninstallHelmRelease
    - [x] teardownShouldCleanupFiles
  - [x] **Metadata:**
    - Task duration: ~2 min (actual)
    - Tests duration: ~10s

- [x] Create run-tests.sh `[FUNC_04 §4.5]`
  - [x] **Objectives:**
    - [x] Parse CLI arguments: --fresh-cluster, --skip-deploy, --skip-tests, --bats-only, --java-only, --test-filter, --cleanup
    - [x] Call setup-minikube.sh with FRESH_CLUSTER flag
    - [x] Call deploy.sh unless --skip-deploy
    - [x] Run Bats tests with optional filter unless --java-only
    - [x] Run Java E2E tests with Maven unless --bats-only
    - [x] Call teardown.sh if --cleanup
    - [x] Print summary with pass/fail counts
  - [x] **Tests:**
    - [x] runTestsShouldParseArguments
    - [x] runTestsShouldRunBatsTests
    - [x] runTestsShouldRunJavaTests
    - [x] runTestsShouldPrintSummary
  - [x] **Metadata:**
    - Task duration: ~5 min (actual)
    - Tests duration: ~5 min (full suite)
  - [x] **Learning:**
    - Use getopts or manual parsing for long options in bash

---

## Phase 5: Bats Test Infrastructure

- [x] Create test_helper.bash `[FUNC_05 §5.1]`
  - [x] **Objectives:**
    - [x] Load bats-support and bats-assert with fallback paths
    - [x] Provide minimal fallback assertions if libraries not found
    - [x] Read NAMESPACE from .test-namespace file
    - [x] Implement wait_for function with configurable timeout
    - [x] Implement apply_fixture, delete_fixture helper functions
    - [x] Implement expect_rejection for testing webhook rejections
    - [x] Implement resource_exists, get_resource helpers
    - [x] Implement cleanup_test_resources and wait_for_webhook
  - [x] **Tests:**
    - [x] testHelperShouldLoadBatsLibraries
    - [x] waitForShouldReturnSuccessWhenConditionMet
    - [x] waitForShouldTimeoutWhenConditionNotMet
  - [x] **Metadata:**
    - Task duration: ~5 min (actual)
    - Tests duration: N/A (infrastructure)
  - [x] **Learning:**
    - Bats runs each @test in a subshell; use setup_file for shared state
    - Added helper functions to create resources inline (create_app_service, etc.)

---

## Phase 6: Bats Deployment Tests

- [x] Create 01_deployment.bats `[FUNC_06 §6.1]`
  - [x] **Objectives:**
    - [x] Test webhook deployment exists
    - [x] Test webhook deployment has ready replicas >= 1
    - [x] Test webhook service exists
    - [x] Test webhook service has endpoints with IP
    - [x] Test webhook pod is in Running phase
    - [x] Test all 6 CRDs are installed
    - [x] Test ValidatingWebhookConfiguration exists
  - [x] **Tests:**
    - [x] webhookDeploymentExists
    - [x] webhookDeploymentHasReadyReplicas
    - [x] webhookServiceExists
    - [x] webhookServiceHasEndpoints
    - [x] webhookPodIsRunning
    - [x] crdsAreInstalled
    - [x] validatingWebhookConfigurationExists
  - [x] **Metadata:**
    - Task duration: ~3 min (actual)
    - Tests duration: ~10s
  - [x] **Learning:**
    - Use jsonpath for precise kubectl output extraction

---

## Phase 7: Bats Webhook Admission Tests

- [x] Create 02_webhook_admission.bats `[FUNC_07 §7.1]`
  - [x] **Objectives:**
    - [x] Setup: wait for webhook ready, cleanup test resources
    - [x] Test webhook accepts valid ApplicationService
    - [x] Test webhook accepts valid VirtualCluster with existing ApplicationService
    - [x] Test webhook accepts valid ServiceAccount with prereqs
    - [x] Test webhook accepts valid Topic with prereqs
    - [x] Test webhook accepts valid ACL
    - [x] Test webhook accepts valid ConsumerGroup
    - [x] Test webhook rejects ApplicationService with missing name
    - [x] Test webhook rejects VirtualCluster with non-existent ApplicationService ref
    - [x] Teardown: cleanup test resources
  - [x] **Tests:**
    - [x] webhookAcceptsValidApplicationService
    - [x] webhookAcceptsValidVirtualClusterWithExistingApplicationService
    - [x] webhookAcceptsValidServiceAccount
    - [x] webhookAcceptsValidTopic
    - [x] webhookAcceptsValidAcl
    - [x] webhookAcceptsValidConsumerGroup
    - [x] webhookRejectsApplicationServiceWithMissingName
    - [x] webhookRejectsVirtualClusterWithNonExistentApplicationServiceRef
  - [x] **Metadata:**
    - Task duration: ~4 min (actual)
    - Tests duration: ~30s
  - [x] **Learning:**
    - Create resources in dependency order; clean up in reverse order

---

## Phase 8: Bats Ownership Chain Tests

- [x] Create 03_ownership_chain.bats `[FUNC_08 §8.1]`
  - [x] **Objectives:**
    - [x] Setup: wait for webhook, cleanup resources
    - [x] Test full ownership chain (App -> VC -> SA -> Topic) accepted
    - [x] Test VirtualCluster requires valid ApplicationService reference
    - [x] Test ServiceAccount requires valid VirtualCluster reference
    - [x] Test Topic requires valid ServiceAccount reference
    - [x] Test cannot change applicationServiceRef on update (immutability)
    - [x] Test deleting parent with existing children fails
    - [x] Each test starts with cleanup_test_resources for isolation
  - [x] **Tests:**
    - [x] fullOwnershipChainValidHierarchyAccepted
    - [x] virtualClusterRequiresValidApplicationServiceReference
    - [x] serviceAccountRequiresValidVirtualClusterReference
    - [x] topicRequiresValidServiceAccountReference
    - [x] cannotChangeApplicationServiceRefOnUpdate
    - [x] deletingParentWithExistingChildrenFails
  - [x] **Metadata:**
    - Task duration: ~4 min (actual)
    - Tests duration: ~45s
  - [x] **Learning:**
    - Use setup() for per-test cleanup, setup_file() for one-time setup

---

## Phase 9: Bats Multi-Tenant Tests

- [x] Create 04_multi_tenant.bats `[FUNC_09 §9.1]`
  - [x] **Objectives:**
    - [x] Setup: create tenant-a and tenant-b ApplicationServices
    - [x] Setup: create tenant-a VirtualCluster and ServiceAccount
    - [x] Test tenant A can create resources under own ApplicationService
    - [x] Test tenant B cannot reference tenant A's VirtualCluster
    - [x] Test tenant B cannot reference tenant A's ServiceAccount
    - [x] Test tenant B can create own isolated resources
  - [x] **Tests:**
    - [x] tenantACanCreateResourcesUnderOwnApplicationService
    - [x] tenantBCannotReferenceTenantAVirtualCluster
    - [x] tenantBCannotReferenceTenantAServiceAccount
    - [x] tenantBCanCreateOwnIsolatedResources
  - [x] **Metadata:**
    - Task duration: ~4 min (actual)
    - Tests duration: ~30s
  - [x] **Learning:**
    - Multi-tenant isolation is critical security boundary; test thoroughly

---

## Phase 10: Bats HA Failover Tests

- [x] Create 05_ha_failover.bats `[FUNC_10 §10.1]`
  - [x] **Objectives:**
    - [x] Setup: scale deployment to 2 replicas, wait for both ready
    - [x] Test webhook has 2 ready replicas
    - [x] Test webhook survives single pod failure (delete pod, verify ops work)
    - [x] Test webhook recovers after pod restart (wait for 2 replicas again)
    - [x] Test rolling restart maintains availability (rollout restart, verify ops during)
    - [x] Teardown: scale back to 1 replica
  - [x] **Tests:**
    - [x] webhookHas2ReadyReplicas
    - [x] webhookSurvivesSinglePodFailure
    - [x] webhookRecoversAfterPodRestart
    - [x] rollingRestartMaintainsAvailability
  - [x] **Metadata:**
    - Task duration: ~4 min (actual)
    - Tests duration: ~120s
  - [x] **Learning:**
    - Use --wait=false on pod delete for non-blocking operation

---

## Phase 11: Test Fixtures - Valid Resources

- [x] Create valid resource fixtures `[FUNC_11 §11.1]`
  - [x] **Objectives:**
    - [x] Create fixtures/valid/application-service.yaml (name: test-app)
    - [x] Create fixtures/valid/virtual-cluster.yaml (refs test-app)
    - [x] Create fixtures/valid/service-account.yaml (refs test-app, test-vc)
    - [x] Create fixtures/valid/topic.yaml (refs test-app, test-sa)
    - [x] Create fixtures/valid/acl.yaml (refs test-app, test-sa, test-topic)
    - [x] Create fixtures/valid/consumer-group.yaml (refs test-app, test-sa)
  - [x] **Tests:**
    - [x] N/A - fixtures are tested by Bats tests
  - [x] **Metadata:**
    - Task duration: ~2 min (actual)
    - Tests duration: N/A

---

## Phase 12: Test Fixtures - Invalid Resources

- [x] Create invalid resource fixtures `[FUNC_12 §12.1]`
  - [x] **Objectives:**
    - [x] Create fixtures/invalid/missing-appname.yaml (ApplicationService without name)
    - [x] Create fixtures/invalid/nonexistent-appservice-ref.yaml (VC refs missing app)
    - [x] Create fixtures/invalid/vc-without-appservice.yaml (orphan VC)
    - [x] Create fixtures/invalid/sa-without-vc.yaml (SA refs missing VC)
    - [x] Create fixtures/invalid/topic-without-sa.yaml (Topic refs missing SA)
    - [x] Create fixtures/invalid/topic-changed-owner.yaml (UPDATE with changed appRef)
  - [x] **Tests:**
    - [x] N/A - fixtures are tested by Bats tests
  - [x] **Metadata:**
    - Task duration: ~2 min (actual)
    - Tests duration: N/A
  - [x] **Learning:**
    - Invalid fixtures must trigger specific error messages for assertion

---

## Phase 13: Test Fixtures - Tenant Isolation

- [x] Create tenant-a fixtures `[FUNC_13 §13.1]`
  - [x] **Objectives:**
    - [x] Create fixtures/tenant-a/application-service.yaml (name: tenant-a-app)
    - [x] Create fixtures/tenant-a/virtual-cluster.yaml (refs tenant-a-app)
    - [x] Create fixtures/tenant-a/service-account.yaml (refs tenant-a-app, tenant-a-vc)
    - [x] Create fixtures/tenant-a/topic.yaml (refs tenant-a-app, tenant-a-sa)
  - [x] **Tests:**
    - [x] N/A - fixtures are tested by Bats tests
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A

- [x] Create tenant-b fixtures `[FUNC_13 §13.2]`
  - [x] **Objectives:**
    - [x] Create fixtures/tenant-b/application-service.yaml (name: tenant-b-app)
    - [x] Create fixtures/tenant-b/virtual-cluster.yaml (refs tenant-b-app)
    - [x] Create fixtures/tenant-b/service-account.yaml (refs tenant-b-app, tenant-b-vc)
    - [x] Create fixtures/tenant-b/topic.yaml (refs tenant-b-app, tenant-b-sa)
    - [x] Create fixtures/tenant-b/cross-tenant-topic.yaml (illegal cross-ref to tenant-a SA)
    - [x] Create fixtures/tenant-b/cross-tenant-vc.yaml (illegal cross-ref to tenant-a app)
  - [x] **Tests:**
    - [x] N/A - fixtures are tested by Bats tests
  - [x] **Metadata:**
    - Task duration: ~2 min (actual)
    - Tests duration: N/A

---

## Phase 14: Test Fixtures - Ownership Chain & HA

- [x] Create ownership-chain fixtures `[FUNC_14 §14.1]`
  - [x] **Objectives:**
    - [x] Create fixtures/ownership-chain/full-hierarchy.yaml with all 6 resources in single file
    - [x] Use YAML document separators (---)
    - [x] Resources: chain-app, chain-vc, chain-sa, chain-topic, chain-cg, chain-acl
  - [x] **Tests:**
    - [x] N/A - fixtures are tested by Bats tests
  - [x] **Metadata:**
    - Task duration: ~1 min (actual)
    - Tests duration: N/A
  - [x] **Learning:**
    - Multi-document YAML allows atomic application of related resources
    - Included all 6 CRD types for comprehensive ownership chain testing

- [x] Create ha-test fixtures `[FUNC_14 §14.2]`
  - [x] **Objectives:**
    - [x] Create fixtures/ha-test/application-service.yaml (ha-test-app)
    - [x] Create fixtures/ha-test/virtual-cluster.yaml (ha-test-vc)
    - [x] Create fixtures/ha-test/service-account.yaml (ha-test-sa)
    - [x] Create fixtures/ha-test/topic-1.yaml through topic-5.yaml
  - [x] **Tests:**
    - [x] N/A - fixtures are tested by Bats tests
  - [x] **Metadata:**
    - Task duration: ~2 min (actual)
    - Tests duration: N/A

---

## Phase 15: Maven Configuration for E2E Tests

- [ ] Add E2E Maven profile `[FUNC_15 §15.1]`
  - [ ] **Objectives:**
    - [ ] Add `<profiles>` section to pom.xml
    - [ ] Create profile id="e2e" with skipUTs=true property
    - [ ] Configure maven-failsafe-plugin to include **/*E2ETest.java
    - [ ] Exclude **/*IT.java from E2E profile
    - [ ] Pass TEST_NAMESPACE as system property
    - [ ] Configure maven-surefire-plugin to skip unit tests in E2E profile
  - [ ] **Tests:**
    - [ ] mvnVerifyPe2eShouldRunE2ETests
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: N/A

- [ ] Add Awaitility dependency `[FUNC_15 §15.2]`
  - [ ] **Objectives:**
    - [ ] Add org.awaitility:awaitility:4.2.0 with test scope
  - [ ] **Tests:**
    - [ ] awaitilityImportShouldResolve
  - [ ] **Metadata:**
    - Task duration: ~1 min
    - Tests duration: N/A
  - [ ] **Learning:**
    - Awaitility provides fluent API for polling async conditions

---

## Phase 16: Java E2E Test Infrastructure

- [ ] Create E2ETest annotation `[FUNC_16 §16.1]`
  - [ ] **Objectives:**
    - [ ] Create @E2ETest annotation in e2e package
    - [ ] Add @Tag("e2e") for test filtering
    - [ ] Add @TestInstance(PER_CLASS) for shared setup
    - [ ] Add @Retention(RUNTIME), @Target(TYPE)
  - [ ] **Tests:**
    - [ ] e2eTestAnnotationShouldBeRetainedAtRuntime
  - [ ] **Metadata:**
    - Task duration: ~2 min
    - Tests duration: N/A

- [ ] Create E2ETestBase `[FUNC_16 §16.2]`
  - [ ] **Objectives:**
    - [ ] Create abstract E2ETestBase class
    - [ ] Implement setupCluster() with Fabric8 Config.autoConfigure(null)
    - [ ] Implement resolveNamespace() checking env, property, file in order
    - [ ] Implement waitForWebhookReady() with Awaitility
    - [ ] Implement webhookHasEndpoints() checking endpoints subsets
    - [ ] Implement assertRejectedWith(Executable, String) helper
    - [ ] Implement scaleWebhook(int replicas) with wait
    - [ ] Implement getWebhookPods() returning pod names
    - [ ] Implement cleanupTestResources() deleting all CRD instances
    - [ ] Add teardownCluster() to close client
  - [ ] **Tests:**
    - [ ] e2eTestBaseShouldConnectToCluster
    - [ ] e2eTestBaseShouldResolveNamespaceFromEnv
    - [ ] e2eTestBaseShouldWaitForWebhook
  - [ ] **Metadata:**
    - Task duration: ~10 min
    - Tests duration: N/A (base class)
  - [ ] **Learning:**
    - Config.autoConfigure(null) uses current kubectl context

---

## Phase 17: Java E2E Tests

- [ ] Create OwnershipChainE2ETest `[FUNC_17 §17.1]`
  - [ ] **Objectives:**
    - [ ] Annotate with @E2ETest, extend E2ETestBase
    - [ ] Add @BeforeEach and @AfterEach calling cleanupTestResources()
    - [ ] Test fullOwnershipChain_validHierarchy_accepted
    - [ ] Test topic_withNonExistentVirtualCluster_rejected
    - [ ] Test virtualCluster_withNonExistentApplicationService_rejected
    - [ ] Use TestDataBuilder for all resource creation
    - [ ] Verify resources exist with k8sClient.resources().get()
  - [ ] **Tests:**
    - [ ] fullOwnershipChainValidHierarchyAccepted
    - [ ] topicWithNonExistentVirtualClusterRejected
    - [ ] virtualClusterWithNonExistentApplicationServiceRejected
  - [ ] **Metadata:**
    - Task duration: ~8 min
    - Tests duration: ~30s
  - [ ] **Learning:**
    - Reuse TestDataBuilder from existing IT tests for consistency

- [ ] Create MultiTenantE2ETest `[FUNC_17 §17.2]`
  - [ ] **Objectives:**
    - [ ] Annotate with @E2ETest, extend E2ETestBase
    - [ ] Create tenant A and B apps in @BeforeEach
    - [ ] Create tenant A VC and SA in setup
    - [ ] Test tenantB_cannotReferenceTenantA_serviceAccount
    - [ ] Test tenantB_canCreateOwnResources
    - [ ] Assert rejection contains "does not belong to ApplicationService"
  - [ ] **Tests:**
    - [ ] tenantBCannotReferenceTenantAServiceAccount
    - [ ] tenantBCanCreateOwnResources
  - [ ] **Metadata:**
    - Task duration: ~6 min
    - Tests duration: ~20s

- [ ] Create HAFailoverE2ETest `[FUNC_17 §17.3]`
  - [ ] **Objectives:**
    - [ ] Annotate with @E2ETest, extend E2ETestBase
    - [ ] Scale back to 1 replica in @AfterEach
    - [ ] Test webhookRemainsAvailable_duringSinglePodFailure
    - [ ] Scale to 2 replicas, delete first pod, verify operations succeed
    - [ ] Use Awaitility for timing-sensitive waits
  - [ ] **Tests:**
    - [ ] webhookRemainsAvailableDuringSinglePodFailure
  - [ ] **Metadata:**
    - Task duration: ~6 min
    - Tests duration: ~60s
  - [ ] **Learning:**
    - HA tests require careful timing; allow grace period after pod deletion

---

## Phase 18: Finalization

- [ ] Update .gitignore `[FUNC_18 §18.1]`
  - [ ] **Objectives:**
    - [ ] Add functional-tests/.test-namespace
    - [ ] Add functional-tests/.certs/
  - [ ] **Tests:**
    - [ ] gitStatusShouldNotShowTestNamespaceFile
  - [ ] **Metadata:**
    - Task duration: ~1 min
    - Tests duration: N/A

- [ ] Final validation `[FUNC_18 §18.2]`
  - [ ] **Objectives:**
    - [ ] Run helm lint and verify no errors
    - [ ] Verify all scripts have execute permission
    - [ ] Verify all bats files have shebang
    - [ ] Run mvn compile test-compile
    - [ ] Run full test suite if cluster available
  - [ ] **Tests:**
    - [ ] helmLintShouldPassWithNoErrors
    - [ ] allScriptsShouldBeExecutable
    - [ ] allBatsFilesShouldHaveShebang
    - [ ] mavenCompileShouldSucceed
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: ~5 min (full validation)

---

## Summary

| Phase | Tasks | Estimated Duration |
|-------|-------|-------------------|
| Phase 1: Structure & Foundation | 3 | ~10 min |
| Phase 2: K8s Resource Templates | 5 | ~17 min |
| Phase 3: CRD Templates | 7 | ~22 min |
| Phase 4: Shell Scripts | 5 | ~28 min |
| Phase 5: Bats Infrastructure | 1 | ~8 min |
| Phase 6: Deployment Tests | 1 | ~5 min |
| Phase 7: Webhook Tests | 1 | ~8 min |
| Phase 8: Ownership Tests | 1 | ~8 min |
| Phase 9: Multi-Tenant Tests | 1 | ~6 min |
| Phase 10: HA Tests | 1 | ~8 min |
| Phase 11-14: Fixtures | 5 | ~18 min |
| Phase 15: Maven Config | 2 | ~6 min |
| Phase 16: Java Infrastructure | 2 | ~12 min |
| Phase 17: Java E2E Tests | 3 | ~20 min |
| Phase 18: Finalization | 2 | ~6 min |
| **Total** | **40** | **~182 min** |

---

## Execution Commands

```bash
# Full fresh run (CI-style)
./functional-tests/scripts/run-tests.sh --fresh-cluster --cleanup

# Quick local iteration (reuse cluster)
./functional-tests/scripts/run-tests.sh

# Run specific test suites
./functional-tests/scripts/run-tests.sh --bats-only --test-filter "02_webhook"
./functional-tests/scripts/run-tests.sh --java-only

# Deploy only for manual exploration
./functional-tests/scripts/run-tests.sh --skip-tests
```
