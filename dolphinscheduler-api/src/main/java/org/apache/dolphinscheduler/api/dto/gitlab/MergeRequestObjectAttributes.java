package org.apache.dolphinscheduler.api.dto.gitlab;

import java.util.Date;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class MergeRequestObjectAttributes {

    private Integer id;
    private Integer iid;
    @JsonProperty("source_branch")
    private String sourceBranch;
    @JsonProperty("target_branch")
    private String targetBranch;
    @JsonProperty("source_project_id")
    private Integer sourceProjectId;
    @JsonProperty("target_project_id")
    private Integer targetProjectId;
    @JsonProperty("author_id")
    private Integer authorId;
    @JsonProperty("assignee_id")
    private Integer assigneeId;
    private String title;
    @JsonProperty("created_at")
    private Date createdAt;
    @JsonProperty("updated_at")
    private Date updatedAt;
    private State state;
    @JsonProperty("merge_status")
    private String mergeStatus;
    private String description;
    private Project source;
    private Project target;
    @JsonProperty("last_commit")
    private Commit lastCommit;
    @JsonProperty("work_in_progress")
    private Boolean workInProgress;
    private String url;
    private Action action;
    private User assignee;

}
