package com.aiapi.link.dto;

import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.enums.LinkType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RequirementLinkResponse {
    Long id;
    String requirementNo;
    LinkType linkType;
    String taskTitle;
    String taskDesc;
    String agentCode;
    String developerName;
    LinkStatus status;
    String resultSummary;
    String executionDetails;
    String deliverablePath;
    Long dependsOnLinkId;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
