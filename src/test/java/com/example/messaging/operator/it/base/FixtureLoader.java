package com.example.messaging.operator.it.base;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.InputStream;
import java.util.List;

/**
 * Loads YAML fixtures for integration tests.
 */
public class FixtureLoader {

    /**
     * Load a single resource of specific type from YAML fixture
     */
    public static <T> T load(KubernetesClient client, String path, Class<T> type) {
        InputStream stream = FixtureLoader.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Fixture not found: " + path);
        }

        return client.load(stream)
            .get().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No resource of type " + type.getSimpleName() + " found in " + path));
    }

    /**
     * Load all resources from YAML fixture
     */
    public static List<HasMetadata> loadAll(KubernetesClient client, String path) {
        InputStream stream = FixtureLoader.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Fixture not found: " + path);
        }

        return client.load(stream).get();
    }
}
