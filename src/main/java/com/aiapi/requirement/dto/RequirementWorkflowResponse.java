package com.aiapi.requirement.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RequirementWorkflowResponse {
    String rootRequirementNo;
    List<RequirementResponse> nodes;
    List<RequirementWorkflowEdgeResponse> edges;
    List<String> startRequirementNos;
    List<String> readyRequirementNos;
}
