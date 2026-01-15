package com.example.messaging.operator.conduktor.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConduktorCliCredentialsTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create mock secret files
        Files.writeString(tempDir.resolve("console-url"), "http://console:8080");
        Files.writeString(tempDir.resolve("console-token"), "test-token-12345");
        Files.writeString(tempDir.resolve("gateway-url"), "http://gateway:8888");
        Files.writeString(tempDir.resolve("gateway-user"), "admin");
        Files.writeString(tempDir.resolve("gateway-password"), "secret-password");
    }

    @Test
    void load_shouldReadCredentialsFromSecretFiles() {
        ConduktorCliCredentials credentials = ConduktorCliCredentials.load(tempDir.toString());

        assertThat(credentials.getConsoleUrl()).isEqualTo("http://console:8080");
        assertThat(credentials.getConsoleToken()).isEqualTo("test-token-12345");
        assertThat(credentials.getGatewayUrl()).isEqualTo("http://gateway:8888");
        assertThat(credentials.getGatewayUser()).isEqualTo("admin");
        assertThat(credentials.getGatewayPassword()).isEqualTo("secret-password");
    }

    @Test
    void load_shouldTrimWhitespaceFromSecretFiles() throws IOException {
        Files.writeString(tempDir.resolve("console-token"), "  trimmed-token  \n");

        ConduktorCliCredentials credentials = ConduktorCliCredentials.load(tempDir.toString());

        assertThat(credentials.getConsoleToken()).isEqualTo("trimmed-token");
    }

    @Test
    void load_shouldThrowWhenMissingRequiredSecret() throws IOException {
        Files.delete(tempDir.resolve("console-token"));

        assertThatThrownBy(() -> ConduktorCliCredentials.load(tempDir.toString())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing credential")
                .hasMessageContaining("CONDUKTOR_CONSOLE_TOKEN")
                .hasMessageContaining("console-token");
    }

    @Test
    void of_shouldCreateCredentialsDirectly() {
        ConduktorCliCredentials credentials = ConduktorCliCredentials.of("http://test-console:8080", "direct-token", "http://test-gateway:8888", "test-user",
                "test-pass");

        assertThat(credentials.getConsoleUrl()).isEqualTo("http://test-console:8080");
        assertThat(credentials.getConsoleToken()).isEqualTo("direct-token");
        assertThat(credentials.getGatewayUrl()).isEqualTo("http://test-gateway:8888");
        assertThat(credentials.getGatewayUser()).isEqualTo("test-user");
        assertThat(credentials.getGatewayPassword()).isEqualTo("test-pass");
    }

    @Test
    void of_shouldThrowOnNullConsoleUrl() {
        assertThatThrownBy(() -> ConduktorCliCredentials.of(null, "token", "gw-url", "user", "pass")).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("consoleUrl");
    }

    @Test
    void of_shouldThrowOnNullConsoleToken() {
        assertThatThrownBy(() -> ConduktorCliCredentials.of("console-url", null, "gw-url", "user", "pass")).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("consoleToken");
    }

    @Test
    void toString_shouldNotExposeToken() {
        ConduktorCliCredentials credentials = ConduktorCliCredentials.of("http://console:8080", "secret-token", "http://gateway:8888", "admin", "secret-pass");

        String result = credentials.toString();

        assertThat(result).contains("console:8080");
        assertThat(result).contains("gateway:8888");
        assertThat(result).contains("admin");
        assertThat(result).doesNotContain("secret-token");
        assertThat(result).doesNotContain("secret-pass");
    }
}
