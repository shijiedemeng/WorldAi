package com.aiapi.defect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalyzeDefectRequest {

    @NotBlank
    private String analysisNo;

    @NotBlank
    private String projectCode;

    private Long defectRecordId;

    private String sourceCode;
    private String externalDefectId;
    private String externalDefectKey;
    private String title;
    private String severity;
    private String defectStatus;
    private String defectType;
    private String assignedTo;
    private String reporterName;
    private String summary;
    private String descriptionText;

    private String aiSettingKey;
    private String agentScope;
    private String promptText;
    private String editedSummary;
}
