package com.aiapi.log.entity;

import com.aiapi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_analysis_log")
public class AiAnalysisLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "business_no", nullable = false, length = 128)
    private String businessNo;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    @Column(name = "ai_setting_key", length = 64)
    private String aiSettingKey;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "request_payload", columnDefinition = "LONGTEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "LONGTEXT")
    private String responsePayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
