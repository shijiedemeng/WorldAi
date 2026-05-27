package com.aiapi.agent.dto;

import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.AgentEngineType;
import com.aiapi.common.enums.AgentStatus;
import com.aiapi.common.enums.McpTransportProtocol;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AgentResponse {
    Long id;
    String agentCode;
    String agentName;
    String projectCode;
    AgentEngineType agentEngineType;
    AgentRole agentRole;
    String agentDesc;
    String capabilityTags;
    String supportedLinkTypes;
    List<String> skillCodes;
    String callbackMode;
    String endpointUrl;
    McpTransportProtocol mcpTransportProtocol;
    AgentStatus status;
    LocalDateTime lastHeartbeatTime;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
