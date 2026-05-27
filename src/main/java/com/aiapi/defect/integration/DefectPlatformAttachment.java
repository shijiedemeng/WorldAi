package com.aiapi.defect.integration;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefectPlatformAttachment {
    String externalAttachmentId;
    String fileId;
    String fileName;
    String suffix;
    Long size;
    String url;
    String creatorName;
    LocalDateTime createdAt;
}
