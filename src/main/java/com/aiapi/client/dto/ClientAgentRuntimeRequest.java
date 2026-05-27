package com.aiapi.client.dto;

import com.aiapi.common.enums.AgentRuntimeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientAgentRuntimeRequest {

    @NotNull
    private AgentRuntimeType agentType;

    @NotBlank
    private String agentName;

    private String agentVersion;
    private String commandPath;
    private Boolean availableFlag;
    private String capabilityTags;
    private Boolean supportsSessionReuse;
    private String defaultWorkspaceDir;
}
