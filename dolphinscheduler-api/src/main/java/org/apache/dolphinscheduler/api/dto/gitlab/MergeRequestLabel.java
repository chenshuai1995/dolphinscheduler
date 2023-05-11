package org.apache.dolphinscheduler.api.dto.gitlab;

import java.util.Date;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class MergeRequestLabel {

    private Integer id;
    private String title;
    private String color;
    @JsonProperty("project_id")
    private Integer projectId;
    @JsonProperty("created_at")
    private Date createdAt;
    @JsonProperty("updated_at")
    private Date updatedAt;
    private Boolean template;
    private String description;
    private String type;
    @JsonProperty("group_id")
    private Integer groupId;

}
