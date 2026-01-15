package com.example.messaging.operator.conduktor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"apiVersion", "kind", "metadata", "spec"})
public class ConduktorInterceptor {

    public static final String API_VERSION = "gateway/v2";
    public static final String KIND = "Interceptor";

    private String apiVersion;
    private String kind;
    private ConduktorInterceptorMetadata metadata;
    private ConduktorInterceptorSpec spec;
}
