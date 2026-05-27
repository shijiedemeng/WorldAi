package com.aiapi.defect.integration;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefectPlatformBug {
    String externalDefectId;
    String externalDefectKey;
    String title;
    String severity;
    String defectStatusId;
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
    List<DefectPlatformComment> comments;
    List<DefectPlatformAttachment> attachments;
}
