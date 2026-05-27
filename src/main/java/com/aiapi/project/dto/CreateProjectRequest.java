package com.aiapi.project.dto;

import com.aiapi.common.enums.ProjectMarkdownSyncMode;
import com.aiapi.common.enums.ProjectMarkdownBaseSyncMode;
import com.aiapi.common.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateProjectRequest {

    @NotBlank
    private String projectCode;

    @NotBlank
    private String projectName;

    private String projectDesc;
    private String businessGoal;
    private String techStack;
    private String repositoryUrl;
    private String ownerAgentCode;
    private String fileSearchMcpAgentCodes;
    private ProjectMarkdownSyncMode markdownSyncMode;
    private ProjectMarkdownBaseSyncMode baseMarkdownSyncMode;
    private Boolean baseMarkdownAllowClientUpload;
    private List<ProjectMarkdownFileRequest> markdownFiles;

    @NotNull
    private ProjectStatus status;
}
