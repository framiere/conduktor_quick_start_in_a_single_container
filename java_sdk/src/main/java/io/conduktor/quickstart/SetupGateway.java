package io.conduktor.quickstart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SetupGateway {

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

    private final OkHttpClient client;
    private final Gson gson;
    private String cdkToken;

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

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        this.gson = new GsonBuilder().setPrettyPrinting().create();
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
        waitForService("Gateway Admin API", cdkGatewayBaseUrl + "/health/ready");

        // Authenticate
        authenticate();

        System.out.println();
        System.out.println("Setting up vClusters with mTLS: " + vCluster + ", " + vClusterAcl);

        // vCluster 1: demo (ACL disabled)
        setupDemoVCluster();

        // vCluster 2: demo-acl (ACL enabled)
        setupDemoAclVCluster();

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

    private boolean isServiceReady(Request request) {
        try (Response response = client.newCall(request).execute()) {
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

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to authenticate: " + response.code());
            }

            JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
            cdkToken = jsonResponse.get("access_token").getAsString();

            if (cdkToken == null || cdkToken.isEmpty()) {
                throw new IOException("Failed to get access token");
            }
        }

        System.out.println("Authenticated.");
    }

    // Gateway API call (Basic auth)
    private JsonObject gatewayApi(String method, String endpoint, Object body) throws IOException {
        String credentials = Base64.getEncoder().encodeToString(
                (cdkGatewayUser + ":" + cdkGatewayPassword).getBytes());

        Request.Builder builder = new Request.Builder()
                .url(cdkGatewayBaseUrl + endpoint)
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json");

        if ("PUT".equals(method)) {
            builder.put(RequestBody.create(gson.toJson(body), MediaType.parse("application/json")));
        } else if ("POST".equals(method)) {
            builder.post(RequestBody.create(gson.toJson(body), MediaType.parse("application/json")));
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Gateway API error: " + response.code() + " - " + responseBody);
            }
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }

    // Console API call (Bearer token)
    private JsonObject consoleApi(String method, String endpoint, Object body) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(cdkBaseUrl + endpoint)
                .header("Authorization", "Bearer " + cdkToken)
                .header("Content-Type", "application/json");

        if ("PUT".equals(method)) {
            builder.put(RequestBody.create(gson.toJson(body), MediaType.parse("application/json")));
        } else if ("POST".equals(method)) {
            builder.post(RequestBody.create(gson.toJson(body), MediaType.parse("application/json")));
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Console API error: " + response.code() + " - " + responseBody);
            }
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }

    private void generateSslProperties(String user) throws IOException {
        String propsFile = user + ".properties";
        try (FileWriter writer = new FileWriter(propsFile)) {
            writer.write("security.protocol=SSL\n");
            writer.write("ssl.truststore.location=" + certDir + "/" + user + ".truststore.jks\n");
            writer.write("ssl.truststore.password=conduktor\n");
            writer.write("ssl.keystore.location=" + certDir + "/" + user + ".keystore.jks\n");
            writer.write("ssl.keystore.password=conduktor\n");
            writer.write("ssl.key.password=conduktor\n");
        }
        System.out.println("Created: " + propsFile);
    }

    private void setupDemoVCluster() throws IOException {
        System.out.println();
        System.out.println("Creating vCluster: " + vCluster + "...");

        gatewayApi("PUT", "/gateway/v2/virtual-cluster", Map.of(
                "kind", "VirtualCluster",
                "apiVersion", "gateway/v2",
                "metadata", Map.of(
                        "name", vCluster
                ),
                "spec", Map.of(
                        "aclMode", "KAFKA_API",
                        "superUsers", List.of(vClusterAdmin)
                )
        ));
        System.out.println("VirtualCluster/" + vCluster + " created");

        System.out.println("Creating service account: " + vClusterAdmin + "...");
        gatewayApi("PUT", "/gateway/v2/service-account", Map.of(
                "kind", "GatewayServiceAccount",
                "apiVersion", "gateway/v2",
                "metadata", Map.of(
                        "vCluster", vCluster,
                        "name", vClusterAdmin
                ),
                "spec", Map.of(
                        "type", "EXTERNAL",
                        "externalNames", List.of("CN=" + vClusterAdmin + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK")
                )
        ));
        System.out.println("GatewayServiceAccount/" + vClusterAdmin + " created");

        generateSslProperties(vClusterAdmin);

        System.out.println();
        System.out.println("Adding vCluster " + vCluster + " to Console...");
        consoleApi("PUT", "/api/public/console/v2/kafka-cluster", Map.of(
                "apiVersion", "v2",
                "kind", "KafkaCluster",
                "metadata", Map.of(
                        "name", vCluster
                ),
                "spec", Map.of(
                        "displayName", vCluster + " (mTLS)",
                        "bootstrapServers", "localhost:6969",
                        "properties", Map.of(
                                "security.protocol", "SSL",
                                "ssl.truststore.location", "/var/lib/conduktor/certs/" + vClusterAdmin + ".truststore.jks",
                                "ssl.truststore.password", "conduktor",
                                "ssl.keystore.location", "/var/lib/conduktor/certs/" + vClusterAdmin + ".keystore.jks",
                                "ssl.keystore.password", "conduktor",
                                "ssl.key.password", "conduktor"
                        ),
                        "kafkaFlavor", Map.of(
                                "type", "Gateway",
                                "url", cdkGatewayBaseUrl,
                                "user", cdkGatewayUser,
                                "password", cdkGatewayPassword,
                                "virtualCluster", vCluster
                        )
                )
        ));
        System.out.println("KafkaCluster/" + vCluster + " created in Console");
    }

    private void setupDemoAclVCluster() throws IOException {
        System.out.println();
        System.out.println("Creating vCluster: " + vClusterAcl + " (ACL enabled)...");

        gatewayApi("PUT", "/gateway/v2/virtual-cluster", Map.of(
                "kind", "VirtualCluster",
                "apiVersion", "gateway/v2",
                "metadata", Map.of(
                        "name", vClusterAcl
                ),
                "spec", Map.of(
                        "aclEnabled", true,
                        "superUsers", List.of(vClusterAclAdmin)
                )
        ));
        System.out.println("VirtualCluster/" + vClusterAcl + " created");

        System.out.println("Creating service account: " + vClusterAclAdmin + "...");
        gatewayApi("PUT", "/gateway/v2/service-account", Map.of(
                "kind", "GatewayServiceAccount",
                "apiVersion", "gateway/v2",
                "metadata", Map.of(
                        "vCluster", vClusterAcl,
                        "name", vClusterAclAdmin
                ),
                "spec", Map.of(
                        "type", "EXTERNAL",
                        "externalNames", List.of("CN=" + vClusterAclAdmin + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK")
                )
        ));
        System.out.println("GatewayServiceAccount/" + vClusterAclAdmin + " created");

        System.out.println("Creating service account: " + vClusterAclUser + "...");
        gatewayApi("PUT", "/gateway/v2/service-account", Map.of(
                "kind", "GatewayServiceAccount",
                "apiVersion", "gateway/v2",
                "metadata", Map.of(
                        "vCluster", vClusterAcl,
                        "name", vClusterAclUser
                ),
                "spec", Map.of(
                        "type", "EXTERNAL",
                        "externalNames", List.of("CN=" + vClusterAclUser + ",OU=TEST,O=CONDUKTOR,L=LONDON,C=UK")
                )
        ));
        System.out.println("GatewayServiceAccount/" + vClusterAclUser + " created");

        generateSslProperties(vClusterAclAdmin);
        generateSslProperties(vClusterAclUser);

        System.out.println();
        System.out.println("Adding vCluster " + vClusterAcl + " to Console...");
        consoleApi("PUT", "/api/public/console/v2/kafka-cluster", Map.of(
                "apiVersion", "v2",
                "kind", "KafkaCluster",
                "metadata", Map.of(
                        "name", vClusterAcl
                ),
                "spec", Map.of(
                        "displayName", vClusterAcl + " (mTLS + ACL)",
                        "bootstrapServers", "localhost:6969",
                        "properties", Map.of(
                                "security.protocol", "SSL",
                                "ssl.truststore.location", "/var/lib/conduktor/certs/" + vClusterAclAdmin + ".truststore.jks",
                                "ssl.truststore.password", "conduktor",
                                "ssl.keystore.location", "/var/lib/conduktor/certs/" + vClusterAclAdmin + ".keystore.jks",
                                "ssl.keystore.password", "conduktor",
                                "ssl.key.password", "conduktor"
                        ),
                        "kafkaFlavor", Map.of(
                                "type", "Gateway",
                                "url", cdkGatewayBaseUrl,
                                "user", cdkGatewayUser,
                                "password", cdkGatewayPassword,
                                "virtualCluster", vClusterAcl
                        )
                )
        ));
        System.out.println("KafkaCluster/" + vClusterAcl + " created in Console");

        System.out.println();
        System.out.println("Creating Console ServiceAccount with ACLs for " + vClusterAclUser + "...");
        consoleApi("PUT", "/api/public/self-serve/v1/cluster/" + vClusterAcl + "/service-account", Map.of(
                "apiVersion", "v1",
                "kind", "ServiceAccount",
                "metadata", Map.of(
                        "cluster", vClusterAcl,
                        "name", vClusterAclUser
                ),
                "spec", Map.of(
                        "authorization", Map.of(
                                "type", "KAFKA_ACL",
                                "acls", List.of(
                                        Map.of(
                                                "type", "TOPIC",
                                                "name", "click",
                                                "patternType", "PREFIXED",
                                                "operations", List.of("read"),
                                                "host", "*",
                                                "permission", "Allow"
                                        ),
                                        Map.of(
                                                "type", "CONSUMER_GROUP",
                                                "name", "myconsumer-",
                                                "patternType", "PREFIXED",
                                                "operations", List.of("read"),
                                                "host", "*",
                                                "permission", "Allow"
                                        )
                                )
                        )
                )
        ));
        System.out.println("ServiceAccount/" + vClusterAclUser + " created in Console");
    }
}
