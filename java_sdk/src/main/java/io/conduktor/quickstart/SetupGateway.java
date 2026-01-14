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

    // CRD Data Model POJOs
    static class MessagingDeclaration {
        @NotBlank(message = "apiVersion must not be empty")
        public String apiVersion;

        @NotBlank(message = "kind must not be empty")
        public String kind;

        @Valid
        @NotNull(message = "metadata must not be null")
        public Metadata metadata;

        @Valid
        @NotNull(message = "spec must not be null")
        public Spec spec;
    }

    static class Metadata {
        public String name;
        public String namespace;
    }

    static class Spec {
        @NotBlank(message = "serviceName must not be empty")
        public String serviceName;

        @NotBlank(message = "virtualClusterId must not be empty")
        public String virtualClusterId;

        @Valid
        @Size(max = 10, message = "topics list must not exceed 10 items")
        public List<TopicDef> topics = List.of();

        @Valid
        @Size(max = 10, message = "acls list must not exceed 10 items")
        public List<AclDef> acls = List.of();
    }

    static class TopicDef {
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

    static class AclDef {
        @NotNull
        public AclResourceType type = AclResourceType.TOPIC;

        @NotBlank(message = "acl name must not be empty")
        public String name;

        @NotNull
        @Size(min = 1, max = 10, message = "operations list must have between 1 and 10 items")
        public List<String> operations = List.of("READ", "WRITE", "DESCRIBE");

        @NotNull
        public ResourcePatternType patternType = ResourcePatternType.LITERAL;

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
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: demo-admin
            spec:
              serviceName: demo-admin
              virtualClusterId: demo
            ---
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: demo-acl-admin
            spec:
              serviceName: demo-acl-admin
              virtualClusterId: demo-acl
            ---
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: demo-acl-user
            spec:
              serviceName: demo-acl-user
              virtualClusterId: demo-acl
              acls:
                - type: TOPIC
                  name: click
                  patternType: PREFIXED
                  operations: [READ]
                - type: CONSUMER_GROUP
                  name: myconsumer-
                  patternType: PREFIXED
                  operations: [READ]
            ---
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: orders-service
              namespace: orders
            spec:
              serviceName: orders-service
              virtualClusterId: prod-cluster
              topics:
                - name: orders.events
                  partitions: 12
                  replicationFactor: 3
                  config:
                    retention.ms: "604800000"
                    cleanup.policy: "delete"
                - name: orders.deadletter
                  partitions: 3
              acls:
                - type: TOPIC
                  name: orders.events
                  operations: [READ, WRITE]
                - type: TOPIC
                  name: orders.deadletter
                  operations: [READ, WRITE]
                - type: TOPIC
                  name: inventory.updates
                  operations: [READ]
            """;

    // Gateway SDK clients
    private final CliVirtualClusterGatewayV27Api virtualClusterApi;
    private final CliGatewayServiceAccountGatewayV210Api gatewayServiceAccountApi;

    // Track vClusters already created in this session
    private final java.util.Set<String> createdVClusters = new java.util.HashSet<>();

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

        log.info("Setting up vClusters with mTLS: {}, {}", vCluster, vClusterAcl);

        // Parse and process all CRDs
        List<MessagingDeclaration> crds = parseAllCrds(CRDS);
        for (MessagingDeclaration crd : crds) {
            processCRD(crd);
        }

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
                .spec(new TopicSpec()
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

    // CRD Processing Methods

    /**
     * Parse and validate multiple CRD documents from a multi-document YAML string.
     * This method is type-safe and validates each document against bean validation constraints.
     *
     * @param multiDocYaml Multi-document YAML string containing CRD definitions
     * @return List of validated MessagingDeclaration objects
     * @throws IllegalArgumentException if any document fails validation or is not a valid MessagingDeclaration
     */
    List<MessagingDeclaration> parseAllCrds(String multiDocYaml) {
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
                new org.yaml.snakeyaml.constructor.Constructor(MessagingDeclaration.class, new org.yaml.snakeyaml.LoaderOptions())
        );

        List<MessagingDeclaration> crds = new ArrayList<>();
        int documentIndex = 0;

        for (Object doc : yaml.loadAll(multiDocYaml)) {
            documentIndex++;

            // Type safety: verify the document is actually a MessagingDeclaration
            if (!(doc instanceof MessagingDeclaration)) {
                throw new IllegalArgumentException(
                        "Document #" + documentIndex + " is not a valid MessagingDeclaration. Got: " +
                                (doc != null ? doc.getClass().getName() : "null")
                );
            }

            MessagingDeclaration crd = (MessagingDeclaration) doc;

            // Validate using bean validation
            java.util.Set<ConstraintViolation<MessagingDeclaration>> violations = validator.validate(crd);
            if (!violations.isEmpty()) {
                StringBuilder errorMsg = new StringBuilder("CRD validation failed for document #" + documentIndex);
                if (crd.metadata != null && crd.metadata.name != null) {
                    errorMsg.append(" (").append(crd.metadata.name).append(")");
                }
                errorMsg.append(":\n");

                for (ConstraintViolation<MessagingDeclaration> violation : violations) {
                    errorMsg.append("  - ").append(violation.getPropertyPath())
                            .append(": ").append(violation.getMessage()).append("\n");
                }
                throw new IllegalArgumentException(errorMsg.toString());
            }

            crds.add(crd);
        }

        if (crds.isEmpty()) {
            throw new IllegalArgumentException("No valid CRD documents found in YAML");
        }

        return crds;
    }

    MessagingDeclaration parseYaml(String crdYaml) {
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
                new org.yaml.snakeyaml.constructor.Constructor(MessagingDeclaration.class, new org.yaml.snakeyaml.LoaderOptions())
        );
        MessagingDeclaration crd = yaml.load(crdYaml);

        // Validate using bean validation
        java.util.Set<ConstraintViolation<MessagingDeclaration>> violations = validator.validate(crd);
        if (!violations.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("CRD validation failed:\n");
            for (ConstraintViolation<MessagingDeclaration> violation : violations) {
                errorMsg.append("  - ").append(violation.getPropertyPath())
                        .append(": ").append(violation.getMessage()).append("\n");
            }
            throw new IllegalArgumentException(errorMsg.toString());
        }

        return crd;
    }

    private void upsertVClusterFromCRD(String vClusterName, boolean hasAcls, String serviceName)
            throws IOException, org.openapitools.client.ApiException, ApiException, InterruptedException {
        VirtualClusterSpec spec = new VirtualClusterSpec();

        String adminUser;
        if (hasAcls) {
            adminUser = serviceName + "-admin";
            spec.aclEnabled(true).superUsers(List.of(adminUser));
        } else {
            adminUser = serviceName;
            spec.aclMode("KAFKA_API").superUsers(List.of(serviceName));
        }

        log.info("Upserting VirtualCluster: {}", vClusterName);
        upsertVirtualCluster(new VirtualCluster()
                .kind("VirtualCluster")
                .apiVersion("gateway/v2")
                .metadata(new VirtualClusterMetadata().name(vClusterName))
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

        // Mark as created
        createdVClusters.add(vClusterName);

        // Wait for Console to establish connection to the cluster
        log.info("Waiting for Console to connect to vCluster {}...", vClusterName);
        Thread.sleep(5000);
    }

    private void createTopicsFromCRD(String vClusterName, List<TopicDef> topics)
            throws org.openapitools.client.ApiException {
        for (TopicDef topic : topics) {
            createTopic(
                    vClusterName,
                    topic.name,
                    topic.partitions,
                    topic.replicationFactor,
                    topic.config
            );
        }
    }

    private void createAclsFromCRD(String vClusterName, String serviceName, List<AclDef> acls)
            throws org.openapitools.client.ApiException {
        List<KafkaServiceAccountACL> kafkaAcls = new ArrayList<>();

        // Convert CRD ACLs to Kafka ACLs
        for (AclDef aclDef : acls) {
            List<Operation> ops = aclDef.operations.stream()
                    .map(Operation::valueOf)
                    .toList();

            kafkaAcls.add(new KafkaServiceAccountACL()
                    .type(aclDef.type)
                    .name(aclDef.name)
                    .patternType(aclDef.patternType)
                    .operations(ops)
                    .host(aclDef.host)
                    .permission(aclDef.permission));
        }

        // Create Console ServiceAccount
        serviceAccountApi.createOrUpdateServiceAccountV1(vClusterName,
                new ServiceAccountResourceV1()
                        .apiVersion("v1")
                        .kind(ServiceAccountKind.SERVICE_ACCOUNT)
                        .metadata(new ServiceAccountMetadata()
                                .name(serviceName)
                                .cluster(vClusterName))
                        .spec(new ServiceAccountSpec()
                                .authorization(new ServiceAccountAuthorization(new KAFKAACL()
                                        .type(KAFKAACL.TypeEnum.KAFKA_ACL)
                                        .acls(kafkaAcls)))), null);
        log.info("ServiceAccount/{} created in Console", serviceName);
    }

    private void processCRD(MessagingDeclaration crd) throws Exception {
        String serviceName = crd.spec.serviceName;
        String vClusterName = crd.spec.virtualClusterId;

        log.info("Processing CRD for service: {}", serviceName);

        // 2. Upsert vCluster (only if it doesn't already exist in Console)
        boolean hasAcls = crd.spec.acls != null && !crd.spec.acls.isEmpty();
        boolean vClusterExistsInConsole = vClusterExistsInConsole(vClusterName);

        if (!vClusterExistsInConsole) {
            upsertVClusterFromCRD(vClusterName, hasAcls, serviceName);
        }

        // 3. Upsert Gateway ServiceAccount for the service (mTLS)
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

        // 4. Create topics
        if (crd.spec.topics != null && !crd.spec.topics.isEmpty()) {
            log.info("Creating topics...");
            createTopicsFromCRD(vClusterName, crd.spec.topics);
        }

        // 5. Create Console ServiceAccount with ACLs (only if ACLs defined)
        if (crd.spec.acls != null && !crd.spec.acls.isEmpty()) {
            log.info("Creating Console ServiceAccount with ACLs: {}", serviceName);
            createAclsFromCRD(vClusterName, serviceName, crd.spec.acls);
        }

        // 6. Generate SSL properties
        generateSslProperties(serviceName);

        log.info("Service {} setup complete", serviceName);
    }
}
