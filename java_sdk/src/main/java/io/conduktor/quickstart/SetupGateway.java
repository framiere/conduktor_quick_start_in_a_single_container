package io.conduktor.quickstart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.conduktor.gateway.client.ApiException;
import io.conduktor.gateway.client.api.CliGatewayServiceAccountGatewayV210Api;
import io.conduktor.gateway.client.api.CliVirtualClusterGatewayV27Api;
import io.conduktor.gateway.client.model.*;
import okhttp3.*;
import org.openapitools.client.ApiClient;
import org.openapitools.client.Configuration;
import org.openapitools.client.api.CliKafkaClusterConsoleV22Api;
import org.openapitools.client.api.CliServiceAccountSelfServeV111Api;
import org.openapitools.client.api.CliTopicKafkaV212Api;
import org.openapitools.client.model.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SetupGateway {

    // CRD Data Model POJOs
    static class MessagingDeclaration {
        public String apiVersion;
        public String kind;
        public Metadata metadata;
        public Spec spec;
    }

    static class Metadata {
        public String name;
        public String namespace;
    }

    static class Spec {
        public String serviceName;
        public String virtualClusterId;
        public List<TopicDef> topics;
        public List<AclDef> acls;
    }

    static class TopicDef {
        public String name;
        public Integer partitions;
        public Integer replicationFactor;
        public Map<String, String> config;
    }

    static class AclDef {
        public String topic;
        public List<String> operations;
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
    private final String vClusterAdmin;
    private final String vClusterAclAdmin;
    private final String vClusterAclUser;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private String bearerToken;

    // Console SDK clients
    private CliKafkaClusterConsoleV22Api kafkaClusterApi;
    private CliServiceAccountSelfServeV111Api serviceAccountApi;
    private CliTopicKafkaV212Api topicApi;

    private static final String CRD = """
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
                - topic: orders.events
                  operations: [READ, WRITE]
                - topic: orders.deadletter
                  operations: [READ, WRITE]
                - topic: inventory.updates
                  operations: [READ]
            """;

    // Gateway SDK clients
    private CliVirtualClusterGatewayV27Api virtualClusterApi;
    private CliGatewayServiceAccountGatewayV210Api gatewayServiceAccountApi;

    public SetupGateway() {
        this.cdkBaseUrl = env("CDK_BASE_URL", "http://localhost:8080");
        this.cdkUser = env("CDK_USER", "admin@demo.dev");
        this.cdkPassword = env("CDK_PASSWORD", "123_ABC_abc");
        this.cdkGatewayBaseUrl = env("CDK_GATEWAY_BASE_URL", "http://localhost:8888");
        this.cdkGatewayUser = env("CDK_GATEWAY_USER", "admin");
        this.cdkGatewayPassword = env("CDK_GATEWAY_PASSWORD", "conduktor");
        this.certDir = env("CERT_DIR", "certs");

        String baseVCluster = env("VCLUSTER", "demo");
        this.vCluster = baseVCluster;
        this.vClusterAcl = baseVCluster + "-acl";
        this.vClusterAdmin = baseVCluster + "-admin";
        this.vClusterAclAdmin = vClusterAcl + "-admin";
        this.vClusterAclUser = vClusterAcl + "-user";

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
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void run() throws Exception {
        // Wait for services
        waitForService("Conduktor Console", cdkBaseUrl + "/platform/api/modules/resources/health/live");
        waitForGateway();

        // Authenticate and initialize Console SDK clients
        authenticate();

        System.out.println();
        System.out.println("Setting up vClusters with mTLS: " + vCluster + ", " + vClusterAcl);

        // vCluster 1: demo (ACL disabled)
        setupDemoVCluster();

        // vCluster 2: demo-acl (ACL enabled)
        setupDemoAclVCluster();

        // Process CRD
        processCRD(CRD);

        System.out.println();
        System.out.println("Setup complete!");
    }

    private void waitForService(String name, String url) throws InterruptedException {
        Request request = new Request.Builder().url(url).get().build();

        if (isServiceReady(request)) {
            System.out.println(name + " is ready.");
            return;
        }

        System.out.print("Waiting for " + name + " to be ready");
        while (!isServiceReady(request)) {
            Thread.sleep(1000);
            System.out.print(".");
        }
        System.out.println();
        System.out.println(name + " is ready.");
    }

    private void waitForGateway() throws InterruptedException {
        if (isGatewayHealthy()) {
            System.out.println("Gateway Admin API is ready.");
            return;
        }

        System.out.print("Waiting for Gateway Admin API to be ready");
        while (!isGatewayHealthy()) {
            Thread.sleep(1000);
            System.out.print(".");
        }
        System.out.println();
        System.out.println("Gateway Admin API is ready.");
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
        System.out.println("Authenticating with Console...");

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

        this.bearerToken = token;

        // Initialize Console SDK clients with Bearer token
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(cdkBaseUrl);
        apiClient.setBearerToken(token);

        kafkaClusterApi = new CliKafkaClusterConsoleV22Api(apiClient);
        serviceAccountApi = new CliServiceAccountSelfServeV111Api(apiClient);
        topicApi = new CliTopicKafkaV212Api(apiClient);

        System.out.println("Authenticated.");
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
        System.out.println("Created: " + propsFile);
    }

    private void createTopic(String clusterName, String topicName,
                             int partitions, Integer replicationFactor,
                             Map<String, String> configs) throws org.openapitools.client.ApiException {
        TopicResourceV2 topicResource = new TopicResourceV2()
                .apiVersion("v2")
                .kind(TopicKind.TOPIC)
                .metadata(new TopicMetadata()
                        .name(topicName)
                        .cluster(clusterName))
                .spec(new TopicSpec()
                        .partitions(partitions)
                        .replicationFactor(replicationFactor != null ? replicationFactor : 1)
                        .configs(configs));

        topicApi.createOrUpdateTopicV2(clusterName, topicResource, null);
        System.out.println("Topic/" + topicName + " created");
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

    private void setupDemoVCluster() throws IOException, org.openapitools.client.ApiException, ApiException {
        System.out.println();
        System.out.println("Creating vCluster: " + vCluster + "...");

        upsertVirtualCluster(new VirtualCluster()
                .kind("VirtualCluster")
                .apiVersion("gateway/v2")
                .metadata(new VirtualClusterMetadata().name(vCluster))
                .spec(new VirtualClusterSpec()
                        .aclMode("KAFKA_API")
                        .superUsers(List.of(vClusterAdmin))));
        System.out.println("VirtualCluster/" + vCluster + " created");

        System.out.println("Creating service account: " + vClusterAdmin + "...");
        upsertServiceAccount(new External()
                .kind("GatewayServiceAccount")
                .apiVersion("gateway/v2")
                .metadata(new ExternalMetadata()
                        .vCluster(vCluster)
                        .name(vClusterAdmin))
                .spec(new ExternalSpec()
                        .type("EXTERNAL")
                        .externalNames(List.of("CN=" + vClusterAdmin + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"))));
        System.out.println("GatewayServiceAccount/" + vClusterAdmin + " created");

        generateSslProperties(vClusterAdmin);

        System.out.println();
        System.out.println("Adding vCluster " + vCluster + " to Console...");

        kafkaClusterApi.createOrUpdateKafkaClusterV2(new KafkaClusterResourceV2()
                .apiVersion("v2")
                .kind(KafkaClusterKind.KAFKA_CLUSTER)
                .metadata(new KafkaClusterMetadata().name(vCluster))
                .spec(new KafkaClusterSpec()
                        .displayName(vCluster + " (mTLS)")
                        .bootstrapServers("localhost:6969")
                        .properties(Map.of(
                                "security.protocol", "SSL",
                                "ssl.truststore.location",  "/var/lib/conduktor/certs/" + vClusterAdmin + ".truststore.jks",
                                "ssl.truststore.password", "conduktor",
                                "ssl.keystore.location", "/var/lib/conduktor/certs/"  + vClusterAdmin + ".keystore.jks",
                                "ssl.keystore.password", "conduktor",
                                "ssl.key.password", "conduktor"
                        ))
                        .kafkaFlavor(new KafkaFlavor(new Gateway()
                                .type(Gateway.TypeEnum.GATEWAY)
                                .url(cdkGatewayBaseUrl)
                                .user(cdkGatewayUser)
                                .password(cdkGatewayPassword)
                                .virtualCluster(vCluster)))), null);
        System.out.println("KafkaCluster/" + vCluster + " created in Console");
    }

    private void setupDemoAclVCluster() throws IOException, org.openapitools.client.ApiException, ApiException {
        System.out.println();
        System.out.println("Creating vCluster: " + vClusterAcl + " (ACL enabled)...");

        upsertVirtualCluster(new VirtualCluster()
                .kind("VirtualCluster")
                .apiVersion("gateway/v2")
                .metadata(new VirtualClusterMetadata().name(vClusterAcl))
                .spec(new VirtualClusterSpec()
                        .aclEnabled(true)
                        .superUsers(List.of(vClusterAclAdmin))));
        System.out.println("VirtualCluster/" + vClusterAcl + " created");

        System.out.println("Creating service account: " + vClusterAclAdmin + "...");
        upsertServiceAccount(new External()
                .kind("GatewayServiceAccount")
                .apiVersion("gateway/v2")
                .metadata(new ExternalMetadata()
                        .vCluster(vClusterAcl)
                        .name(vClusterAclAdmin))
                .spec(new ExternalSpec()
                        .type("EXTERNAL")
                        .externalNames(List.of("CN=" + vClusterAclAdmin + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"))));
        System.out.println("GatewayServiceAccount/" + vClusterAclAdmin + " created");

        System.out.println("Creating service account: " + vClusterAclUser + "...");
        upsertServiceAccount(new External()
                .kind("GatewayServiceAccount")
                .apiVersion("gateway/v2")
                .metadata(new ExternalMetadata()
                        .vCluster(vClusterAcl)
                        .name(vClusterAclUser))
                .spec(new ExternalSpec()
                        .type("EXTERNAL")
                        .externalNames(List.of("CN=" + vClusterAclUser + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"))));
        System.out.println("GatewayServiceAccount/" + vClusterAclUser + " created");

        generateSslProperties(vClusterAclAdmin);
        generateSslProperties(vClusterAclUser);

        System.out.println();
        System.out.println("Adding vCluster " + vClusterAcl + " to Console...");

        kafkaClusterApi.createOrUpdateKafkaClusterV2(new KafkaClusterResourceV2()
                .apiVersion("v2")
                .kind(KafkaClusterKind.KAFKA_CLUSTER)
                .metadata(new KafkaClusterMetadata().name(vClusterAcl))
                .spec(new KafkaClusterSpec()
                        .displayName(vClusterAcl + " (mTLS + ACL)")
                        .bootstrapServers("localhost:6969")
                        .properties(Map.of(
                                "security.protocol", "SSL",
                                "ssl.truststore.location", "/var/lib/conduktor/certs/" + vClusterAclAdmin + ".truststore.jks",
                                "ssl.truststore.password", "conduktor",
                                "ssl.keystore.location", "/var/lib/conduktor/certs/" + vClusterAclAdmin + ".keystore.jks",
                                "ssl.keystore.password", "conduktor",
                                "ssl.key.password", "conduktor"
                        ))
                        .kafkaFlavor(new KafkaFlavor(new Gateway()
                                .type(Gateway.TypeEnum.GATEWAY)
                                .url(cdkGatewayBaseUrl)
                                .user(cdkGatewayUser)
                                .password(cdkGatewayPassword)
                                .virtualCluster(vClusterAcl)))), null);
        System.out.println("KafkaCluster/" + vClusterAcl + " created in Console");

        System.out.println();
        System.out.println("Creating Console ServiceAccount with ACLs for " + vClusterAclUser + "...");

        serviceAccountApi.createOrUpdateServiceAccountV1(vClusterAcl, new ServiceAccountResourceV1()
                .apiVersion("v1")
                .kind(ServiceAccountKind.SERVICE_ACCOUNT)
                .metadata(new ServiceAccountMetadata()
                        .name(vClusterAclUser)
                        .cluster(vClusterAcl))
                .spec(new ServiceAccountSpec()
                        .authorization(new ServiceAccountAuthorization(new KAFKAACL()
                                .type(KAFKAACL.TypeEnum.KAFKA_ACL)
                                .acls(List.of(
                                        new KafkaServiceAccountACL()
                                                .type(AclResourceType.TOPIC)
                                                .name("click")
                                                .patternType(ResourcePatternType.PREFIXED)
                                                .operations(List.of(Operation.READ))
                                                .host("*")
                                                .permission(AclPermissionTypeForAccessControlEntry.ALLOW),
                                        new KafkaServiceAccountACL()
                                                .type(AclResourceType.CONSUMER_GROUP)
                                                .name("myconsumer-")
                                                .patternType(ResourcePatternType.PREFIXED)
                                                .operations(List.of(Operation.READ))
                                                .host("*")
                                                .permission(AclPermissionTypeForAccessControlEntry.ALLOW)
                                ))))), null);
        System.out.println("ServiceAccount/" + vClusterAclUser + " created in Console");
    }

    // CRD Processing Methods

    MessagingDeclaration parseYaml(String crdYaml) {
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
            new org.yaml.snakeyaml.constructor.Constructor(MessagingDeclaration.class, new org.yaml.snakeyaml.LoaderOptions())
        );
        MessagingDeclaration crd = yaml.load(crdYaml);

        // Validate required fields
        if (crd.spec == null) {
            throw new IllegalArgumentException("Invalid CRD format: missing spec");
        }
        if (crd.spec.serviceName == null || crd.spec.serviceName.isBlank()) {
            throw new IllegalArgumentException("Invalid CRD format: missing required field serviceName");
        }
        if (crd.spec.virtualClusterId == null || crd.spec.virtualClusterId.isBlank()) {
            throw new IllegalArgumentException("Invalid CRD format: missing required field virtualClusterId");
        }

        return crd;
    }

    private void upsertVClusterFromCRD(String vClusterName, boolean hasAcls, String serviceName)
            throws IOException, org.openapitools.client.ApiException, ApiException {
        VirtualClusterSpec spec = new VirtualClusterSpec();

        String adminUser;
        if (hasAcls) {
            adminUser = serviceName + "-admin";
            spec.aclEnabled(true).superUsers(List.of(adminUser));
        } else {
            adminUser = serviceName;
            spec.aclMode("KAFKA_API").superUsers(List.of(serviceName));
        }

        System.out.println("Upserting VirtualCluster: " + vClusterName);
        upsertVirtualCluster(new VirtualCluster()
            .kind("VirtualCluster")
            .apiVersion("gateway/v2")
            .metadata(new VirtualClusterMetadata().name(vClusterName))
            .spec(spec));
        System.out.println("VirtualCluster/" + vClusterName + " upserted");

        // Upsert admin service account
        System.out.println("Upserting admin service account: " + adminUser);
        upsertServiceAccount(new External()
            .kind("GatewayServiceAccount")
            .apiVersion("gateway/v2")
            .metadata(new ExternalMetadata()
                .vCluster(vClusterName)
                .name(adminUser))
            .spec(new ExternalSpec()
                .type("EXTERNAL")
                .externalNames(List.of("CN=" + adminUser + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"))));
        System.out.println("GatewayServiceAccount/" + adminUser + " upserted");

        generateSslProperties(adminUser);

        // Add to Console
        addVClusterToConsole(vClusterName, adminUser, hasAcls);
    }

    private void addVClusterToConsole(String vClusterName, String adminUser, boolean aclEnabled)
            throws org.openapitools.client.ApiException {
        System.out.println("Adding vCluster " + vClusterName + " to Console...");

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
        System.out.println("KafkaCluster/" + vClusterName + " created in Console");
    }

    private void createTopicsFromCRD(String vClusterName, List<TopicDef> topics)
            throws org.openapitools.client.ApiException {
        for (TopicDef topic : topics) {
            createTopic(
                vClusterName,
                topic.name,
                topic.partitions != null ? topic.partitions : 1,
                topic.replicationFactor,
                topic.config != null ? topic.config : Map.of()
            );
        }
    }

    private void createAclsFromCRD(String vClusterName, String serviceName, List<AclDef> acls)
            throws org.openapitools.client.ApiException {
        List<KafkaServiceAccountACL> kafkaAcls = new ArrayList<>();

        // Convert CRD ACLs to Kafka ACLs
        for (AclDef aclDef : acls) {
            List<Operation> ops;
            if (aclDef.operations == null || aclDef.operations.isEmpty()) {
                // No operations defined: grant all Kafka ACL operations
                ops = List.of(
                    Operation.READ,
                    Operation.WRITE,
                    Operation.CREATE,
                    Operation.DELETE,
                    Operation.ALTER,
                    Operation.DESCRIBE,
                    Operation.CLUSTER_ACTION,
                    Operation.ALTER_CONFIGS,
                    Operation.DESCRIBE_CONFIGS,
                    Operation.IDEMPOTENT_WRITE,
                    Operation.CREATE_TOKENS,
                    Operation.DESCRIBE_TOKENS
                );
            } else {
                ops = aclDef.operations.stream()
                    .map(Operation::valueOf)
                    .toList();
            }

            kafkaAcls.add(new KafkaServiceAccountACL()
                .type(AclResourceType.TOPIC)
                .name(aclDef.topic)
                .patternType(ResourcePatternType.LITERAL)
                .operations(ops)
                .host("*")
                .permission(AclPermissionTypeForAccessControlEntry.ALLOW));
        }

        // Add consumer group ACL
        kafkaAcls.add(new KafkaServiceAccountACL()
            .type(AclResourceType.CONSUMER_GROUP)
            .name(serviceName + "-")
            .patternType(ResourcePatternType.PREFIXED)
            .operations(List.of(Operation.READ))
            .host("*")
            .permission(AclPermissionTypeForAccessControlEntry.ALLOW));

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
        System.out.println("ServiceAccount/" + serviceName + " created in Console");
    }

    private void processCRD(String crdYaml) throws Exception {
        // 1. Parse CRD
        MessagingDeclaration crd = parseYaml(crdYaml);
        String serviceName = crd.spec.serviceName;
        String vClusterName = crd.spec.virtualClusterId;

        System.out.println();
        System.out.println("Processing CRD for service: " + serviceName);

        // 2. Upsert vCluster
        boolean hasAcls = crd.spec.acls != null && !crd.spec.acls.isEmpty();
        upsertVClusterFromCRD(vClusterName, hasAcls, serviceName);

        // 3. Upsert Gateway ServiceAccount for the service (mTLS)
        System.out.println("Upserting Gateway ServiceAccount: " + serviceName);
        upsertServiceAccount(new External()
            .kind("GatewayServiceAccount")
            .apiVersion("gateway/v2")
            .metadata(new ExternalMetadata()
                .vCluster(vClusterName)
                .name(serviceName))
            .spec(new ExternalSpec()
                .type("EXTERNAL")
                .externalNames(List.of("CN=" + serviceName + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"))));
        System.out.println("GatewayServiceAccount/" + serviceName + " upserted");

        // 4. Create topics
        if (crd.spec.topics != null && !crd.spec.topics.isEmpty()) {
            System.out.println("Creating topics...");
            createTopicsFromCRD(vClusterName, crd.spec.topics);
        }

        // 5. Create Console ServiceAccount with ACLs (only if ACLs defined)
        if (crd.spec.acls != null && !crd.spec.acls.isEmpty()) {
            System.out.println("Creating Console ServiceAccount with ACLs: " + serviceName);
            createAclsFromCRD(vClusterName, serviceName, crd.spec.acls);
        }

        // 6. Generate SSL properties
        generateSslProperties(serviceName);

        System.out.println("Service " + serviceName + " setup complete");
    }
}
