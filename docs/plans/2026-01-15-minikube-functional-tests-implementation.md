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

- [ ] Create namespace template `[FUNC_02 §2.1]`
  - [ ] **Objectives:**
    - [ ] Create templates/namespace.yaml with namespace resource
    - [ ] Include standard labels from helpers
  - [ ] **Tests:**
    - [ ] helmTemplateNamespaceShouldRenderOperatorSystem
  - [ ] **Metadata:**
    - Task duration: ~2 min
    - Tests duration: <1s

- [ ] Create webhook deployment template `[FUNC_02 §2.2]`
  - [ ] **Objectives:**
    - [ ] Create templates/webhook-deployment.yaml
    - [ ] Template replicas, image, resources from values
    - [ ] Add TLS volume mount from secret
    - [ ] Configure liveness and readiness probes with HTTPS
    - [ ] Set WEBHOOK_PORT environment variable
  - [ ] **Tests:**
    - [ ] helmTemplateDeploymentShouldHaveCorrectReplicas
    - [ ] helmTemplateDeploymentShouldMountTlsSecret
    - [ ] helmTemplateDeploymentShouldHaveHealthProbes
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: <1s
  - [ ] **Learning:**
    - Use scheme: HTTPS for probes when container serves TLS

- [ ] Create webhook service template `[FUNC_02 §2.3]`
  - [ ] **Objectives:**
    - [ ] Create templates/webhook-service.yaml with ClusterIP type
    - [ ] Map port 443 to targetPort 8443
    - [ ] Use selector labels from helpers
  - [ ] **Tests:**
    - [ ] helmTemplateServiceShouldExposePort443
  - [ ] **Metadata:**
    - Task duration: ~2 min
    - Tests duration: <1s

- [ ] Create TLS secret template `[FUNC_02 §2.4]`
  - [ ] **Objectives:**
    - [ ] Create templates/tls-secret.yaml with conditional logic
    - [ ] When tls.generate=true, create placeholder (replaced by script)
    - [ ] When tls.generate=false, require tls.cert, tls.key, tls.caCert
    - [ ] Set type: kubernetes.io/tls
  - [ ] **Tests:**
    - [ ] helmTemplateTlsSecretShouldRequireCertsWhenGenerateFalse
  - [ ] **Metadata:**
    - Task duration: ~3 min
    - Tests duration: <1s
  - [ ] **Learning:**
    - Use Helm `required` function for mandatory values

- [ ] Create ValidatingWebhookConfiguration template `[FUNC_02 §2.5]`
  - [ ] **Objectives:**
    - [ ] Create templates/webhook-config.yaml
    - [ ] Loop over webhookConfig.resources to generate webhook entries
    - [ ] Configure rules for CREATE, UPDATE, DELETE operations
    - [ ] Set failurePolicy and timeoutSeconds from values
    - [ ] Add caBundle placeholder for script injection
  - [ ] **Tests:**
    - [ ] helmTemplateWebhookConfigShouldHave5Webhooks
    - [ ] helmTemplateWebhookConfigShouldHaveFailurePolicyFail
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: <1s
  - [ ] **Learning:**
    - ValidatingWebhookConfiguration is cluster-scoped (no namespace)

---

## Phase 3: CRD Templates

- [ ] Create ApplicationService CRD `[FUNC_03 §3.1]`
  - [ ] **Objectives:**
    - [ ] Create templates/crds/applicationservice.yaml
    - [ ] Define spec.name as required string field
    - [ ] Add status subresource with phase and message
    - [ ] Set scope: Namespaced with shortName appsvc
  - [ ] **Tests:**
    - [ ] kubectlGetCrdApplicationservicesShouldExist
  - [ ] **Metadata:**
    - Task duration: ~3 min
    - Tests duration: N/A (tested during deployment)

