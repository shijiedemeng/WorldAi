package com.aiapi.system.entity;

import com.aiapi.common.enums.AiModelPurpose;
import com.aiapi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_model_setting")
public class AiModelSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 64)
    private String settingKey;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    @Column(name = "api_key", length = 512)
    private String apiKey;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_purpose", nullable = false, length = 32)
    private AiModelPurpose modelPurpose;

    @Column(name = "vector_chunk_size", nullable = false)
    private Integer vectorChunkSize;

    @Column(name = "vector_chunk_overlap", nullable = false)
    private Integer vectorChunkOverlap;

    @Column(name = "support_image_flag", nullable = false)
    private Boolean supportImageFlag;

    @Column(name = "prompt_template", columnDefinition = "TEXT")
    private String promptTemplate;

    @Column(name = "enabled_flag", nullable = false)
    private Boolean enabledFlag;
}
