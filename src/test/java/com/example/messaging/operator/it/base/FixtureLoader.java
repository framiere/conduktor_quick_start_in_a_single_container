package com.example.messaging.operator.it.base;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Loads YAML fixtures for integration tests.
 */
public class FixtureLoader {

    private FixtureLoader() {
        throw new AssertionError("Utility class");
    }

    /**
     * Load a single resource of specific type from YAML fixture
     */
    public static <T> T load(KubernetesClient client, String path, Class<T> type) {
        Objects.requireNonNull(client, "Client cannot be null");

        try (InputStream stream = FixtureLoader.class.getResourceAsStream(path)) {
            Objects.requireNonNull(stream, "Fixture not found: " + path);

            return client.load(stream)
                    .get()
                    .stream()
                    .filter(type::isInstance)
                    .map(type::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No resource of type " + type.getSimpleName() + " found in " + path));
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to load fixture: " + path, e);
        }
    }

    /**
     * Load all resources from YAML fixture
     */
    public static List<HasMetadata> loadAll(KubernetesClient client, String path) {
        Objects.requireNonNull(client, "Client cannot be null");

        try (InputStream stream = FixtureLoader.class.getResourceAsStream(path)) {
            Objects.requireNonNull(stream, "Fixture not found: " + path);

            return client.load(stream).get();
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to load fixture: " + path, e);
        }
    }
}
