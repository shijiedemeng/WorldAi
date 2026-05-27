package com.aiapi.skill.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SkillResponse {
    Long id;
    String skillCode;
    String skillName;
    String skillDesc;
    String contentText;
    String archiveFileName;
    String archiveContentType;
    Long archiveSize;
    Boolean hasArchive;
    Boolean enabledFlag;
    LocalDateTime lastArchiveUploadTime;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
