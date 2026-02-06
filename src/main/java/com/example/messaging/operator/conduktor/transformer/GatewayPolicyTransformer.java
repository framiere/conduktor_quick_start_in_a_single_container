package com.example.messaging.operator.conduktor.transformer;

import com.example.messaging.operator.conduktor.model.ConduktorInterceptor;
import com.example.messaging.operator.conduktor.model.ConduktorInterceptorMetadata;
import com.example.messaging.operator.conduktor.model.ConduktorInterceptorSpec;
import com.example.messaging.operator.conduktor.model.InterceptorScope;
import com.example.messaging.operator.crd.GatewayPolicy;
import com.example.messaging.operator.crd.GatewayPolicySpec;
import com.example.messaging.operator.crd.KafkaCluster;
import com.example.messaging.operator.crd.Scope;
import com.example.messaging.operator.crd.ScopeSpec;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import java.util.Objects;

public class GatewayPolicyTransformer {

    private final CRDStore store;

    public GatewayPolicyTransformer(CRDStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    public ConduktorInterceptor transform(GatewayPolicy source) {
        GatewayPolicySpec spec = source.getSpec();
        String namespace = source.getMetadata().getNamespace();

        String interceptorName = buildInterceptorName(namespace, source.getMetadata().getName());
        InterceptorScope scope = buildScope(spec, namespace);

        return ConduktorInterceptor.builder()
                .apiVersion(ConduktorInterceptor.API_VERSION)
                .kind(ConduktorInterceptor.KIND)
                .metadata(ConduktorInterceptorMetadata.builder()
                        .name(interceptorName)
                        .scope(scope)
                        .build())
                .spec(ConduktorInterceptorSpec.builder()
                        .pluginClass(spec.getPolicyType().getPluginClass())
                        .priority(spec.getPriority())
                        .config(spec.getConfig())
                        .build())
                .build();
    }

    private String buildInterceptorName(String namespace, String name) {
        return namespace + "--" + name;
    }

    private InterceptorScope buildScope(GatewayPolicySpec spec, String namespace) {
        Scope scopeResource = store.get(CRDKind.SCOPE, namespace, spec.getScopeRef());
        if (scopeResource == null) {
            throw new IllegalStateException(
                    "Cannot resolve scope: Scope '%s' not found in namespace '%s'"
                            .formatted(spec.getScopeRef(), namespace));
        }

        ScopeSpec scopeSpec = scopeResource.getSpec();
        InterceptorScope.InterceptorScopeBuilder builder = InterceptorScope.builder();

        if (scopeSpec.getClusterRef() != null) {
            KafkaCluster cluster = store.get(CRDKind.KAFKA_CLUSTER, namespace, scopeSpec.getClusterRef());
            if (cluster == null) {
                throw new IllegalStateException(
                        "Cannot resolve scope: KafkaCluster '%s' not found in namespace '%s'"
                                .formatted(scopeSpec.getClusterRef(), namespace));
            }
            builder.vCluster(cluster.getSpec().getClusterId());
        }

        if (scopeSpec.getServiceAccountRef() != null) {
            ServiceAccount sa = store.get(CRDKind.SERVICE_ACCOUNT, namespace, scopeSpec.getServiceAccountRef());
            if (sa == null) {
                throw new IllegalStateException(
                        "Cannot resolve scope: ServiceAccount '%s' not found in namespace '%s'"
                                .formatted(scopeSpec.getServiceAccountRef(), namespace));
            }
            builder.username(sa.getSpec().getName());
        }

        if (scopeSpec.getGroupRef() != null) {
            builder.group(scopeSpec.getGroupRef());
        }

        InterceptorScope scope = builder.build();

        if (scope.getVCluster() == null && scope.getGroup() == null && scope.getUsername() == null) {
            return null;
        }

        return scope;
    }
}
