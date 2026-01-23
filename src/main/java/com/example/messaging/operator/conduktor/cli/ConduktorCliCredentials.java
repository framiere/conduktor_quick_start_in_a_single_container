package com.example.messaging.operator.conduktor.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds credentials for Conduktor CLI authentication.
 *
 * <p>
 * Credentials can be loaded from:
 * <ul>
 * <li>Environment variables (CONDUKTOR_CONSOLE_URL, CONDUKTOR_CONSOLE_TOKEN, etc.)</li>
 * <li>Mounted secret files (/var/run/secrets/conduktor/console-token, etc.)</li>
 * </ul>
 *
 * <p>
 * The Kubernetes Secret should contain:
 * <ul>
 * <li>console-url: Console API URL</li>
 * <li>console-token: Bearer token for Console authentication</li>
 * <li>gateway-url: Gateway Admin API URL</li>
 * <li>gateway-user: Gateway Basic Auth username</li>
 * <li>gateway-password: Gateway Basic Auth password</li>
 * </ul>
 */
public class ConduktorCliCredentials {

    private static final Logger log = LoggerFactory.getLogger(ConduktorCliCredentials.class);

    private static final String DEFAULT_SECRET_MOUNT_PATH = "/var/run/secrets/conduktor";

    private final String consoleUrl;
    private final String consoleToken;
    private final String gatewayUrl;
    private final String gatewayUser;
    private final String gatewayPassword;

    private ConduktorCliCredentials(String consoleUrl, String consoleToken, String gatewayUrl, String gatewayUser, String gatewayPassword) {
        this.consoleUrl = Objects.requireNonNull(consoleUrl, "consoleUrl is required");
        this.consoleToken = Objects.requireNonNull(consoleToken, "consoleToken is required");
        this.gatewayUrl = Objects.requireNonNull(gatewayUrl, "gatewayUrl is required");
        this.gatewayUser = Objects.requireNonNull(gatewayUser, "gatewayUser is required");
        this.gatewayPassword = Objects.requireNonNull(gatewayPassword, "gatewayPassword is required");
    }

    /**
     * Load credentials from environment variables and/or mounted secret files. Environment variables take precedence over mounted files.
     */
    public static ConduktorCliCredentials load() {
        return load(DEFAULT_SECRET_MOUNT_PATH);
    }

    /**
     * Load credentials from environment variables and/or mounted secret files.
     *
     * @param secretMountPath
     *            path where the K8s secret is mounted
     */
    public static ConduktorCliCredentials load(String secretMountPath) {
        log.info("Loading Conduktor CLI credentials...");

        String consoleUrl = loadValue("CONDUKTOR_CONSOLE_URL", secretMountPath, "console-url");
        String consoleToken = loadValue("CONDUKTOR_CONSOLE_TOKEN", secretMountPath, "console-token");
        String gatewayUrl = loadValue("CONDUKTOR_GATEWAY_URL", secretMountPath, "gateway-url");
        String gatewayUser = loadValue("CONDUKTOR_GATEWAY_USER", secretMountPath, "gateway-user");
        String gatewayPassword = loadValue("CONDUKTOR_GATEWAY_PASSWORD", secretMountPath, "gateway-password");

        log.info("Loaded credentials for Console: {}, Gateway: {}", consoleUrl, gatewayUrl);

        return new ConduktorCliCredentials(consoleUrl, consoleToken, gatewayUrl, gatewayUser, gatewayPassword);
    }

    /**
     * Create credentials directly (for testing).
     */
    public static ConduktorCliCredentials of(String consoleUrl, String consoleToken, String gatewayUrl, String gatewayUser, String gatewayPassword) {
        return new ConduktorCliCredentials(consoleUrl, consoleToken, gatewayUrl, gatewayUser, gatewayPassword);
    }

    private static String loadValue(String envVar, String secretMountPath, String secretKey) {
        // First try environment variable
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isBlank()) {
            log.debug("Loaded {} from environment variable", envVar);
            return envValue.trim();
        }

        // Then try mounted secret file
        Path secretFile = Path.of(secretMountPath, secretKey);
        if (Files.exists(secretFile)) {
            try {
                String fileValue = Files.readString(secretFile).trim();
                if (!fileValue.isBlank()) {
                    log.debug("Loaded {} from secret file {}", secretKey, secretFile);
                    return fileValue;
                }
            } catch (IOException e) {
                log.warn("Failed to read secret file {}: {}", secretFile, e.getMessage());
            }
        }

        throw new IllegalStateException(
                "Missing credential: set %s environment variable or mount secret with key '%s' at %s".formatted(envVar, secretKey, secretMountPath));
    }

    public String getConsoleUrl() {
        return consoleUrl;
    }

    public String getConsoleToken() {
        return consoleToken;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public String getGatewayUser() {
        return gatewayUser;
    }

    public String getGatewayPassword() {
        return gatewayPassword;
    }

    @Override
    public String toString() {
        return "ConduktorCliCredentials{consoleUrl='%s', gatewayUrl='%s', gatewayUser='%s'}".formatted(consoleUrl, gatewayUrl, gatewayUser);
    }
}