- [ ] Create VirtualCluster CRD `[FUNC_03 §3.2]`
  - [ ] **Objectives:**
    - [ ] Create templates/crds/virtualcluster.yaml
    - [ ] Define spec.clusterId and spec.applicationServiceRef as required
    - [ ] Add status subresource
    - [ ] Set shortName vc
  - [ ] **Tests:**
    - [ ] kubectlGetCrdVirtualclustersShouldExist
  - [ ] **Metadata:**
    - Task duration: ~3 min
    - Tests duration: N/A

- [ ] Create ServiceAccount CRD `[FUNC_03 §3.3]`
  - [ ] **Objectives:**
    - [ ] Create templates/crds/serviceaccount.yaml
    - [ ] Define spec.name, spec.dn (array), spec.clusterRef, spec.applicationServiceRef
    - [ ] Add status subresource
    - [ ] Set shortName sa
  - [ ] **Tests:**
    - [ ] kubectlGetCrdServiceaccountsShouldExist
  - [ ] **Metadata:**
    - Task duration: ~3 min
    - Tests duration: N/A

- [ ] Create Topic CRD `[FUNC_03 §3.4]`
  - [ ] **Objectives:**
    - [ ] Create templates/crds/topic.yaml
    - [ ] Define spec.name, spec.serviceRef, spec.applicationServiceRef as required
    - [ ] Add spec.partitions (default 6), spec.replicationFactor (default 3)
    - [ ] Add spec.config as additionalProperties map
    - [ ] Add status subresource
  - [ ] **Tests:**
    - [ ] kubectlGetCrdTopicsShouldExist
  - [ ] **Metadata:**
    - Task duration: ~4 min
    - Tests duration: N/A

- [ ] Create ACL CRD `[FUNC_03 §3.5]`
  - [ ] **Objectives:**
    - [ ] Create templates/crds/acl.yaml
    - [ ] Define spec.serviceRef, spec.applicationServiceRef as required
    - [ ] Add spec.topicRef, spec.consumerGroupRef as optional
    - [ ] Add spec.operations array with enum validation
    - [ ] Add spec.host (default "*"), spec.permission (enum ALLOW/DENY)
    - [ ] Add status subresource
  - [ ] **Tests:**
    - [ ] kubectlGetCrdAclsShouldExist
  - [ ] **Metadata:**
    - Task duration: ~4 min
    - Tests duration: N/A

- [ ] Create ConsumerGroup CRD `[FUNC_03 §3.6]`
  - [ ] **Objectives:**
    - [ ] Create templates/crds/consumergroup.yaml
    - [ ] Define spec.name, spec.serviceRef, spec.applicationServiceRef as required
    - [ ] Add spec.patternType enum (LITERAL, PREFIXED) with default LITERAL
    - [ ] Add status subresource
    - [ ] Set shortName cg
  - [ ] **Tests:**
    - [ ] kubectlGetCrdConsumergroupsShouldExist
  - [ ] **Metadata:**
    - Task duration: ~3 min
    - Tests duration: N/A

- [ ] Create Helm test pod `[FUNC_03 §3.7]`
  - [ ] **Objectives:**
    - [ ] Create tests/test-webhook-health.yaml
    - [ ] Use busybox wget to check webhook health endpoint
    - [ ] Add helm.sh/hook: test annotation
    - [ ] Add hook-delete-policy: hook-succeeded
  - [ ] **Tests:**
    - [ ] helmTestShouldPassAfterDeployment
  - [ ] **Metadata:**
    - Task duration: ~2 min
    - Tests duration: N/A

---

## Phase 4: Shell Scripts

- [ ] Create setup-minikube.sh `[FUNC_04 §4.1]`
  - [ ] **Objectives:**
    - [ ] Accept CLUSTER_NAME, FRESH_CLUSTER, NAMESPACE env vars
    - [ ] Check if cluster exists with minikube status
    - [ ] Delete existing cluster if FRESH_CLUSTER=true
    - [ ] Reuse existing cluster and create fresh namespace if FRESH_CLUSTER=false
    - [ ] Create new cluster with docker driver, 2 CPUs, 4GB memory
    - [ ] Write namespace to .test-namespace file
  - [ ] **Tests:**
    - [ ] setupMinikubeShouldCreateCluster
    - [ ] setupMinikubeShouldReuseExistingCluster
    - [ ] setupMinikubeShouldWriteNamespaceFile
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: ~90s (cluster creation)
  - [ ] **Learning:**
    - Use minikube profile to switch between clusters

