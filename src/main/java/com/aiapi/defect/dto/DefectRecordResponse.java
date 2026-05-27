package com.aiapi.defect.dto;

import com.aiapi.common.enums.DefectPlatformType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefectRecordResponse {
    Long id;
    String syncCode;
    String projectCode;
    String accountCode;
    DefectPlatformType platformType;
    String externalDefectId;
    String externalDefectKey;
    String title;
    String severity;
    String defectStatus;
    String defectType;
    String assignedTo;
    String reporterName;
    LocalDateTime openedAt;
    LocalDateTime updatedAtRemote;
    Boolean hasImageFlag;
    String tags;
    String summary;
    String descriptionText;
    String rawPayload;
    List<DefectCommentResponse> comments;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
