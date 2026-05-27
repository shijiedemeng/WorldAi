package com.aiapi.log.repository;

import java.time.LocalDateTime;

public interface AiAnalysisLogListProjection {
    Long getId();

    String getSourceType();

    String getBusinessNo();

    String getProjectCode();

    String getAiSettingKey();

    String getModelName();

    String getStatus();

    String getErrorMessage();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
