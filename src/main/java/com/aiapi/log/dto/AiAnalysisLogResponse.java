package com.aiapi.log.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AiAnalysisLogResponse {
    Long id;
    String sourceType;
    String businessNo;
    String projectCode;
    String aiSettingKey;
    String modelName;
    String status;
    String requestPayload;
    String responsePayload;
    String errorMessage;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
