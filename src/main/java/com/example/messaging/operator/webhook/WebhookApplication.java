package com.example.messaging.operator.webhook;

import com.example.messaging.operator.reconciliation.ReconciliationController;
import com.example.messaging.operator.store.CRDStore;
import com.example.messaging.operator.validation.KubernetesResourceLookup;
import com.example.messaging.operator.validation.OwnershipValidator;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class WebhookApplication {
    private static final Logger log = LoggerFactory.getLogger(WebhookApplication.class);

    private static final String PEM_PRIVATE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIVATE_KEY_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PEM_RSA_PRIVATE_KEY_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PEM_RSA_PRIVATE_KEY_FOOTER = "-----END RSA PRIVATE KEY-----";

    private static final String DEFAULT_PORT = "8443";
    private static final String DEFAULT_CERT_PATH = "/etc/webhook/certs/tls.crt";
    private static final String DEFAULT_KEY_PATH = "/etc/webhook/certs/tls.key";

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(System.getenv().getOrDefault("WEBHOOK_PORT", DEFAULT_PORT));
            String certPath = System.getenv().getOrDefault("TLS_CERT_PATH", DEFAULT_CERT_PATH);
            String keyPath = System.getenv().getOrDefault("TLS_KEY_PATH", DEFAULT_KEY_PATH);

            log.info("Starting Webhook server on port {}", port);
            log.info("TLS cert: {}, key: {}", certPath, keyPath);

            KubernetesClient k8sClient = new KubernetesClientBuilder().build();
            log.info("Connected to Kubernetes cluster: {}", k8sClient.getMasterUrl());

            KubernetesResourceLookup resourceLookup = new KubernetesResourceLookup(k8sClient);
            OwnershipValidator ownershipValidator = new OwnershipValidator(resourceLookup);
            WebhookValidator webhookValidator = new WebhookValidator(ownershipValidator);

            HttpsServer httpsServer = createHttpsServer(port, certPath, keyPath);

            httpsServer.createContext("/health", exchange -> {
                String response = "OK";
                exchange.sendResponseHeaders(200, response.length());
                try (var os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });

            WebhookServer webhookHandler = new WebhookServer(webhookValidator, httpsServer);
            webhookHandler.registerEndpoints();

            httpsServer.setExecutor(null);
            httpsServer.start();

            log.info("Webhook server started successfully on port {}", port);

            // Start reconciliation controller if enabled
            CRDStore store = new CRDStore();
            ReconciliationController reconciler = null;
            boolean reconciliationEnabled = Boolean.parseBoolean(
                    System.getenv().getOrDefault("RECONCILIATION_ENABLED", "true"));

            if (reconciliationEnabled) {
                log.info("Starting reconciliation controller...");
                reconciler = new ReconciliationController(k8sClient, store);
                reconciler.start();

                if (reconciler.waitForSync(60, TimeUnit.SECONDS)) {
                    log.info("Reconciliation controller ready");
                } else {
                    log.warn("Reconciliation controller sync timeout - continuing anyway");
                }
            } else {
                log.info("Reconciliation disabled via RECONCILIATION_ENABLED=false");
            }

            final ReconciliationController finalReconciler = reconciler;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down...");
                if (finalReconciler != null) {
                    finalReconciler.close();
                }
                httpsServer.stop(5);
                k8sClient.close();
            }));

            Thread.currentThread().join();

        } catch (Exception e) {
            log.error("Failed to start webhook server", e);
            System.exit(1);
        }
    }

    private static HttpsServer createHttpsServer(int port, String certPath, String keyPath) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        try (FileInputStream certStream = new FileInputStream(certPath)) {
            cert = cf.generateCertificate(certStream);
        }

        byte[] keyBytes = Files.readAllBytes(Path.of(keyPath));
        String keyPem = new String(keyBytes);
        keyPem = keyPem.replace(PEM_PRIVATE_KEY_HEADER, "")
                       .replace(PEM_PRIVATE_KEY_FOOTER, "")
                       .replace(PEM_RSA_PRIVATE_KEY_HEADER, "")
                       .replace(PEM_RSA_PRIVATE_KEY_FOOTER, "")
                       .replaceAll("\\s", "");

        byte[] decoded = java.util.Base64.getDecoder().decode(keyPem);
        java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(decoded);
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
        java.security.PrivateKey privateKey = kf.generatePrivate(keySpec);

        keyStore.setKeyEntry("webhook", privateKey, "".toCharArray(), new Certificate[]{cert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        HttpsServer server = HttpsServer.create(new InetSocketAddress(port), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParams = getSSLContext().getDefaultSSLParameters();
                params.setSSLParameters(sslParams);
            }
        });

        return server;
    }
}
