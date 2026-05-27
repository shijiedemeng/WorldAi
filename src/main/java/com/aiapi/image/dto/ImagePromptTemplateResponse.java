package com.aiapi.image.dto;

import com.aiapi.common.enums.ImagePromptTemplateType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ImagePromptTemplateResponse {
    Long id;
    String templateCode;
    String templateName;
    ImagePromptTemplateType templateType;
    String contentText;
    Boolean enabledFlag;
    Integer sortOrder;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
