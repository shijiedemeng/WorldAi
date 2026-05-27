package com.aiapi.defect.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefectCommentResponse {
    Long id;
    String externalCommentId;
    String authorName;
    String commentContent;
    LocalDateTime commentedAt;
}