- [ ] Create generate-certs.sh `[FUNC_04 §4.2]`
  - [ ] **Objectives:**
    - [ ] Generate CA key and self-signed certificate
    - [ ] Generate server key and CSR with SAN for service DNS names
    - [ ] Sign server certificate with CA
    - [ ] Export base64-encoded values for Helm
    - [ ] Write values-tls.yaml file for deploy script
  - [ ] **Tests:**
    - [ ] generateCertsShouldCreateCaKey
    - [ ] generateCertsShouldCreateServerCert
    - [ ] generateCertsShouldWriteValuesTlsYaml
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: <1s
  - [ ] **Learning:**
    - SAN must include service.namespace.svc.cluster.local for K8s DNS

- [ ] Create deploy.sh `[FUNC_04 §4.3]`
  - [ ] **Objectives:**
    - [ ] Build Java application with mvn package -DskipTests
    - [ ] Build Docker image in Minikube context (eval minikube docker-env)
    - [ ] Call generate-certs.sh for TLS certificates
    - [ ] Deploy with helm upgrade --install using values-minikube.yaml and values-tls.yaml
    - [ ] Wait for deployment ready with kubectl wait
    - [ ] Verify endpoints exist
  - [ ] **Tests:**
    - [ ] deployShouldBuildDockerImage
    - [ ] deployShouldInstallHelmRelease
    - [ ] deployShouldWaitForWebhookReady
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: ~60s
  - [ ] **Learning:**
    - Use helm upgrade --install for idempotent deployments

- [ ] Create teardown.sh `[FUNC_04 §4.4]`
  - [ ] **Objectives:**
    - [ ] Uninstall Helm release if exists
    - [ ] Delete all CRD instances in namespace
    - [ ] Optionally delete namespace (DELETE_NAMESPACE=true)
    - [ ] Optionally delete cluster (DELETE_CLUSTER=true)
    - [ ] Cleanup .test-namespace and .certs files
  - [ ] **Tests:**
    - [ ] teardownShouldUninstallHelmRelease
    - [ ] teardownShouldCleanupFiles
  - [ ] **Metadata:**
    - Task duration: ~3 min
    - Tests duration: ~10s

- [ ] Create run-tests.sh `[FUNC_04 §4.5]`
  - [ ] **Objectives:**
    - [ ] Parse CLI arguments: --fresh-cluster, --skip-deploy, --skip-tests, --bats-only, --java-only, --test-filter, --cleanup
    - [ ] Call setup-minikube.sh with FRESH_CLUSTER flag
    - [ ] Call deploy.sh unless --skip-deploy
    - [ ] Run Bats tests with optional filter unless --java-only
    - [ ] Run Java E2E tests with Maven unless --bats-only
    - [ ] Call teardown.sh if --cleanup
    - [ ] Print summary with pass/fail counts
  - [ ] **Tests:**
    - [ ] runTestsShouldParseArguments
    - [ ] runTestsShouldRunBatsTests
    - [ ] runTestsShouldRunJavaTests
    - [ ] runTestsShouldPrintSummary
  - [ ] **Metadata:**
    - Task duration: ~10 min
    - Tests duration: ~5 min (full suite)
  - [ ] **Learning:**
    - Use getopts or manual parsing for long options in bash

---

## Phase 5: Bats Test Infrastructure

