package com.aiapi.defect.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefectAttachmentResponse {
    String externalAttachmentId;
    String fileId;
    String fileName;
    String suffix;
    Long size;
    String url;
    String creatorName;
    LocalDateTime createdAt;
}
