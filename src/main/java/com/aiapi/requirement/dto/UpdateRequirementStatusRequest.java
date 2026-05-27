package com.aiapi.requirement.dto;

import com.aiapi.common.enums.RequirementStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRequirementStatusRequest {

    @NotNull
    private RequirementStatus status;
}