- [ ] Create test_helper.bash `[FUNC_05 §5.1]`
  - [ ] **Objectives:**
    - [ ] Load bats-support and bats-assert with fallback paths
    - [ ] Provide minimal fallback assertions if libraries not found
    - [ ] Read NAMESPACE from .test-namespace file
    - [ ] Implement wait_for function with configurable timeout
    - [ ] Implement apply_fixture, delete_fixture helper functions
    - [ ] Implement expect_rejection for testing webhook rejections
    - [ ] Implement resource_exists, get_resource helpers
    - [ ] Implement cleanup_test_resources and wait_for_webhook
  - [ ] **Tests:**
    - [ ] testHelperShouldLoadBatsLibraries
    - [ ] waitForShouldReturnSuccessWhenConditionMet
    - [ ] waitForShouldTimeoutWhenConditionNotMet
  - [ ] **Metadata:**
    - Task duration: ~8 min
    - Tests duration: N/A (infrastructure)
  - [ ] **Learning:**
    - Bats runs each @test in a subshell; use setup_file for shared state

---

## Phase 6: Bats Deployment Tests

- [ ] Create 01_deployment.bats `[FUNC_06 §6.1]`
  - [ ] **Objectives:**
    - [ ] Test webhook deployment exists
    - [ ] Test webhook deployment has ready replicas >= 1
    - [ ] Test webhook service exists
    - [ ] Test webhook service has endpoints with IP
    - [ ] Test webhook pod is in Running phase
    - [ ] Test all 6 CRDs are installed
    - [ ] Test ValidatingWebhookConfiguration exists
  - [ ] **Tests:**
    - [ ] webhookDeploymentExists
    - [ ] webhookDeploymentHasReadyReplicas
    - [ ] webhookServiceExists
    - [ ] webhookServiceHasEndpoints
    - [ ] webhookPodIsRunning
    - [ ] crdsAreInstalled
    - [ ] validatingWebhookConfigurationExists
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: ~10s
  - [ ] **Learning:**
    - Use jsonpath for precise kubectl output extraction

---

## Phase 7: Bats Webhook Admission Tests

- [ ] Create 02_webhook_admission.bats `[FUNC_07 §7.1]`
  - [ ] **Objectives:**
    - [ ] Setup: wait for webhook ready, cleanup test resources
    - [ ] Test webhook accepts valid ApplicationService
    - [ ] Test webhook accepts valid VirtualCluster with existing ApplicationService
    - [ ] Test webhook accepts valid ServiceAccount with prereqs
    - [ ] Test webhook accepts valid Topic with prereqs
    - [ ] Test webhook accepts valid ACL
    - [ ] Test webhook accepts valid ConsumerGroup
    - [ ] Test webhook rejects ApplicationService with missing name
    - [ ] Test webhook rejects VirtualCluster with non-existent ApplicationService ref
    - [ ] Teardown: cleanup test resources
  - [ ] **Tests:**
    - [ ] webhookAcceptsValidApplicationService
    - [ ] webhookAcceptsValidVirtualClusterWithExistingApplicationService
    - [ ] webhookAcceptsValidServiceAccount
    - [ ] webhookAcceptsValidTopic
    - [ ] webhookAcceptsValidAcl
    - [ ] webhookAcceptsValidConsumerGroup
    - [ ] webhookRejectsApplicationServiceWithMissingName
    - [ ] webhookRejectsVirtualClusterWithNonExistentApplicationServiceRef
  - [ ] **Metadata:**
    - Task duration: ~8 min
    - Tests duration: ~30s
  - [ ] **Learning:**
    - Create resources in dependency order; clean up in reverse order

---

## Phase 8: Bats Ownership Chain Tests

