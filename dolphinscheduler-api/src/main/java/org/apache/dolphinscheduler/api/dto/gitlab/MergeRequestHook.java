package org.apache.dolphinscheduler.api.dto.gitlab;

import java.util.List;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class MergeRequestHook extends WebHook {

    private User user;
    private User assignee;
    private Project project;
    @JsonProperty("object_attributes")
    private MergeRequestObjectAttributes objectAttributes;
    private List<MergeRequestLabel> labels;

}
