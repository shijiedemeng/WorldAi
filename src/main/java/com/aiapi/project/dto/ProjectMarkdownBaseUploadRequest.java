package com.aiapi.project.dto;

import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.ProjectMarkdownBaseKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectMarkdownBaseUploadRequest {

    private AgentRole agentRole;

    @NotNull
    private ProjectMarkdownBaseKey baseKey;

    private String filePath;

    @NotBlank
    private String content;

    @NotNull
    private Integer versionNo;

    private String clientCode;
    private String agentCode;
}
