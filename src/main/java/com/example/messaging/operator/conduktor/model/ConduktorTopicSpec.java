package com.example.messaging.operator.conduktor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConduktorTopicSpec {
    private Integer partitions;
    private Integer replicationFactor;
    private Map<String, String> configs;
}
