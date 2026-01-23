package com.example.messaging.operator.conduktor.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.conduktor.model.ConduktorMetadata;
import com.example.messaging.operator.conduktor.model.VirtualCluster;
import com.example.messaging.operator.conduktor.model.VirtualClusterSpec;
import org.junit.jupiter.api.Test;

class ConduktorCliTest {

    private VirtualCluster createTestVirtualCluster() {
        return VirtualCluster.builder().metadata(ConduktorMetadata.builder().name("test-vcluster").build()).spec(VirtualClusterSpec.builder().build()).build();
    }

    @Test
    void apply_withoutCredentials_shouldReturnError() {
        ConduktorCli cli = new ConduktorCli("conduktor", 30, null);

        CliResult result = cli.apply(createTestVirtualCluster());

        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.stderr()).contains("Credentials not configured");
    }

    @Test
    void applyDryRun_shouldNotRequireCredentials() {
        // Dry-run should work even without credentials, but will fail on CLI execution
        // This tests that the code path doesn't reject the request upfront
        ConduktorCli cli = new ConduktorCli("nonexistent-cli", 5, null);

        CliResult result = cli.applyDryRun(createTestVirtualCluster());

        // Should fail because CLI doesn't exist, not because credentials are missing
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.stderr()).doesNotContain("Credentials not configured");
        assertThat(result.stderr()).contains("Failed to execute CLI");
    }

    @Test
    void applyYaml_withoutCredentials_shouldReturnErrorForApply() {
        ConduktorCli cli = new ConduktorCli("conduktor", 30, null);

        CliResult result = cli.applyYaml("kind: GatewayVirtualCluster", false);

        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.stderr()).contains("Credentials not configured");
    }

    @Test
    void applyYaml_dryRunWithoutCredentials_shouldAttemptExecution() {
        ConduktorCli cli = new ConduktorCli("nonexistent-cli", 5, null);

        CliResult result = cli.applyYaml("kind: GatewayVirtualCluster", true);

        // Dry-run should proceed without credentials
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.stderr()).contains("Failed to execute CLI");
        assertThat(result.stderr()).doesNotContain("Credentials not configured");
    }

    @Test
    void cliResult_shouldExposeAllFields() {
        CliResult result = new CliResult(0, "output", "error");

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).isEqualTo("output");
        assertThat(result.stderr()).isEqualTo("error");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void cliResult_nonZeroExitCode_shouldBeFailure() {
        CliResult result = new CliResult(1, "", "error message");

        assertThat(result.isSuccess()).isFalse();
    }
}
