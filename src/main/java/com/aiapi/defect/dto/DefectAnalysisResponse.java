package com.aiapi.defect.dto;

import com.aiapi.common.enums.DefectPushStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefectAnalysisResponse {
    Long id;
    String analysisNo;
    String projectCode;
    Long defectRecordId;
    String sourceCode;
    String externalDefectId;
    String externalDefectKey;
    String title;
    String aiSettingKey;
    String agentScope;
    String promptText;
    String editedSummary;
    String analysisResult;
    String nextRequirementNo;
    DefectPushStatus pushStatus;
    LocalDateTime pushedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
