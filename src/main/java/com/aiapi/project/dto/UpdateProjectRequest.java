package com.aiapi.project.dto;

import com.aiapi.common.enums.ProjectMarkdownSyncMode;
import com.aiapi.common.enums.ProjectMarkdownBaseSyncMode;
import com.aiapi.common.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProjectRequest {

    @NotBlank
    private String projectName;

    private String projectDesc;
    private String businessGoal;
    private String techStack;
    private String repositoryUrl;
    private String ownerAgentCode;
    private String fileSearchMcpAgentCodes;

    @NotNull
    private ProjectMarkdownSyncMode markdownSyncMode;

    private ProjectMarkdownBaseSyncMode baseMarkdownSyncMode;

    private Boolean baseMarkdownAllowClientUpload;

    @NotNull
    private ProjectStatus status;
}
