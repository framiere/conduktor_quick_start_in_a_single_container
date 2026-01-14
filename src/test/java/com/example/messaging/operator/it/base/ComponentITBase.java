package com.example.messaging.operator.it.base;

import com.example.messaging.operator.webhook.WebhookServer;
import com.example.messaging.operator.webhook.WebhookValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for component integration tests.
 * Shares a single WebhookServer instance across all tests in the class.
 */
public abstract class ComponentITBase extends KubernetesITBase {

    protected static WebhookServer webhookServer;
    protected static WebhookValidator webhookValidator;
    protected static final int WEBHOOK_PORT = 18080;

    @BeforeAll
    static void setupWebhook() throws Exception {
        setupKubernetes();

        ObjectMapper mapper = new ObjectMapper();
        webhookValidator = new WebhookValidator(ownershipValidator, mapper);
        webhookServer = new WebhookServer(webhookValidator, WEBHOOK_PORT);
        webhookServer.start();
    }

    @AfterAll
    static void teardownWebhook() {
        if (webhookServer != null) {
            webhookServer.stop();
        }
        teardownKubernetes();
    }
}
