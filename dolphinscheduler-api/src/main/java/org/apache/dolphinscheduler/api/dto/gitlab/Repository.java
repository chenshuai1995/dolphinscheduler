package org.apache.dolphinscheduler.api.dto.gitlab;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class Repository {

    private String name;
    private String url;
    private String description;
    private String homepage;
    @JsonProperty("git_ssh_url")
    private String gitSshUrl;
    @JsonProperty("git_http_url")
    private String gitHttpUrl;
    @JsonProperty("visibility_level")
    private Integer visibilityLevel;

}
