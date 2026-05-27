package com.aiapi.defect.integration;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefectPlatformComment {
    String externalCommentId;
    String authorName;
    String commentContent;
    LocalDateTime commentedAt;
}
