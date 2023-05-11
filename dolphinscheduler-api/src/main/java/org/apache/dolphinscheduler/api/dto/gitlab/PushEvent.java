package org.apache.dolphinscheduler.api.dto.gitlab;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PushEvent {

    // 这个提交的分支信息
    private String ref;
    // 提交的详细信息
    private List<Commit> commits;

    private Project project;

    @JsonProperty("user_name")
    private String userName;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Project {

        private String name;
        @JsonProperty("web_url")
        private String webUrl;
        private String namespace;
        private String description;
        @JsonProperty("default_branch")
        private String defaultBranch;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Commit {

        // commit消息
        private String message;
        // 新增的文件列表
        private List<String> added;
        // 修改的文件列表
        private List<String> modified;
        // 删除的文件列表
        private List<String> removed;
    }

}
