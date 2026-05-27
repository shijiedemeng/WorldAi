package com.aiapi.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientControllableAgentRequest {

    @NotBlank
    private String agentCode;

    private Boolean enabledFlag;
    private String workspaceDir;
    private String workerCommand;
    private String skillsDir;
}
