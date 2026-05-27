package com.aiapi.client.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientControllableAgentResponse {
    Long id;
    String agentCode;
    String agentName;
    String projectCode;
    String agentEngineType;
    String agentRole;
    String mcpTransportProtocol;
    String status;
    Boolean enabledFlag;
    List<String> skillCodes;
    String skillsDir;
    String workspaceDir;
    String workerCommand;
    LocalDateTime lastSeenTime;
}
