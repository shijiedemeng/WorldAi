package com.aiapi.defect.dto;

import com.aiapi.common.enums.RequirementExecutionMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PushDefectToRequirementRequest {

    private String requirementNo;

    private String title;

    private String mainAgentCode;
    private String executionSteps;

    @NotNull
    private RequirementExecutionMode executionMode;
}
