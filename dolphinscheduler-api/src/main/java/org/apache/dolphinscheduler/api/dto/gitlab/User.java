package org.apache.dolphinscheduler.api.dto.gitlab;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class User {

    private Integer id;
    private String name;
    private String username;
    private String email;
    @JsonProperty("avatar_url")
    private String avatarUrl;

}
