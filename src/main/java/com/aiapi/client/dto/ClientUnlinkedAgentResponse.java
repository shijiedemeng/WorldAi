package com.aiapi.client.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientUnlinkedAgentResponse {
    String agentCode;
    Boolean enabledFlag;
    String skillsDir;
    String workspaceDir;
    String workerCommand;
    String reason;
    LocalDateTime lastSeenTime;
}
