package com.aiapi.project.dto;

import com.aiapi.common.enums.ProjectMarkdownSyncMode;
import com.aiapi.common.enums.ProjectMarkdownBaseSyncMode;
import com.aiapi.common.enums.ProjectStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectResponse {
    Long id;
    String projectCode;
    String projectName;
    String projectDesc;
    String businessGoal;
    String techStack;
    String repositoryUrl;
    String ownerAgentCode;
    String fileSearchMcpAgentCodes;
    ProjectMarkdownSyncMode markdownSyncMode;
    ProjectMarkdownBaseSyncMode baseMarkdownSyncMode;
    Boolean baseMarkdownAllowClientUpload;
    ProjectStatus status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
