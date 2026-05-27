package com.aiapi.requirement.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalyzeRequirementRequest {
    private String aiSettingKey;
    private String promptText;
}
