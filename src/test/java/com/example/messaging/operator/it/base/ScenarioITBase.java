package com.example.messaging.operator.it.base;

import com.example.messaging.operator.webhook.WebhookServer;
import com.example.messaging.operator.webhook.WebhookValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for scenario integration tests.
 * Creates a fresh WebhookServer for each test method.
 */
public abstract class ScenarioITBase extends KubernetesITBase {

    protected WebhookServer webhookServer;
    protected WebhookValidator webhookValidator;
    protected static final int WEBHOOK_PORT = 18081;

    @BeforeEach
    void setupWebhook() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        webhookValidator = new WebhookValidator(ownershipValidator, mapper);
        webhookServer = new WebhookServer(webhookValidator, WEBHOOK_PORT);
        webhookServer.start();
    }

    @AfterEach
    void teardownWebhook() {
        if (webhookServer != null) {
            webhookServer.stop();
        }
    }
}
