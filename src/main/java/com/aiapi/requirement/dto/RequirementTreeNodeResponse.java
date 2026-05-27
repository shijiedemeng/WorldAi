package com.aiapi.requirement.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RequirementTreeNodeResponse {
    RequirementResponse requirement;
    List<RequirementTreeNodeResponse> children;
}
