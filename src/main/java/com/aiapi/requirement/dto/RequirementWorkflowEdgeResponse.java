package com.aiapi.requirement.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RequirementWorkflowEdgeResponse {
    Long id;
    String fromRequirementNo;
    String toRequirementNo;
}
