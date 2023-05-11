package org.apache.dolphinscheduler.api.dto.gitlab;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public abstract class WebHook {

    @JsonProperty("repository")
    private Repository repository;
    @JsonProperty("object_kind")
    private String objectKind;

}