- [ ] Create 03_ownership_chain.bats `[FUNC_08 §8.1]`
  - [ ] **Objectives:**
    - [ ] Setup: wait for webhook, cleanup resources
    - [ ] Test full ownership chain (App -> VC -> SA -> Topic) accepted
    - [ ] Test VirtualCluster requires valid ApplicationService reference
    - [ ] Test ServiceAccount requires valid VirtualCluster reference
    - [ ] Test Topic requires valid ServiceAccount reference
    - [ ] Test cannot change applicationServiceRef on update (immutability)
    - [ ] Test deleting parent with existing children fails
    - [ ] Each test starts with cleanup_test_resources for isolation
  - [ ] **Tests:**
    - [ ] fullOwnershipChainValidHierarchyAccepted
    - [ ] virtualClusterRequiresValidApplicationServiceReference
    - [ ] serviceAccountRequiresValidVirtualClusterReference
    - [ ] topicRequiresValidServiceAccountReference
    - [ ] cannotChangeApplicationServiceRefOnUpdate
    - [ ] deletingParentWithExistingChildrenFails
  - [ ] **Metadata:**
    - Task duration: ~8 min
    - Tests duration: ~45s
  - [ ] **Learning:**
    - Use setup() for per-test cleanup, setup_file() for one-time setup

---

## Phase 9: Bats Multi-Tenant Tests

- [ ] Create 04_multi_tenant.bats `[FUNC_09 §9.1]`
  - [ ] **Objectives:**
    - [ ] Setup: create tenant-a and tenant-b ApplicationServices
    - [ ] Setup: create tenant-a VirtualCluster and ServiceAccount
    - [ ] Test tenant A can create resources under own ApplicationService
    - [ ] Test tenant B cannot reference tenant A's VirtualCluster
    - [ ] Test tenant B cannot reference tenant A's ServiceAccount
    - [ ] Test tenant B can create own isolated resources
  - [ ] **Tests:**
    - [ ] tenantACanCreateResourcesUnderOwnApplicationService
    - [ ] tenantBCannotReferenceTenantAVirtualCluster
    - [ ] tenantBCannotReferenceTenantAServiceAccount
    - [ ] tenantBCanCreateOwnIsolatedResources
  - [ ] **Metadata:**
    - Task duration: ~6 min
    - Tests duration: ~30s
  - [ ] **Learning:**
    - Multi-tenant isolation is critical security boundary; test thoroughly

---

## Phase 10: Bats HA Failover Tests

- [ ] Create 05_ha_failover.bats `[FUNC_10 §10.1]`
  - [ ] **Objectives:**
    - [ ] Setup: scale deployment to 2 replicas, wait for both ready
    - [ ] Test webhook has 2 ready replicas
    - [ ] Test webhook survives single pod failure (delete pod, verify ops work)
    - [ ] Test webhook recovers after pod restart (wait for 2 replicas again)
    - [ ] Test rolling restart maintains availability (rollout restart, verify ops during)
    - [ ] Teardown: scale back to 1 replica
  - [ ] **Tests:**
    - [ ] webhookHas2ReadyReplicas
    - [ ] webhookSurvivesSinglePodFailure
    - [ ] webhookRecoversAfterPodRestart
    - [ ] rollingRestartMaintainsAvailability
  - [ ] **Metadata:**
    - Task duration: ~8 min
    - Tests duration: ~120s
  - [ ] **Learning:**
    - Use --wait=false on pod delete for non-blocking operation

---

## Phase 11: Test Fixtures - Valid Resources

- [ ] Create valid resource fixtures `[FUNC_11 §11.1]`
  - [ ] **Objectives:**
    - [ ] Create fixtures/valid/application-service.yaml (name: test-app)
    - [ ] Create fixtures/valid/virtual-cluster.yaml (refs test-app)
    - [ ] Create fixtures/valid/service-account.yaml (refs test-app, test-vc)
    - [ ] Create fixtures/valid/topic.yaml (refs test-app, test-sa)
    - [ ] Create fixtures/valid/acl.yaml (refs test-app, test-sa, test-topic)
    - [ ] Create fixtures/valid/consumer-group.yaml (refs test-app, test-sa)
  - [ ] **Tests:**
    - [ ] N/A - fixtures are tested by Bats tests
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: N/A

---

## Phase 12: Test Fixtures - Invalid Resources

