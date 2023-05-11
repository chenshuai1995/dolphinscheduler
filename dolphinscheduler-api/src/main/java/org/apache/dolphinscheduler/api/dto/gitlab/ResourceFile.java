package org.apache.dolphinscheduler.api.dto.gitlab;

import org.apache.dolphinscheduler.spi.enums.ResourceType;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Data
@NoArgsConstructor
@JsonPropertyOrder({"id", "pid", "name", "fullName", "description", "isDirctory", "children", "type"})
public class ResourceFile {

    public ResourceFile(int id, int pid, String name, String fullName, String description, boolean isDirctory) {
        this.id = id;
        this.pid = pid;
        this.name = name;
        this.fullName = fullName;
        this.description = description;
        this.isDirctory = isDirctory;
        int directoryFlag = isDirctory ? 1 : 0;
        this.idValue = String.format("%s_%s", id, directoryFlag);
    }

    /**
     * id
     */
    protected int id;
    /**
     * parent id
     */
    protected int pid;
    /**
     * name
     */
    protected String name;
    /**
     * current directory
     */
    protected String currentDir;
    /**
     * full name
     */
    protected String fullName;
    /**
     * description
     */
    protected String description;
    /**
     * is directory
     */
    protected boolean isDirctory;
    /**
     * id value
     */
    protected String idValue;
    /**
     * resoruce type
     */
    protected ResourceType type;
    /**
     * children
     */
    protected List<ResourceFile> children = new ArrayList<>();

    /**
     * add resource component
     * @param resourceComponent resource component
     */
    public void add(ResourceFile resourceComponent) {
        children.add(resourceComponent);
    }

    public void setIdValue(int id, boolean isDirctory) {
        int directoryFlag = isDirctory ? 1 : 0;
        this.idValue = String.format("%s_%s", id, directoryFlag);
    }
}
