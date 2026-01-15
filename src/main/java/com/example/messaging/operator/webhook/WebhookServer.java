package com.example.messaging.operator.webhook;

import com.example.messaging.operator.crd.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kubernetes ValidatingWebhook HTTP server using Java's built-in HttpServer. Exposes endpoints for K8s API server to validate admission requests for all CRD types.
 */
public class WebhookServer {
    private static final Logger log = LoggerFactory.getLogger(WebhookServer.class);

    private HttpServer server;
    private final WebhookValidator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookServer(WebhookValidator validator, int port) throws IOException {
        this.validator = validator;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/health", new HealthHandler());
        registerValidationEndpoints(server);
    }

    public WebhookServer(WebhookValidator validator, HttpServer externalServer) {
        this.validator = validator;
        this.server = externalServer;
    }

    public void registerEndpoints() {
        registerValidationEndpoints(server);
    }

    private void registerValidationEndpoints(HttpServer httpServer) {
        httpServer.createContext("/validate/topic", new ValidationHandler(Topic.class));
        httpServer.createContext("/validate/acl", new ValidationHandler(ACL.class));
        httpServer.createContext("/validate/serviceaccount", new ValidationHandler(ServiceAccount.class));
        httpServer.createContext("/validate/kafkacluster", new ValidationHandler(KafkaCluster.class));
        httpServer.createContext("/validate/consumergroup", new ValidationHandler(ConsumerGroup.class));
    }

    public void start() {
        server.start();
        log.info("Webhook server started on port {}", server.getAddress().getPort());
    }

    public void stop() {
        server.stop(5);
        log.info("Webhook server stopped");
    }

    private static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "OK";
            exchange.sendResponseHeaders(HttpStatus.OK.getCode(), response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private class ValidationHandler implements HttpHandler {
        private final Class<?> resourceClass;

        public ValidationHandler(Class<?> resourceClass) {
            this.resourceClass = resourceClass;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, HttpStatus.METHOD_NOT_ALLOWED.getCode(), "{\"error\": \"Method not allowed\"}");
                return;
            }

            try (var requestBody = exchange.getRequestBody()) {
                AdmissionReview review = objectMapper.readValue(requestBody, AdmissionReview.class);

                AdmissionRequest request = review.getRequest();
                if (request == null) {
                    sendResponse(exchange, HttpStatus.BAD_REQUEST.getCode(), "{\"error\": \"Missing request\"}");
                    return;
                }

                log.info("Validating {} operation on {} in namespace {}", request.getOperation(), request.getName(), request.getNamespace());

                AdmissionResponse admissionResponse = validator.validate(request, resourceClass);
                review.setResponse(admissionResponse);

                String responseJson = objectMapper.writeValueAsString(review);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, HttpStatus.OK.getCode(), responseJson);

                log.info("Validation result: {}", admissionResponse.isAllowed() ? "ALLOWED" : "DENIED");

            } catch (Exception e) {
                log.error("Error processing webhook request", e);
                sendResponse(exchange, HttpStatus.INTERNAL_SERVER_ERROR.getCode(), "{\"error\": \"Internal server error\"}");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.sendResponseHeaders(statusCode, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
