package com.aiapi.client.dto;

import com.aiapi.common.enums.AgentRuntimeType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientAgentRuntimeResponse {
    Long id;
    AgentRuntimeType agentType;
    String agentName;
    String agentVersion;
    String commandPath;
    Boolean availableFlag;
    String capabilityTags;
    Boolean supportsSessionReuse;
    String defaultWorkspaceDir;
    LocalDateTime lastProbeTime;
}
