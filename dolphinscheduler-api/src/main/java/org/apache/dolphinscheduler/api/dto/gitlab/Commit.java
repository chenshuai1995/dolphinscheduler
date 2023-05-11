package org.apache.dolphinscheduler.api.dto.gitlab;

import java.util.Date;
import java.util.List;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class Commit {

    private String id;
    private String message;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private Date timestamp;
    private String url;
    private User author;
    private List<String> added;
    private List<String> modified;
    private List<String> removed;

}
