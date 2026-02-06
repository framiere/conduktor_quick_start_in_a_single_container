package com.example.messaging.operator.conduktor.transformer;

import com.example.messaging.operator.conduktor.model.ConduktorMetadata;
import com.example.messaging.operator.conduktor.model.GatewayServiceAccount;
import com.example.messaging.operator.conduktor.model.GatewayServiceAccountSpec;
import com.example.messaging.operator.conduktor.model.GatewayServiceAccountSpec.ServiceAccountType;
import com.example.messaging.operator.crd.AuthType;
import com.example.messaging.operator.crd.KafkaCluster;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.crd.ServiceAccountSpec;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceAccountTransformer implements CrdTransformer<ServiceAccount, GatewayServiceAccount> {

    private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,]+)");

    private final CRDStore store;

    public ServiceAccountTransformer(CRDStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public GatewayServiceAccount transform(ServiceAccount source) {
        ServiceAccountSpec spec = source.getSpec();
        KafkaCluster cluster = resolveKafkaCluster(source);
        AuthType authType = cluster.getSpec().getAuthType() != null ? cluster.getSpec().getAuthType() : AuthType.MTLS;
        List<String> externalNames = resolveExternalNames(spec, authType);

        return GatewayServiceAccount.builder()
                .apiVersion(GatewayServiceAccount.API_VERSION)
                .kind(GatewayServiceAccount.KIND)
                .metadata(ConduktorMetadata.builder()
                        .name(spec.getName())
                        .vCluster(cluster.getSpec().getClusterId())
                        .build())
                .spec(GatewayServiceAccountSpec.builder()
                        .type(ServiceAccountType.EXTERNAL)
                        .externalNames(externalNames)
                        .build())
                .build();
    }

    private KafkaCluster resolveKafkaCluster(ServiceAccount source) {
        String namespace = source.getMetadata().getNamespace();
        String clusterRef = source.getSpec().getClusterRef();

        KafkaCluster cluster = store.get(CRDKind.KAFKA_CLUSTER, namespace, clusterRef);
        if (cluster == null) {
            throw new IllegalStateException(
                    "Cannot resolve KafkaCluster '%s' in namespace '%s'"
                            .formatted(clusterRef, namespace));
        }
        return cluster;
    }

    private List<String> resolveExternalNames(ServiceAccountSpec spec, AuthType authType) {
        if (authType == AuthType.SASL_SSL) {
            return List.of(spec.getName());
        }

        // MTLS: use DN extraction if dn is present, otherwise fall back to name
        if (spec.getDn() == null || spec.getDn().isEmpty()) {
            return List.of(spec.getName());
        }

        return extractCommonNames(spec.getDn());
    }

    private List<String> extractCommonNames(List<String> dns) {
        return dns.stream()
                .map(this::extractCN)
                .toList();
    }

    private String extractCN(String dn) {
        Matcher matcher = CN_PATTERN.matcher(dn);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return dn;
    }
}
