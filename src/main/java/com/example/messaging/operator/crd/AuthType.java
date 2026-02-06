package com.example.messaging.operator.crd;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthType {
    MTLS("mTLS authentication using client certificates"),
    SASL_SSL("SASL/SSL authentication");

    private final String description;
}
