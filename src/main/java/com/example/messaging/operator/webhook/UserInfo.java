package com.example.messaging.operator.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfo {

    @JsonProperty("username") private String username;

    @JsonProperty("uid") private String uid;

    @JsonProperty("groups") private List<String> groups;
}
