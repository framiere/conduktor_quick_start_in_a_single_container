package com.example.messaging.operator.conduktor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayServiceAccountSpec {

    @Getter
    @RequiredArgsConstructor
    public enum ServiceAccountType {
        LOCAL("Local service account managed by Gateway"),
        EXTERNAL("External service account mapped from client certificates");

        private final String description;
    }

    private ServiceAccountType type;
    private List<String> externalNames;
}
