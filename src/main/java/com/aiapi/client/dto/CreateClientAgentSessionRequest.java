package com.aiapi.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateClientAgentSessionRequest {

    @NotBlank
    private String agentCode;

    private String sessionName;
    private String sessionType;
    private String runtimeType;
    private Boolean defaultFlag;
    private String workspaceDir;
}
