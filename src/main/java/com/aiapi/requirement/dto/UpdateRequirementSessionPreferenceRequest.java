package com.aiapi.requirement.dto;

import com.aiapi.common.enums.SessionStrategy;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateRequirementSessionPreferenceRequest {
    private SessionStrategy sessionStrategy;
    private String preferredSessionCode;
}
