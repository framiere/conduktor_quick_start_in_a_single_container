package com.example.messaging.operator.conduktor.transformer;

import com.example.messaging.operator.conduktor.model.ConduktorMetadata;
import com.example.messaging.operator.conduktor.model.VirtualCluster;
import com.example.messaging.operator.conduktor.model.VirtualClusterSpec;
import com.example.messaging.operator.crd.KafkaCluster;

public class KafkaClusterTransformer implements CrdTransformer<KafkaCluster, VirtualCluster> {

    @Override
    public VirtualCluster transform(KafkaCluster source) {
        return VirtualCluster.builder()
                .apiVersion(VirtualCluster.API_VERSION)
                .kind(VirtualCluster.KIND)
                .metadata(ConduktorMetadata.builder()
                        .name(source.getSpec().getClusterId())
                        .build())
                .spec(VirtualClusterSpec.builder()
                        .aclEnabled(true)
                        .build())
                .build();
    }
}
