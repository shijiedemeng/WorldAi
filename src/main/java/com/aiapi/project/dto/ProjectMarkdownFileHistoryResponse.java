package com.aiapi.project.dto;

import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.ProjectMarkdownBaseKey;
import com.aiapi.common.enums.ProjectMarkdownChangeSource;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectMarkdownFileHistoryResponse {
    Long id;
    String projectCode;
    AgentRole agentRole;
    ProjectMarkdownBaseKey baseKey;
    String filePath;
    String content;
    Integer versionNo;
    ProjectMarkdownChangeSource changeSource;
    String changeDesc;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
