package com.example.messaging.operator.conduktor.transformer;

import com.example.messaging.operator.conduktor.model.ConduktorMetadata;
import com.example.messaging.operator.conduktor.model.GatewayServiceAccount;
import com.example.messaging.operator.conduktor.model.GatewayServiceAccountSpec;
import com.example.messaging.operator.conduktor.model.GatewayServiceAccountSpec.ServiceAccountType;
import com.example.messaging.operator.crd.ServiceAccount;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceAccountTransformer implements CrdTransformer<ServiceAccount, GatewayServiceAccount> {

    private static final Pattern CN_PATTERN = Pattern.compile("CN=([^,]+)");

    @Override
    public GatewayServiceAccount transform(ServiceAccount source) {
        List<String> externalNames = extractCommonNames(source.getSpec().getDn());

        return GatewayServiceAccount.builder()
                .apiVersion(GatewayServiceAccount.API_VERSION)
                .kind(GatewayServiceAccount.KIND)
                .metadata(ConduktorMetadata.builder()
                        .name(source.getSpec().getName())
                        .vCluster(source.getSpec().getClusterRef())
                        .build())
                .spec(GatewayServiceAccountSpec.builder()
                        .type(ServiceAccountType.EXTERNAL)
                        .externalNames(externalNames)
                        .build())
                .build();
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
