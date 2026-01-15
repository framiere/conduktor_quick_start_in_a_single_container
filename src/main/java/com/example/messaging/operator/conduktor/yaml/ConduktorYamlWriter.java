package com.example.messaging.operator.conduktor.yaml;

import com.example.messaging.operator.conduktor.model.ConduktorResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConduktorYamlWriter {

    private final ObjectMapper yamlMapper;

    public ConduktorYamlWriter() {
        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        this.yamlMapper = new ObjectMapper(yamlFactory);
    }

    public String toYaml(ConduktorResource<?> resource) {
        return toYamlObject(resource);
    }

    public String toYaml(Object resource) {
        return toYamlObject(resource);
    }

    private String toYamlObject(Object resource) {
        try {
            return yamlMapper.writeValueAsString(resource);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize resource to YAML", e);
        }
    }

    public Path writeToTempFile(ConduktorResource<?> resource) {
        try {
            Path tempFile = Files.createTempFile("conduktor-", ".yaml");
            String yaml = toYaml(resource);
            Files.writeString(tempFile, yaml);
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write YAML to temp file", e);
        }
    }
}
