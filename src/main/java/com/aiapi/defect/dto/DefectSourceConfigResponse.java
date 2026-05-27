package com.aiapi.defect.dto;

import com.aiapi.common.enums.DefectPlatformType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DefectSourceConfigResponse {
    Long id;
    String sourceCode;
    String sourceName;
    String projectCode;
    DefectPlatformType platformType;
    String baseUrl;
    String username;
    String passwordValue;
    String accessToken;
    Boolean enabledFlag;
    String extraConfig;
    String externalProjectKey;
    String externalProjectName;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
