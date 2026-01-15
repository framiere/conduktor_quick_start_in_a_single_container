package com.example.messaging.operator.conduktor.transformer;

import com.example.messaging.operator.conduktor.model.ConduktorMetadata;
import com.example.messaging.operator.conduktor.model.ConduktorTopic;
import com.example.messaging.operator.conduktor.model.ConduktorTopicSpec;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import java.util.Objects;

public class TopicTransformer implements CrdTransformer<Topic, ConduktorTopic> {

    private final CRDStore store;

    public TopicTransformer(CRDStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public ConduktorTopic transform(Topic source) {
        String clusterName = resolveClusterName(source);

        return ConduktorTopic.builder()
                .apiVersion(ConduktorTopic.API_VERSION)
                .kind(ConduktorTopic.KIND)
                .metadata(ConduktorMetadata.builder()
                        .name(source.getSpec().getName())
                        .cluster(clusterName)
                        .build())
                .spec(ConduktorTopicSpec.builder()
                        .partitions(source.getSpec().getPartitions())
                        .replicationFactor(source.getSpec().getReplicationFactor())
                        .configs(source.getSpec().getConfig().isEmpty() ? null : source.getSpec().getConfig())
                        .build())
                .build();
    }

    private String resolveClusterName(Topic topic) {
        String serviceRef = topic.getSpec().getServiceRef();
        String namespace = topic.getMetadata().getNamespace();

        ServiceAccount sa = store.get(CRDKind.SERVICE_ACCOUNT, namespace, serviceRef);
        if (sa == null) {
            throw new IllegalStateException("""
                    Cannot resolve cluster: ServiceAccount '%s' not found in namespace '%s'"""
                    .formatted(serviceRef, namespace));
        }

        return sa.getSpec().getClusterRef();
    }
}
