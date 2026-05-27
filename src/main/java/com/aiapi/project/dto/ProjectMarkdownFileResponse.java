package com.aiapi.project.dto;

import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.ProjectMarkdownBaseKey;
import com.aiapi.common.enums.ProjectMarkdownFileType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectMarkdownFileResponse {
    Long id;
    AgentRole agentRole;
    ProjectMarkdownFileType fileType;
    ProjectMarkdownBaseKey baseKey;
    String filePath;
    String content;
    Integer versionNo;
    String lastSyncSource;
    java.time.LocalDateTime createdAt;
    java.time.LocalDateTime updatedAt;
}
