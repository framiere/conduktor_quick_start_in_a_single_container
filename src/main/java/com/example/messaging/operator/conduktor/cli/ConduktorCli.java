package com.example.messaging.operator.conduktor.cli;

import com.example.messaging.operator.conduktor.model.ConduktorResource;
import com.example.messaging.operator.conduktor.yaml.ConduktorYamlWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConduktorCli {

    private static final Logger log = LoggerFactory.getLogger(ConduktorCli.class);

    private final String cliPath;
    private final int timeoutSeconds;
    private final ConduktorYamlWriter yamlWriter;

    public ConduktorCli() {
        this(
                System.getenv().getOrDefault("CONDUKTOR_CLI_PATH", "conduktor"),
                Integer.parseInt(System.getenv().getOrDefault("CONDUKTOR_CLI_TIMEOUT", "30")));
    }

    public ConduktorCli(String cliPath, int timeoutSeconds) {
        this.cliPath = cliPath;
        this.timeoutSeconds = timeoutSeconds;
        this.yamlWriter = new ConduktorYamlWriter();
    }

    /**
     * Apply a Conduktor resource using dry-run mode (validation only).
     */
    public CliResult applyDryRun(ConduktorResource<?> resource) {
        Path yamlFile = null;
        try {
            yamlFile = yamlWriter.writeToTempFile(resource);
            log.debug("Wrote Conduktor YAML to {}", yamlFile);

            return executeCommand(yamlFile);
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

    private CliResult executeCommand(Path yamlFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    cliPath, "apply", "-f", yamlFile.toString(), "--dry-run");
            pb.redirectErrorStream(false);

            log.info("Executing: {} apply -f {} --dry-run", cliPath, yamlFile);

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

            return new CliResult(exitCode, stdout, stderr);

        } catch (IOException e) {
            log.error("Failed to execute Conduktor CLI", e);
            return new CliResult(-1, "", "Failed to execute CLI: %s".formatted(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CliResult(-1, "", "CLI execution interrupted");
        }
    }

    private String readStream(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