- [ ] Create invalid resource fixtures `[FUNC_12 §12.1]`
  - [ ] **Objectives:**
    - [ ] Create fixtures/invalid/missing-appname.yaml (ApplicationService without name)
    - [ ] Create fixtures/invalid/nonexistent-appservice-ref.yaml (VC refs missing app)
    - [ ] Create fixtures/invalid/vc-without-appservice.yaml (orphan VC)
    - [ ] Create fixtures/invalid/sa-without-vc.yaml (SA refs missing VC)
    - [ ] Create fixtures/invalid/topic-without-sa.yaml (Topic refs missing SA)
    - [ ] Create fixtures/invalid/topic-changed-owner.yaml (UPDATE with changed appRef)
  - [ ] **Tests:**
    - [ ] N/A - fixtures are tested by Bats tests
  - [ ] **Metadata:**
    - Task duration: ~5 min
    - Tests duration: N/A
  - [ ] **Learning:**
    - Invalid fixtures must trigger specific error messages for assertion

---

## Phase 13: Test Fixtures - Tenant Isolation

- [ ] Create tenant-a fixtures `[FUNC_13 §13.1]`
  - [ ] **Objectives:**
    - [ ] Create fixtures/tenant-a/application-service.yaml (name: tenant-a-app)
    - [ ] Create fixtures/tenant-a/virtual-cluster.yaml (refs tenant-a-app)
    - [ ] Create fixtures/tenant-a/service-account.yaml (refs tenant-a-app, tenant-a-vc)
    - [ ] Create fixtures/tenant-a/topic.yaml (refs tenant-a-app, tenant-a-sa)
  - [ ] **Tests:**
    - [ ] N/A - fixtures are tested by Bats tests
  - [ ] **Metadata:**
    - Task duration: ~3 min
    - Tests duration: N/A

- [ ] Create tenant-b fixtures `[FUNC_13 §13.2]`
  - [ ] **Objectives:**
    - [ ] Create fixtures/tenant-b/application-service.yaml (name: tenant-b-app)
    - [ ] Create fixtures/tenant-b/virtual-cluster.yaml (refs tenant-b-app)
    - [ ] Create fixtures/tenant-b/service-account.yaml (refs tenant-b-app, tenant-b-vc)
    - [ ] Create fixtures/tenant-b/topic.yaml (refs tenant-b-app, tenant-b-sa)
    - [ ] Create fixtures/tenant-b/topic-referencing-tenant-a-vc.yaml (illegal cross-ref)
    - [ ] Create fixtures/tenant-b/acl-referencing-tenant-a-sa.yaml (illegal cross-ref)
  - [ ] **Tests:**
    - [ ] N/A - fixtures are tested by Bats tests
  - [ ] **Metadata:**
    - Task duration: ~4 min
    - Tests duration: N/A

---

## Phase 14: Test Fixtures - Ownership Chain & HA

- [ ] Create ownership-chain fixtures `[FUNC_14 §14.1]`
  - [ ] **Objectives:**
    - [ ] Create fixtures/ownership-chain/full-hierarchy.yaml with all 4 resources in single file
    - [ ] Use YAML document separators (---)
    - [ ] Resources: chain-app, chain-vc, chain-sa, chain-topic
  - [ ] **Tests:**
    - [ ] N/A - fixtures are tested by Bats tests
  - [ ] **Metadata:**
    - Task duration: ~3 min
    - Tests duration: N/A
  - [ ] **Learning:**
    - Multi-document YAML allows atomic application of related resources

- [ ] Create ha-test fixtures `[FUNC_14 §14.2]`
  - [ ] **Objectives:**
    - [ ] Create fixtures/ha-test/application-service.yaml (ha-test-app)
    - [ ] Create fixtures/ha-test/virtual-cluster.yaml (ha-test-vc)
    - [ ] Create fixtures/ha-test/topic-rolling-1.yaml through topic-rolling-5.yaml
  - [ ] **Tests:**
    - [ ] N/A - fixtures are tested by Bats tests
  - [ ] **Metadata:**
    - Task duration: ~3 min
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
