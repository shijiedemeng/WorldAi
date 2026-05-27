package com.aiapi.session.dto;

import com.aiapi.common.enums.AgentRuntimeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAgentSessionRequest {

    @NotBlank
    private String sessionCode;

    private String sessionName;

    @NotNull
    private AgentRuntimeType sessionType;

    @NotBlank
    private String projectCode;

    private String requirementNo;
    private String rootRequirementNo;

    @NotBlank
    private String agentCode;

    @NotBlank
    private String clientCode;

    private String externalSessionId;
    private Boolean reusableFlag;
}
