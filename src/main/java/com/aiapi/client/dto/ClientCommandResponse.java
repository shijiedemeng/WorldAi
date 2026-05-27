package com.aiapi.client.dto;

import com.aiapi.common.enums.DispatchStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientCommandResponse {
    String commandId;
    String clientCode;
    String agentCode;
    String sessionId;
    String sessionCode;
    String requirementNo;
    Long linkId;
    String title;
    String prompt;
    DispatchStatus status;
    String resultSummary;
    String executionDetails;
    String deliverablePath;
    LocalDateTime createdAt;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
    LocalDateTime updatedAt;
}
