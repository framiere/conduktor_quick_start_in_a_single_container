package io.conduktor.quickstart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.conduktor.gateway.client.ApiException;
import io.conduktor.gateway.client.api.CliGatewayServiceAccountGatewayV210Api;
import io.conduktor.gateway.client.api.CliVirtualClusterGatewayV27Api;
import io.conduktor.gateway.client.model.*;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import okhttp3.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.TopicConfig;
import org.openapitools.client.ApiClient;
import org.openapitools.client.Configuration;
import org.openapitools.client.api.CliKafkaClusterConsoleV22Api;
import org.openapitools.client.api.CliServiceAccountSelfServeV111Api;
import org.openapitools.client.api.CliTopicKafkaV212Api;
import org.openapitools.client.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SetupGateway {
    private static final Logger log = LoggerFactory.getLogger(SetupGateway.class);
    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // CRD Data Model POJOs - Base Classes
    static class Metadata {
        public String name;
        public String namespace;
    }

    // VirtualCluster CR
    static class VirtualClusterCR {
        @NotBlank(message = "apiVersion must not be empty")
        public String apiVersion;

        @NotBlank(message = "kind must not be empty")
        public String kind;

        @Valid
        @NotNull(message = "metadata must not be null")
        public Metadata metadata;

        @Valid
        @NotNull(message = "spec must not be null")
        public VirtualClusterSpec spec;
    }

    static class VirtualClusterSpec {
        @NotBlank(message = "clusterId must not be empty")
        public String clusterId;
    }

    // ServiceAccount CR
    static class ServiceAccountCR {
        @NotBlank(message = "apiVersion must not be empty")
        public String apiVersion;

        @NotBlank(message = "kind must not be empty")
        public String kind;

        @Valid
        @NotNull(message = "metadata must not be null")
        public Metadata metadata;

        @Valid
        @NotNull(message = "spec must not be null")
        public ServiceAccountSpec spec;
    }

    static class ServiceAccountSpec {
        @NotBlank(message = "service account name must not be empty")
        public String name;
    }

    // MessagingService CR
    static class MessagingServiceCR {
        @NotBlank(message = "apiVersion must not be empty")
        public String apiVersion;

        @NotBlank(message = "kind must not be empty")
        public String kind;

        @Valid
        @NotNull(message = "metadata must not be null")
        public Metadata metadata;

        @Valid
        @NotNull(message = "spec must not be null")
        public MessagingServiceSpec spec;
    }

    static class MessagingServiceSpec {
        @NotBlank(message = "serviceAccountRef must not be empty")
        public String serviceAccountRef;

        @NotBlank(message = "clusterRef must not be empty")
        public String clusterRef;
    }

    // Topic CR
    static class TopicCR {
        @NotBlank(message = "apiVersion must not be empty")
        public String apiVersion;

        @NotBlank(message = "kind must not be empty")
        public String kind;

        @Valid
        @NotNull(message = "metadata must not be null")
        public Metadata metadata;

        @Valid
        @NotNull(message = "spec must not be null")
        public TopicSpec spec;
    }

    static class TopicSpec {
        @NotBlank(message = "serviceRef must not be empty")
        public String serviceRef;

        @NotBlank(message = "topic name must not be empty")
        public String name;

        @NotNull
        @Min(value = 1, message = "partitions must be at least 1")
        @Max(value = 100, message = "partitions must not exceed 100")
        public Integer partitions = 3;

        @NotNull
        @Min(value = 1, message = "replicationFactor must be at least 1")
        @Max(value = 10, message = "replicationFactor must not exceed 10")
        public Integer replicationFactor = 3;

        public Map<String, String> config = Map.of();
    }

    // ConsumerGroup CR
    static class ConsumerGroupCR {
        @NotBlank(message = "apiVersion must not be empty")
        public String apiVersion;

        @NotBlank(message = "kind must not be empty")
        public String kind;

        @Valid
        @NotNull(message = "metadata must not be null")
        public Metadata metadata;

        @Valid
        @NotNull(message = "spec must not be null")
        public ConsumerGroupSpec spec;
    }

    static class ConsumerGroupSpec {
        @NotBlank(message = "serviceRef must not be empty")
        public String serviceRef;

        @NotBlank(message = "consumer group name must not be empty")
        public String name;

        @NotNull
        public ResourcePatternType patternType = ResourcePatternType.LITERAL;
    }

    // ACL CR
    static class AclCR {
        @NotBlank(message = "apiVersion must not be empty")
        public String apiVersion;

        @NotBlank(message = "kind must not be empty")
        public String kind;

        @Valid
        @NotNull(message = "metadata must not be null")
        public Metadata metadata;

        @Valid
        @NotNull(message = "spec must not be null")
        public AclSpec spec;
    }

    static class AclSpec {
        @NotBlank(message = "serviceRef must not be empty")
        public String serviceRef;

        public String topicRef;
        public String consumerGroupRef;

        @NotNull
        @Size(min = 1, max = 10, message = "operations list must have between 1 and 10 items")
        public List<String> operations = List.of("READ", "WRITE", "DESCRIBE");

        @NotBlank(message = "host must not be empty")
        public String host = "*";

        @NotNull
        public AclPermissionTypeForAccessControlEntry permission = AclPermissionTypeForAccessControlEntry.ALLOW;
    }

    // Configuration
    private final String cdkBaseUrl;
    private final String cdkUser;
    private final String cdkPassword;
    private final String cdkGatewayBaseUrl;
    private final String cdkGatewayUser;
    private final String cdkGatewayPassword;
    private final String certDir;
    private final String vCluster;
    private final String vClusterAcl;
    private final int maxRetries;

    private final OkHttpClient httpClient;
    private final Gson gson;

    // Console SDK clients
    private CliKafkaClusterConsoleV22Api kafkaClusterApi;
    private CliServiceAccountSelfServeV111Api serviceAccountApi;
    private CliTopicKafkaV212Api topicApi;

    private static final String CRDS = """
            # VirtualCluster CR - represents a Conduktor virtual cluster
            apiVersion: messaging.example.com/v1
            kind: VirtualCluster
            metadata:
              name: prod-cluster
              namespace: orders
            spec:
              clusterId: prod-cluster
            ---
            # ServiceAccount CR - represents a service identity
            apiVersion: messaging.example.com/v1
            kind: ServiceAccount
            metadata:
              name: orders-service-sa
              namespace: orders
            spec:
              name: orders-service
            ---
            # MessagingService CR - ties together service account and cluster
            apiVersion: messaging.example.com/v1
            kind: MessagingService
            metadata:
              name: orders-service
              namespace: orders
            spec:
              serviceAccountRef: orders-service-sa
              clusterRef: prod-cluster
            ---
            # Topic CR - references parent service
            apiVersion: messaging.example.com/v1
            kind: Topic
            metadata:
              name: orders-events
              namespace: orders
            spec:
              serviceRef: orders-service
              name: orders.events
              partitions: 12
              replicationFactor: 3
              config:
                retention.ms: "604800000"
                cleanup.policy: "delete"
            ---
            apiVersion: messaging.example.com/v1
            kind: Topic
            metadata:
              name: orders-deadletter
              namespace: orders
            spec:
              serviceRef: orders-service
              name: orders.deadletter
              partitions: 3
              replicationFactor: 3
            ---
            apiVersion: messaging.example.com/v1
            kind: Topic
            metadata:
              name: inventory-updates
              namespace: orders
            spec:
              serviceRef: orders-service
              name: inventory.updates
              partitions: 6
              replicationFactor: 3
            ---
            # ACL CR - references parent service and Topic CR
            apiVersion: messaging.example.com/v1
            kind: ACL
            metadata:
              name: orders-events-rw
              namespace: orders
            spec:
              serviceRef: orders-service
              topicRef: orders-events
              operations: [READ, WRITE]
            ---
            apiVersion: messaging.example.com/v1
            kind: ACL
            metadata:
              name: orders-deadletter-rw
              namespace: orders
            spec:
              serviceRef: orders-service
              topicRef: orders-deadletter
              operations: [READ, WRITE]
            ---
            apiVersion: messaging.example.com/v1
            kind: ACL
            metadata:
              name: inventory-updates-read
              namespace: orders
            spec:
              serviceRef: orders-service
              topicRef: inventory-updates
              operations: [READ]
            ---
            # Additional examples from original crd.yaml
            ---
            apiVersion: messaging.example.com/v1
            kind: VirtualCluster
            metadata:
              name: demo
            spec:
              clusterId: demo
            ---
            apiVersion: messaging.example.com/v1
            kind: VirtualCluster
            metadata:
              name: demo-acl
            spec:
              clusterId: demo-acl
            ---
            apiVersion: messaging.example.com/v1
            kind: ServiceAccount
            metadata:
              name: demo-admin-sa
            spec:
              name: demo-admin
            ---
            apiVersion: messaging.example.com/v1
            kind: MessagingService
            metadata:
              name: demo-admin
            spec:
              serviceAccountRef: demo-admin-sa
              clusterRef: demo
            ---
            apiVersion: messaging.example.com/v1
            kind: ServiceAccount
            metadata:
              name: demo-acl-admin-sa
            spec:
              name: demo-acl-admin
            ---
            apiVersion: messaging.example.com/v1
            kind: MessagingService
            metadata:
              name: demo-acl-admin
            spec:
              serviceAccountRef: demo-acl-admin-sa
              clusterRef: demo-acl
            ---
            apiVersion: messaging.example.com/v1
            kind: ServiceAccount
            metadata:
              name: demo-acl-user-sa
            spec:
              name: demo-acl-user
            ---
            apiVersion: messaging.example.com/v1
            kind: MessagingService
            metadata:
              name: demo-acl-user
            spec:
              serviceAccountRef: demo-acl-user-sa
              clusterRef: demo-acl
            ---
            # Topic CR for demo-acl-user
            apiVersion: messaging.example.com/v1
            kind: Topic
            metadata:
              name: click-topic
            spec:
              serviceRef: demo-acl-user
              name: click
              partitions: 6
              replicationFactor: 3
            ---
            # ConsumerGroup CR
            apiVersion: messaging.example.com/v1
            kind: ConsumerGroup
            metadata:
              name: myconsumer-group
            spec:
              serviceRef: demo-acl-user
              name: myconsumer-
              patternType: PREFIXED
            ---
            # ACL for Topic
            apiVersion: messaging.example.com/v1
            kind: ACL
            metadata:
              name: demo-acl-user-click-read
            spec:
              serviceRef: demo-acl-user
              topicRef: click-topic
              operations: [READ]
            ---
            # ACL for ConsumerGroup
            apiVersion: messaging.example.com/v1
            kind: ACL
            metadata:
              name: demo-acl-user-consumer-read
            spec:
              serviceRef: demo-acl-user
              consumerGroupRef: myconsumer-group
              operations: [READ]
            """;

    // Gateway SDK clients
    private final CliVirtualClusterGatewayV27Api virtualClusterApi;
    private final CliGatewayServiceAccountGatewayV210Api gatewayServiceAccountApi;

    // Track vClusters already created in this session
    private final java.util.Set<String> createdVClusters = new java.util.HashSet<>();
    // Track which vClusters need ACL support (determined by scanning all CRDs upfront)
    private final java.util.Set<String> vClustersRequiringAcls = new java.util.HashSet<>();

    public SetupGateway() {
        this.cdkBaseUrl = env("CDK_BASE_URL", "http://localhost:8080");
        this.cdkUser = env("CDK_USER", "admin@demo.dev");
        this.cdkPassword = env("CDK_PASSWORD", "123_ABC_abc");
        this.cdkGatewayBaseUrl = env("CDK_GATEWAY_BASE_URL", "http://localhost:8888");
        this.cdkGatewayUser = env("CDK_GATEWAY_USER", "admin");
        this.cdkGatewayPassword = env("CDK_GATEWAY_PASSWORD", "conduktor");
        this.certDir = env("CERT_DIR", "certs");
        this.maxRetries = Integer.parseInt(env("MAX_RETRIES", "20"));

        String baseVCluster = env("VCLUSTER", "demo");
        this.vCluster = baseVCluster;
        this.vClusterAcl = baseVCluster + "-acl";

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Initialize Gateway SDK clients
        io.conduktor.gateway.client.ApiClient gatewayApiClient = new io.conduktor.gateway.client.ApiClient();
        gatewayApiClient.setBasePath(cdkGatewayBaseUrl);
        gatewayApiClient.setUsername(cdkGatewayUser);
        gatewayApiClient.setPassword(cdkGatewayPassword);

        this.virtualClusterApi = new CliVirtualClusterGatewayV27Api(gatewayApiClient);
        this.gatewayServiceAccountApi = new CliGatewayServiceAccountGatewayV210Api(gatewayApiClient);
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    public static void main(String[] args) {
        try {
            new SetupGateway().run();
        } catch (Exception e) {
            log.error("ERROR: " + e.getMessage(), e);
            System.exit(1);
        }
    }

    public void run() throws Exception {
        // Wait for services
        waitForService("Conduktor Console", cdkBaseUrl + "/platform/api/modules/resources/health/live");
        waitForGateway();

        // Authenticate and initialize Console SDK clients
        authenticate();

        log.info("Setting up vClusters with mTLS using new six-CR pattern");

        // Parse all CRDs
        ParsedCRs crds = parseAllCrds(CRDS);

        // Process CRs in dependency order
        processCRs(crds);

        log.info("Setup complete!");
    }

    private void waitForService(String name, String url) throws InterruptedException {
        Request request = new Request.Builder().url(url).get().build();

        int attempt = 0;
        do {
            if (isServiceReady(request)) {
                log.info("{} is ready.", name);
                return;
            }
            Thread.sleep(1000);
            if (attempt == 0) {
                log.info("Waiting for {} to be ready", name);
            }
            attempt++;
        } while (attempt < maxRetries);

        throw new RuntimeException(name + " did not become ready after " + maxRetries + " retries");
    }

    private void waitForGateway() throws InterruptedException {
        int attempt = 0;
        do {
            if (isGatewayHealthy()) {
                log.info("Gateway Admin API is ready.");
                return;
            }
            Thread.sleep(1000);
            if (attempt == 0) {
                log.info("Waiting for Gateway Admin API to be ready");
            }
            attempt++;
        } while (attempt < maxRetries);

        throw new RuntimeException("Gateway Admin API did not become ready after " + maxRetries + " retries");
    }

    private boolean isGatewayHealthy() {
        Request request = new Request.Builder()
                .url(cdkGatewayBaseUrl + "/health/ready")
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isServiceReady(Request request) {
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    private void authenticate() throws IOException {
        log.info("Authenticating with Console...");

        JsonObject loginBody = new JsonObject();
        loginBody.addProperty("username", cdkUser);
        loginBody.addProperty("password", cdkPassword);

        Request request = new Request.Builder()
                .url(cdkBaseUrl + "/api/login")
                .post(RequestBody.create(gson.toJson(loginBody), MediaType.parse("application/json")))
                .build();

        String token;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to authenticate: " + response.code());
            }
            JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
            token = jsonResponse.get("access_token").getAsString();
        }

        // Initialize Console SDK clients with Bearer token
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(cdkBaseUrl);
        apiClient.setBearerToken(token);

        kafkaClusterApi = new CliKafkaClusterConsoleV22Api(apiClient);
        serviceAccountApi = new CliServiceAccountSelfServeV111Api(apiClient);
        topicApi = new CliTopicKafkaV212Api(apiClient);

        log.info("Authenticated.");
    }

    private void generateSslProperties(String user) throws IOException {
        String propsFile = user + ".properties";
        try (FileWriter writer = new FileWriter(propsFile)) {
            writer.write("""
                    security.protocol=SSL
                    ssl.truststore.location=%s/%s.truststore.jks
                    ssl.truststore.password=conduktor
                    ssl.keystore.location=%s/%s.keystore.jks
                    ssl.keystore.password=conduktor
                    ssl.key.password=conduktor
                    """.formatted(certDir, user, certDir, user));
        }
        log.info("Created: {}", propsFile);
    }

    private void createTopic(String clusterName, String topicName,
                             int partitions, int replicationFactor,
                             Map<String, String> configs) throws org.openapitools.client.ApiException {
        TopicResourceV2 topicResource = new TopicResourceV2()
                .apiVersion("v2")
                .kind(TopicKind.TOPIC)
                .metadata(new TopicMetadata()
                        .name(topicName)
                        .cluster(clusterName))
                .spec(new org.openapitools.client.model.TopicSpec()
                        .partitions(partitions)
                        .replicationFactor(replicationFactor)
                        .configs(configs));

        topicApi.createOrUpdateTopicV2(clusterName, topicResource, null);
        log.info("Topic/{} created", topicName);
    }

    // Workaround for SDK response deserialization bug
    private void upsertVirtualCluster(VirtualCluster vc) throws ApiException {
        try {
            virtualClusterApi.upsertAVirtualClusterOptionallyIncludingACLs(vc, false);
        } catch (com.google.gson.JsonSyntaxException e) {
            // API succeeded but response parsing failed - ignore
        }
    }

    // Workaround for SDK response deserialization bug
    private void upsertServiceAccount(External sa) throws ApiException {
        try {
            gatewayServiceAccountApi.upsertAServiceAccount(new GatewayServiceAccount(sa), false);
        } catch (com.google.gson.JsonSyntaxException e) {
            // API succeeded but response parsing failed - ignore
        }
    }

    /**
     * Process all CRs in dependency order:
     * 1. VirtualClusters and ServiceAccounts (foundation)
     * 2. MessagingServices (bind service account to cluster)
     * 3. Topics and ConsumerGroups (resources)
     * 4. ACLs (access control)
     */
    private void processCRs(ParsedCRs crds) throws Exception {
        // Build lookup maps for reference resolution
        Map<String, VirtualClusterCR> vClusterMap = crds.virtualClusters.stream()
                .collect(java.util.stream.Collectors.toMap(vc -> vc.metadata.name, vc -> vc));

        Map<String, ServiceAccountCR> serviceAccountMap = crds.serviceAccounts.stream()
                .collect(java.util.stream.Collectors.toMap(sa -> sa.metadata.name, sa -> sa));

        Map<String, MessagingServiceCR> messagingServiceMap = crds.messagingServices.stream()
                .collect(java.util.stream.Collectors.toMap(ms -> ms.metadata.name, ms -> ms));

        Map<String, TopicCR> topicMap = crds.topics.stream()
                .collect(java.util.stream.Collectors.toMap(t -> t.metadata.name, t -> t));

        Map<String, ConsumerGroupCR> consumerGroupMap = crds.consumerGroups.stream()
                .collect(java.util.stream.Collectors.toMap(cg -> cg.metadata.name, cg -> cg));

        // Scan for ACL requirements to determine which vClusters need ACL support
        scanForAclRequirements(crds, messagingServiceMap, vClusterMap);

        // Process MessagingServices (creates vClusters, service accounts, and generates SSL properties)
        for (MessagingServiceCR msgService : crds.messagingServices) {
            processMessagingService(msgService, serviceAccountMap, vClusterMap);
        }

        // Process Topics
        for (TopicCR topic : crds.topics) {
            processTopic(topic, messagingServiceMap, serviceAccountMap, vClusterMap);
        }

        // Process ConsumerGroups (no-op for now, just register them)
        for (ConsumerGroupCR consumerGroup : crds.consumerGroups) {
            log.info("ConsumerGroup/{} registered", consumerGroup.metadata.name);
        }

        // Process ACLs
        for (AclCR acl : crds.acls) {
            processAcl(acl, messagingServiceMap, serviceAccountMap, vClusterMap, topicMap, consumerGroupMap);
        }
    }

    /**
     * Scan for ACL requirements to determine which vClusters need ACL support.
     */
    private void scanForAclRequirements(ParsedCRs crds,
                                        Map<String, MessagingServiceCR> messagingServiceMap,
                                        Map<String, VirtualClusterCR> vClusterMap) {
        for (AclCR acl : crds.acls) {
            // Resolve MessagingService -> VirtualCluster
            MessagingServiceCR msgService = messagingServiceMap.get(acl.spec.serviceRef);
            if (msgService == null) {
                log.warn("ACL {} references unknown MessagingService {}", acl.metadata.name, acl.spec.serviceRef);
                continue;
            }

            VirtualClusterCR vCluster = vClusterMap.get(msgService.spec.clusterRef);
            if (vCluster == null) {
                log.warn("MessagingService {} references unknown VirtualCluster {}",
                        msgService.metadata.name, msgService.spec.clusterRef);
                continue;
            }

            vClustersRequiringAcls.add(vCluster.spec.clusterId);
        }

        if (!vClustersRequiringAcls.isEmpty()) {
            log.info("vClusters requiring ACL support: {}", vClustersRequiringAcls);
        }
    }

    // CRD Processing Methods

    // Container for all parsed CRs
    static class ParsedCRs {
        public List<VirtualClusterCR> virtualClusters = new ArrayList<>();
        public List<ServiceAccountCR> serviceAccounts = new ArrayList<>();
        public List<MessagingServiceCR> messagingServices = new ArrayList<>();
        public List<TopicCR> topics = new ArrayList<>();
        public List<ConsumerGroupCR> consumerGroups = new ArrayList<>();
        public List<AclCR> acls = new ArrayList<>();
    }

    /**
     * Parse and validate multiple CRD documents from a multi-document YAML string.
     * This method handles multiple CR kinds and validates each document against bean validation constraints.
     *
     * @param multiDocYaml Multi-document YAML string containing CRD definitions
     * @return ParsedCRs object containing all parsed and validated CRs grouped by type
     * @throws IllegalArgumentException if any document fails validation or has unknown kind
     */
    ParsedCRs parseAllCrds(String multiDocYaml) {
        ParsedCRs result = new ParsedCRs();
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.LoaderOptions());

        int documentIndex = 0;
        for (Object doc : yaml.loadAll(multiDocYaml)) {
            documentIndex++;

            if (!(doc instanceof Map)) {
                continue; // Skip non-map documents (e.g., comments)
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> docMap = (Map<String, Object>) doc;
            String kind = (String) docMap.get("kind");

            if (kind == null || kind.isEmpty()) {
                throw new IllegalArgumentException("Document #" + documentIndex + " missing 'kind' field");
            }

            try {
                switch (kind) {
                    case "VirtualCluster" -> {
                        VirtualClusterCR cr = parseAndValidate(docMap, VirtualClusterCR.class, documentIndex);
                        result.virtualClusters.add(cr);
                    }
                    case "ServiceAccount" -> {
                        ServiceAccountCR cr = parseAndValidate(docMap, ServiceAccountCR.class, documentIndex);
                        result.serviceAccounts.add(cr);
                    }
                    case "MessagingService" -> {
                        MessagingServiceCR cr = parseAndValidate(docMap, MessagingServiceCR.class, documentIndex);
                        result.messagingServices.add(cr);
                    }
                    case "Topic" -> {
                        TopicCR cr = parseAndValidate(docMap, TopicCR.class, documentIndex);
                        result.topics.add(cr);
                    }
                    case "ConsumerGroup" -> {
                        ConsumerGroupCR cr = parseAndValidate(docMap, ConsumerGroupCR.class, documentIndex);
                        result.consumerGroups.add(cr);
                    }
                    case "ACL" -> {
                        AclCR cr = parseAndValidate(docMap, AclCR.class, documentIndex);
                        result.acls.add(cr);
                    }
                    default -> throw new IllegalArgumentException(
                            "Document #" + documentIndex + " has unknown kind: " + kind
                    );
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Failed to parse document #" + documentIndex + " (kind: " + kind + "): " + e.getMessage(),
                        e
                );
            }
        }

        return result;
    }

    /**
     * Parse a map into a specific CR type and validate it.
     */
    private <T> T parseAndValidate(Map<String, Object> docMap, Class<T> crClass, int documentIndex) {
        // Convert map to YAML string and parse it into the target class
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
                new org.yaml.snakeyaml.constructor.Constructor(crClass, new org.yaml.snakeyaml.LoaderOptions())
        );
        String yamlStr = new org.yaml.snakeyaml.Yaml().dump(docMap);
        T cr = yaml.load(yamlStr);

        // Validate using bean validation
        java.util.Set<ConstraintViolation<T>> violations = validator.validate(cr);
        if (!violations.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("CRD validation failed for document #" + documentIndex);
            errorMsg.append(":\n");

            for (ConstraintViolation<T> violation : violations) {
                errorMsg.append("  - ").append(violation.getPropertyPath())
                        .append(": ").append(violation.getMessage()).append("\n");
            }
            throw new IllegalArgumentException(errorMsg.toString());
        }

        return cr;
    }

    private void upsertVClusterFromCRD(String vClusterName, boolean hasAcls, String serviceName)
            throws IOException, org.openapitools.client.ApiException, ApiException, InterruptedException {
        io.conduktor.gateway.client.model.VirtualClusterSpec spec = new io.conduktor.gateway.client.model.VirtualClusterSpec();

        String adminUser;
        if (hasAcls) {
            // Use the serviceName as-is if it already designates an admin user
            adminUser = serviceName.endsWith("-admin") ? serviceName : serviceName + "-admin";
            spec.aclEnabled(true).superUsers(List.of(adminUser));
        } else {
            adminUser = serviceName;
            spec.aclMode("KAFKA_API").superUsers(List.of(serviceName));
        }

        log.info("Upserting VirtualCluster: {}", vClusterName);
        upsertVirtualCluster(new io.conduktor.gateway.client.model.VirtualCluster()
                .kind("VirtualCluster")
                .apiVersion("gateway/v2")
                .metadata(new io.conduktor.gateway.client.model.VirtualClusterMetadata().name(vClusterName))
                .spec(spec));
        log.info("VirtualCluster/{} upserted", vClusterName);

        // Upsert admin service account
        log.info("Upserting admin service account: {}", adminUser);
        upsertServiceAccount(new External()
                .kind("GatewayServiceAccount")
                .apiVersion("gateway/v2")
                .metadata(new ExternalMetadata()
                        .vCluster(vClusterName)
                        .name(adminUser))
                .spec(new ExternalSpec()
                        .type("EXTERNAL")
                        .externalNames(List.of("CN=" + adminUser + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"))));
        log.info("GatewayServiceAccount/{} upserted", adminUser);

        generateSslProperties(adminUser);

        // Wait for Gateway to fully set up the vCluster
        log.info("Waiting for Gateway vCluster to be fully ready...");
        Thread.sleep(3000);

        // Add to Console
        addVClusterToConsole(vClusterName, adminUser, hasAcls);
    }

    private boolean vClusterExistsInConsole(String vClusterName) {
        return createdVClusters.contains(vClusterName);
    }

    private void addVClusterToConsole(String vClusterName, String adminUser, boolean aclEnabled)
            throws org.openapitools.client.ApiException, InterruptedException {
        log.info("Adding vCluster {} to Console...", vClusterName);

        String displayName = vClusterName + " (mTLS" + (aclEnabled ? " + ACL" : "") + ")";

        try {
            kafkaClusterApi.createOrUpdateKafkaClusterV2(new KafkaClusterResourceV2()
                    .apiVersion("v2")
                    .kind(KafkaClusterKind.KAFKA_CLUSTER)
                    .metadata(new KafkaClusterMetadata().name(vClusterName))
                    .spec(new KafkaClusterSpec()
                            .displayName(displayName)
                            .bootstrapServers("localhost:6969")
                            .properties(Map.of(
                                    "security.protocol", "SSL",
                                    "ssl.truststore.location", "/var/lib/conduktor/certs/" + adminUser + ".truststore.jks",
                                    "ssl.truststore.password", "conduktor",
                                    "ssl.keystore.location", "/var/lib/conduktor/certs/" + adminUser + ".keystore.jks",
                                    "ssl.keystore.password", "conduktor",
                                    "ssl.key.password", "conduktor"
                            ))
                            .kafkaFlavor(new KafkaFlavor(new Gateway()
                                    .type(Gateway.TypeEnum.GATEWAY)
                                    .url(cdkGatewayBaseUrl)
                                    .user(cdkGatewayUser)
                                    .password(cdkGatewayPassword)
                                    .virtualCluster(vClusterName)))), null);
            log.info("KafkaCluster/{} created in Console", vClusterName);
        } catch (org.openapitools.client.ApiException e) {
            // Handle "cluster already exists" error gracefully
            if (e.getCode() == 500 && e.getResponseBody() != null && e.getResponseBody().contains("cluster slug already exist")) {
                log.info("KafkaCluster/{} already exists in Console, skipping creation", vClusterName);
            } else {
                throw e;
            }
        }

        // Mark as created
        createdVClusters.add(vClusterName);

        // Wait for Console to establish connection to the cluster
        log.info("Waiting for Console to connect to vCluster {}...", vClusterName);
        Thread.sleep(10000);
    }

    private void waitForConsoleReady(String vClusterName) throws InterruptedException {
        log.info("Waiting for Console to be ready for vCluster {}...", vClusterName);
        // Give Console additional time to establish connection and index the cluster
        Thread.sleep(30000);  // 30 seconds
        log.info("Proceeding with operations on vCluster {}", vClusterName);
    }

    /**
     * Process a MessagingService CR - creates vCluster, service accounts, and generates SSL properties
     */
    private void processMessagingService(MessagingServiceCR msgService,
                                         Map<String, ServiceAccountCR> serviceAccountMap,
                                         Map<String, VirtualClusterCR> vClusterMap) throws Exception {
        String msgServiceName = msgService.metadata.name;
        log.info("Processing MessagingService: {}", msgServiceName);

        // Resolve references
        ServiceAccountCR serviceAccount = serviceAccountMap.get(msgService.spec.serviceAccountRef);
        if (serviceAccount == null) {
            throw new IllegalArgumentException(
                    "MessagingService " + msgServiceName + " references unknown ServiceAccount: " + msgService.spec.serviceAccountRef
            );
        }

        VirtualClusterCR vCluster = vClusterMap.get(msgService.spec.clusterRef);
        if (vCluster == null) {
            throw new IllegalArgumentException(
                    "MessagingService " + msgServiceName + " references unknown VirtualCluster: " + msgService.spec.clusterRef
            );
        }

        String serviceName = serviceAccount.spec.name;
        String vClusterName = vCluster.spec.clusterId;

        // Check if vCluster needs ACL support
        boolean vClusterRequiresAcls = vClustersRequiringAcls.contains(vClusterName);
        boolean vClusterExistsInConsole = vClusterExistsInConsole(vClusterName);

        if (!vClusterExistsInConsole) {
            upsertVClusterFromCRD(vClusterName, vClusterRequiresAcls, serviceName);
        } else {
            log.info("vCluster {} already exists, ensuring Console connection is ready", vClusterName);
            Thread.sleep(5000);
        }

        // Upsert Gateway ServiceAccount for the service (mTLS)
        log.info("Upserting Gateway ServiceAccount: {}", serviceName);
        upsertServiceAccount(new External()
                .kind("GatewayServiceAccount")
                .apiVersion("gateway/v2")
                .metadata(new ExternalMetadata()
                        .vCluster(vClusterName)
                        .name(serviceName))
                .spec(new ExternalSpec()
                        .type("EXTERNAL")
                        .externalNames(List.of("CN=" + serviceName + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"))));
        log.info("GatewayServiceAccount/{} upserted", serviceName);

        // Generate SSL properties
        generateSslProperties(serviceName);

        log.info("MessagingService {} setup complete", msgServiceName);
    }

    /**
     * Process a Topic CR - creates the topic in the vCluster
     */
    private void processTopic(TopicCR topic,
                              Map<String, MessagingServiceCR> messagingServiceMap,
                              Map<String, ServiceAccountCR> serviceAccountMap,
                              Map<String, VirtualClusterCR> vClusterMap) throws Exception {
        String topicName = topic.metadata.name;
        log.info("Processing Topic: {}", topicName);

        // Resolve references: Topic -> MessagingService -> VirtualCluster
        MessagingServiceCR msgService = messagingServiceMap.get(topic.spec.serviceRef);
        if (msgService == null) {
            throw new IllegalArgumentException(
                    "Topic " + topicName + " references unknown MessagingService: " + topic.spec.serviceRef
            );
        }

        VirtualClusterCR vCluster = vClusterMap.get(msgService.spec.clusterRef);
        if (vCluster == null) {
            throw new IllegalArgumentException(
                    "MessagingService " + msgService.metadata.name + " references unknown VirtualCluster: " + msgService.spec.clusterRef
            );
        }

        String vClusterName = vCluster.spec.clusterId;

        // Wait for Console to be ready
        waitForConsoleReady(vClusterName);

        // Create topic
        createTopic(
                vClusterName,
                topic.spec.name,
                topic.spec.partitions,
                topic.spec.replicationFactor,
                topic.spec.config
        );
    }

    /**
     * Process an ACL CR - creates the ACL in the Console
     */
    private void processAcl(AclCR acl,
                            Map<String, MessagingServiceCR> messagingServiceMap,
                            Map<String, ServiceAccountCR> serviceAccountMap,
                            Map<String, VirtualClusterCR> vClusterMap,
                            Map<String, TopicCR> topicMap,
                            Map<String, ConsumerGroupCR> consumerGroupMap) throws Exception {
        String aclName = acl.metadata.name;
        log.info("Processing ACL: {}", aclName);

        // Resolve references: ACL -> MessagingService -> ServiceAccount + VirtualCluster
        MessagingServiceCR msgService = messagingServiceMap.get(acl.spec.serviceRef);
        if (msgService == null) {
            throw new IllegalArgumentException(
                    "ACL " + aclName + " references unknown MessagingService: " + acl.spec.serviceRef
            );
        }

        ServiceAccountCR serviceAccount = serviceAccountMap.get(msgService.spec.serviceAccountRef);
        if (serviceAccount == null) {
            throw new IllegalArgumentException(
                    "MessagingService " + msgService.metadata.name + " references unknown ServiceAccount: " + msgService.spec.serviceAccountRef
            );
        }

        VirtualClusterCR vCluster = vClusterMap.get(msgService.spec.clusterRef);
        if (vCluster == null) {
            throw new IllegalArgumentException(
                    "MessagingService " + msgService.metadata.name + " references unknown VirtualCluster: " + msgService.spec.clusterRef
            );
        }

        String serviceName = serviceAccount.spec.name;
        String vClusterName = vCluster.spec.clusterId;

        // Determine ACL type and resource name
        AclResourceType aclType;
        String resourceName;
        ResourcePatternType patternType;

        if (acl.spec.topicRef != null) {
            // Topic ACL
            TopicCR topic = topicMap.get(acl.spec.topicRef);
            if (topic == null) {
                throw new IllegalArgumentException(
                        "ACL " + aclName + " references unknown Topic: " + acl.spec.topicRef
                );
            }
            aclType = AclResourceType.TOPIC;
            resourceName = topic.spec.name;
            patternType = ResourcePatternType.LITERAL;
        } else if (acl.spec.consumerGroupRef != null) {
            // ConsumerGroup ACL
            ConsumerGroupCR consumerGroup = consumerGroupMap.get(acl.spec.consumerGroupRef);
            if (consumerGroup == null) {
                throw new IllegalArgumentException(
                        "ACL " + aclName + " references unknown ConsumerGroup: " + acl.spec.consumerGroupRef
                );
            }
            aclType = AclResourceType.CONSUMER_GROUP;
            resourceName = consumerGroup.spec.name;
            patternType = consumerGroup.spec.patternType;
        } else {
            throw new IllegalArgumentException(
                    "ACL " + aclName + " must reference either topicRef or consumerGroupRef"
            );
        }

        // Wait for Console to be ready
        waitForConsoleReady(vClusterName);

        // Convert operations to Kafka operations
        List<Operation> ops = acl.spec.operations.stream()
                .map(Operation::valueOf)
                .toList();

        // Create the ACL via Console ServiceAccount
        List<KafkaServiceAccountACL> kafkaAcls = List.of(
                new KafkaServiceAccountACL()
                        .type(aclType)
                        .name(resourceName)
                        .patternType(patternType)
                        .operations(ops)
                        .host(acl.spec.host)
                        .permission(acl.spec.permission)
        );

        // Create or update Console ServiceAccount with this ACL
        serviceAccountApi.createOrUpdateServiceAccountV1(vClusterName,
                new ServiceAccountResourceV1()
                        .apiVersion("v1")
                        .kind(ServiceAccountKind.SERVICE_ACCOUNT)
                        .metadata(new org.openapitools.client.model.ServiceAccountMetadata()
                                .name(serviceName)
                                .cluster(vClusterName))
                        .spec(new org.openapitools.client.model.ServiceAccountSpec()
                                .authorization(new ServiceAccountAuthorization(new KAFKAACL()
                                        .type(KAFKAACL.TypeEnum.KAFKA_ACL)
                                        .acls(kafkaAcls)))), null);
        log.info("ACL/{} created for ServiceAccount/{}", aclName, serviceName);
    }
}
