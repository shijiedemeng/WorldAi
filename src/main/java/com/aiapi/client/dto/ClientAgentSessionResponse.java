package com.aiapi.client.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientAgentSessionResponse {
    String requestId;
    String sessionId;
    String sessionCode;
    String clientCode;
    String agentCode;
    String sessionName;
    String sessionType;
    String runtimeType;
    Boolean defaultFlag;
    String status;
    String workspaceDir;
    String errorMessage;
    List<Object> initializedSkills;
    List<ClientSessionEventResponse> events;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
