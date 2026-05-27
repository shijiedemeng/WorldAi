package com.aiapi.session.dto;

import com.aiapi.common.enums.AgentSessionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAgentSessionStatusRequest {

    @NotNull
    private AgentSessionStatus status;

    private Boolean reusableFlag;
}
