package com.aiapi.system.dto;

import com.aiapi.common.enums.AiModelPurpose;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AiModelSettingResponse {
    Long id;
    String settingKey;
    String providerName;
    String baseUrl;
    String apiKey;
    String modelName;
    AiModelPurpose modelPurpose;
    Integer vectorChunkSize;
    Integer vectorChunkOverlap;
    Boolean supportImageFlag;
    String promptTemplate;
    Boolean enabledFlag;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
