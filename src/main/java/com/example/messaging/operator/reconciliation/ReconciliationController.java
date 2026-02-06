package com.example.messaging.operator.reconciliation;

import com.example.messaging.operator.conduktor.cli.CliResult;
import com.example.messaging.operator.conduktor.cli.ConduktorCli;
import com.example.messaging.operator.conduktor.cli.ConduktorCliCredentials;
import com.example.messaging.operator.conduktor.model.ConduktorResource;
import com.example.messaging.operator.conduktor.transformer.KafkaClusterTransformer;
import com.example.messaging.operator.conduktor.transformer.ServiceAccountTransformer;
import com.example.messaging.operator.conduktor.transformer.TopicTransformer;
import com.example.messaging.operator.crd.KafkaCluster;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.store.CRDStore;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ReconciliationController implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);
    private static final long RESYNC_PERIOD_MS = 30_000;

    private final KubernetesClient client;
    private final ConduktorCli cli;
    private final CRDStore store;
    private final SharedInformerFactory informerFactory;
    private final ExecutorService reconcileExecutor;
    private final List<SharedIndexInformer<?>> informers = new ArrayList<>();

    private final KafkaClusterTransformer kafkaClusterTransformer;
    private final ServiceAccountTransformer serviceAccountTransformer;
    private final TopicTransformer topicTransformer;

    public ReconciliationController(KubernetesClient client, CRDStore store) {
        this.client = client;
        this.store = store;
        this.informerFactory = client.informers();
        this.reconcileExecutor = Executors.newFixedThreadPool(4);

        // Initialize transformers
        this.kafkaClusterTransformer = new KafkaClusterTransformer();
        this.serviceAccountTransformer = new ServiceAccountTransformer(store);
        this.topicTransformer = new TopicTransformer(store);

        // Initialize CLI with credentials
        ConduktorCliCredentials credentials = loadCredentials();
        this.cli = credentials != null ? new ConduktorCli(credentials) : null;

        if (cli == null) {
            log.warn("Conduktor CLI credentials not configured - reconciliation will be disabled");
        }
    }

    private ConduktorCliCredentials loadCredentials() {
        try {
            return ConduktorCliCredentials.load();
        } catch (Exception e) {
            log.warn("Failed to load Conduktor CLI credentials: {}", e.getMessage());
            return null;
        }
    }

    public void start() {
        if (cli == null) {
            log.error("Cannot start reconciliation - CLI credentials not configured");
            return;
        }

        log.info("Starting reconciliation controller...");

        // Register informers for each CRD type
        registerKafkaClusterInformer();
        registerServiceAccountInformer();
        registerTopicInformer();

        // Start all informers
        informerFactory.startAllRegisteredInformers();

        log.info("Reconciliation controller started - watching {} CRD types", informers.size());
    }

    private void registerKafkaClusterInformer() {
        SharedIndexInformer<KafkaCluster> informer = informerFactory
                .sharedIndexInformerFor(KafkaCluster.class, RESYNC_PERIOD_MS);

        informer.addEventHandler(createHandler(
                "KafkaCluster",
                kafkaClusterTransformer::transform,
                this::getKafkaClusterDeleteName
        ));

        informers.add(informer);
        log.info("Registered KafkaCluster informer");
    }

    private void registerServiceAccountInformer() {
        SharedIndexInformer<ServiceAccount> informer = informerFactory
                .sharedIndexInformerFor(ServiceAccount.class, RESYNC_PERIOD_MS);

        informer.addEventHandler(createHandler(
                "ServiceAccount",
                serviceAccountTransformer::transform,
                this::getServiceAccountDeleteName
        ));

        informers.add(informer);
        log.info("Registered ServiceAccount informer");
    }

    private void registerTopicInformer() {
        SharedIndexInformer<Topic> informer = informerFactory
                .sharedIndexInformerFor(Topic.class, RESYNC_PERIOD_MS);

        informer.addEventHandler(createHandler(
                "Topic",
                this::transformTopic,
                this::getTopicDeleteName
        ));

        informers.add(informer);
        log.info("Registered Topic informer");
    }

    private ConduktorResource<?> transformTopic(Topic topic) {
        // Store the topic first so transformer can resolve references
        storeResource(topic);
        return topicTransformer.transform(topic);
    }

    private void storeResource(Topic topic) {
        // Ensure ServiceAccount is in store for reference resolution
        String namespace = topic.getMetadata().getNamespace();
        String saRef = topic.getSpec().getServiceRef();

        ServiceAccount sa = client.resources(ServiceAccount.class)
                .inNamespace(namespace)
                .withName(saRef)
                .get();

        if (sa != null) {
            store.create(com.example.messaging.operator.store.CRDKind.SERVICE_ACCOUNT, namespace, sa);
        }
    }

    private String getKafkaClusterDeleteName(KafkaCluster cluster) {
        return cluster.getSpec().getClusterId();
    }

    private String getServiceAccountDeleteName(ServiceAccount sa) {
        return sa.getSpec().getName();
    }

    private String getTopicDeleteName(Topic topic) {
        return topic.getSpec().getName();
    }

    private <T extends HasMetadata> ResourceEventHandler<T> createHandler(
            String resourceType,
            Function<T, ConduktorResource<?>> transformer,
            Function<T, String> deleteNameExtractor) {

        return new ResourceEventHandler<>() {
            @Override
            public void onAdd(T resource) {
                reconcileExecutor.submit(() -> handleAdd(resourceType, resource, transformer));
            }

            @Override
            public void onUpdate(T oldResource, T newResource) {
                reconcileExecutor.submit(() -> handleUpdate(resourceType, newResource, transformer));
            }

            @Override
            public void onDelete(T resource, boolean deletedFinalStateUnknown) {
                reconcileExecutor.submit(() -> handleDelete(resourceType, resource, deleteNameExtractor));
            }
        };
    }

    private <T extends HasMetadata> void handleAdd(String resourceType, T resource, Function<T, ConduktorResource<?>> transformer) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();

        log.info("[RECONCILE] ADD {} {}/{}", resourceType, namespace, name);

        try {
            ConduktorResource<?> conduktorResource = transformer.apply(resource);
            CliResult result = cli.apply(conduktorResource);

            if (result.exitCode() == 0) {
                log.info("[RECONCILE] SUCCESS {} {}/{} -> Conduktor", resourceType, namespace, name);
                log.debug("CLI stdout: {}", result.stdout());
            } else {
                log.error("[RECONCILE] FAILED {} {}/{}: exit={}, stderr={}",
                        resourceType, namespace, name, result.exitCode(), result.stderr());
            }
        } catch (Exception e) {
            log.error("[RECONCILE] ERROR {} {}/{}: {}", resourceType, namespace, name, e.getMessage(), e);
        }
    }

    private <T extends HasMetadata> void handleUpdate(String resourceType, T resource, Function<T, ConduktorResource<?>> transformer) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();

        log.info("[RECONCILE] UPDATE {} {}/{}", resourceType, namespace, name);

        try {
            ConduktorResource<?> conduktorResource = transformer.apply(resource);
            CliResult result = cli.apply(conduktorResource);

            if (result.exitCode() == 0) {
                log.info("[RECONCILE] SUCCESS {} {}/{} updated in Conduktor", resourceType, namespace, name);
            } else {
                log.error("[RECONCILE] FAILED {} {}/{}: exit={}, stderr={}",
                        resourceType, namespace, name, result.exitCode(), result.stderr());
            }
        } catch (Exception e) {
            log.error("[RECONCILE] ERROR {} {}/{}: {}", resourceType, namespace, name, e.getMessage(), e);
        }
    }

    private <T extends HasMetadata> void handleDelete(String resourceType, T resource, Function<T, String> deleteNameExtractor) {
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();
        String conduktorName = deleteNameExtractor.apply(resource);

        log.info("[RECONCILE] DELETE {} {}/{} (Conduktor name: {})", resourceType, namespace, name, conduktorName);

        log.warn("[RECONCILE] DELETE operations are delegated to Kubernetes RBAC - resource {} removed from K8s but may remain in Conduktor",
                conduktorName);
    }

    public boolean waitForSync(long timeout, TimeUnit unit) {
        log.info("Waiting for informer caches to sync...");

        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);

        for (SharedIndexInformer<?> informer : informers) {
            while (!informer.hasSynced()) {
                if (System.currentTimeMillis() > deadline) {
                    log.error("Timeout waiting for informer sync");
                    return false;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        log.info("All informer caches synced");
        return true;
    }

    @Override
    public void close() {
        log.info("Shutting down reconciliation controller...");

        informerFactory.stopAllRegisteredInformers();

        reconcileExecutor.shutdown();
        try {
            if (!reconcileExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                reconcileExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            reconcileExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Reconciliation controller stopped");
    }
}
