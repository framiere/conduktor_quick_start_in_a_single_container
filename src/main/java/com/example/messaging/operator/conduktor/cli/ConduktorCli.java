package com.example.messaging.operator.conduktor.cli;

import com.example.messaging.operator.conduktor.model.ConduktorResource;
import com.example.messaging.operator.conduktor.yaml.ConduktorYamlWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes Conduktor CLI commands for applying resources to Console and Gateway.
 *
 * <p>
 * Supports two modes:
 * <ul>
 * <li>Dry-run: Validates resources without applying (no credentials needed)</li>
 * <li>Apply: Actually creates/updates resources (requires credentials)</li>
 * </ul>
 *
 * <p>
 * Credentials are loaded from environment variables or mounted Kubernetes Secrets via {@link ConduktorCliCredentials}.
 */
public class ConduktorCli {

    private static final Logger log = LoggerFactory.getLogger(ConduktorCli.class);

    private final String cliPath;
    private final int timeoutSeconds;
    private final ConduktorYamlWriter yamlWriter;
    private final ConduktorCliCredentials credentials;

    /**
     * Create CLI executor with credentials loaded from environment/secrets.
     */
    public ConduktorCli() {
        this(System.getenv().getOrDefault("CONDUKTOR_CLI_PATH", "conduktor"), Integer.parseInt(System.getenv().getOrDefault("CONDUKTOR_CLI_TIMEOUT", "30")), null);
    }

    /**
     * Create CLI executor with explicit credentials.
     */
    public ConduktorCli(ConduktorCliCredentials credentials) {
        this(System.getenv().getOrDefault("CONDUKTOR_CLI_PATH", "conduktor"), Integer.parseInt(System.getenv().getOrDefault("CONDUKTOR_CLI_TIMEOUT", "30")), credentials);
    }

    public ConduktorCli(String cliPath, int timeoutSeconds, ConduktorCliCredentials credentials) {
        this.cliPath = cliPath;
        this.timeoutSeconds = timeoutSeconds;
        this.yamlWriter = new ConduktorYamlWriter();
        this.credentials = credentials;
    }

    /**
     * Apply a Conduktor resource using dry-run mode (validation only). No credentials required for dry-run.
     */
    public CliResult applyDryRun(ConduktorResource<?> resource) {
        return executeApply(resource, true);
    }

    /**
     * Apply a Conduktor resource to Console/Gateway. Requires credentials to be configured.
     */
    public CliResult apply(ConduktorResource<?> resource) {
        if (credentials == null) {
            return new CliResult(-1, "", "Credentials not configured. Use ConduktorCli(credentials) constructor.");
        }
        return executeApply(resource, false);
    }

    private CliResult executeApply(ConduktorResource<?> resource, boolean dryRun) {
        Path yamlFile = null;
        try {
            yamlFile = yamlWriter.writeToTempFile(resource);
            log.debug("Wrote Conduktor YAML to {}", yamlFile);

            return executeCommand(yamlFile, dryRun);
        } finally {
            if (yamlFile != null) {
                try {
                    Files.deleteIfExists(yamlFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", yamlFile, e);
                }
            }
        }
    }

    /**
     * Apply a raw YAML string to Console/Gateway.
     */
    public CliResult applyYaml(String yamlContent, boolean dryRun) {
        if (!dryRun && credentials == null) {
            return new CliResult(-1, "", "Credentials not configured for apply mode.");
        }

        Path yamlFile = null;
        try {
            yamlFile = Files.createTempFile("conduktor-", ".yaml");
            Files.writeString(yamlFile, yamlContent);
            log.debug("Wrote YAML to {}", yamlFile);

            return executeCommand(yamlFile, dryRun);
        } catch (IOException e) {
            log.error("Failed to write YAML to temp file", e);
            return new CliResult(-1, "", "Failed to write YAML: %s".formatted(e.getMessage()));
        } finally {
            if (yamlFile != null) {
                try {
                    Files.deleteIfExists(yamlFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", yamlFile, e);
                }
            }
        }
    }

    private CliResult executeCommand(Path yamlFile, boolean dryRun) {
        try {
            List<String> command = buildCommand(yamlFile, dryRun);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);

            // Set environment variables for authentication
            if (!dryRun && credentials != null) {
                Map<String, String> env = pb.environment();
                env.put("CDK_BASE_URL", credentials.getConsoleUrl());
                env.put("CDK_TOKEN", credentials.getConsoleToken());
                env.put("CDK_GATEWAY_BASE_URL", credentials.getGatewayUrl());
                env.put("CDK_GATEWAY_USER", credentials.getGatewayUser());
                env.put("CDK_GATEWAY_PASSWORD", credentials.getGatewayPassword());
            }

            log.info("Executing: {}", String.join(" ", command));

            Process process = pb.start();

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CliResult(-1, "", "Command timed out after %d seconds".formatted(timeoutSeconds));
            }

            int exitCode = process.exitValue();
            log.info("CLI exited with code {}", exitCode);

            if (exitCode != 0) {
                log.warn("CLI stderr: {}", stderr);
            }

            return new CliResult(exitCode, stdout, stderr);

        } catch (IOException e) {
            log.error("Failed to execute Conduktor CLI", e);
            return new CliResult(-1, "", "Failed to execute CLI: %s".formatted(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CliResult(-1, "", "CLI execution interrupted");
        }
    }

    private List<String> buildCommand(Path yamlFile, boolean dryRun) {
        List<String> command = new ArrayList<>();
        command.add(cliPath);
        command.add("apply");
        command.add("-f");
        command.add(yamlFile.toString());

        if (dryRun) {
            command.add("--dry-run");
        }

        return command;
    }

    private String readStream(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
