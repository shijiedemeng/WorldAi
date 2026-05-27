package com.aiapi.defect.dto;

import com.aiapi.common.enums.DefectPlatformType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RemoteDefectResponse {
    String sourceCode;
    String projectCode;
    String accountCode;
    DefectPlatformType platformType;
    Long persistedRecordId;
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
    List<DefectCommentResponse> comments;
    List<DefectAttachmentResponse> attachments;
}
