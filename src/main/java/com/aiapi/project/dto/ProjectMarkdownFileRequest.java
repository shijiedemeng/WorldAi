package com.aiapi.project.dto;

import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.ProjectMarkdownBaseKey;
import com.aiapi.common.enums.ProjectMarkdownFileType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectMarkdownFileRequest {
    private AgentRole agentRole;
    private ProjectMarkdownFileType fileType;
    private ProjectMarkdownBaseKey baseKey;
    private String filePath;
    private String content;
}
