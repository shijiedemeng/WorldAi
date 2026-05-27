package com.aiapi.project.dto;

import com.aiapi.common.enums.ProjectDocumentUsage;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProjectMarkdownDocumentLinkResponse {
    Long id;
    ProjectDocumentUsage usageType;
    String documentId;
    String title;
    String type;
    String status;
    Integer sortNo;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
