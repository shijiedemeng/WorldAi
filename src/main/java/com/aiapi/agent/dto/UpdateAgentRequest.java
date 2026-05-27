package com.aiapi.agent.dto;

import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.AgentEngineType;
import com.aiapi.common.enums.AgentStatus;
import com.aiapi.common.enums.McpTransportProtocol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAgentRequest {

    @NotBlank
    private String agentName;

    @NotBlank
    private String projectCode;

    private AgentEngineType agentEngineType;

    @NotNull
    private AgentRole agentRole;

    private String agentDesc;
    private String capabilityTags;
    private String supportedLinkTypes;
    private List<String> skillCodes;
    private String callbackMode;
    private String endpointUrl;
    private McpTransportProtocol mcpTransportProtocol;

    @NotNull
    private AgentStatus status;
}
