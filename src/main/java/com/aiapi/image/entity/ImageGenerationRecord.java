package com.aiapi.image.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.ImageGenerationStatus;
import com.aiapi.common.enums.ImageGenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "image_generation_record")
public class ImageGenerationRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_type", nullable = false, length = 32)
    private ImageGenerationType generationType;

    @Column(name = "ai_setting_key", nullable = false, length = 64)
    private String aiSettingKey;

    @Column(name = "provider_name", length = 64)
    private String providerName;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "prompt_template_codes", columnDefinition = "TEXT")
    private String promptTemplateCodes;

    @Column(name = "negative_template_codes", columnDefinition = "TEXT")
    private String negativeTemplateCodes;

    @Column(name = "positive_prompt_text", columnDefinition = "LONGTEXT")
    private String positivePromptText;

    @Column(name = "negative_prompt_text", columnDefinition = "LONGTEXT")
    private String negativePromptText;

    @Column(name = "final_prompt", nullable = false, columnDefinition = "LONGTEXT")
    private String finalPrompt;

    @Column(name = "image_size", length = 32)
    private String imageSize;

    @Column(name = "quality", length = 32)
    private String quality;

    @Column(name = "output_format", length = 32)
    private String outputFormat;

    @Column(name = "background", length = 32)
    private String background;

    @Column(name = "moderation", length = 32)
    private String moderation;

    @Column(name = "response_format", length = 32)
    private String responseFormat;

    @Column(name = "input_fidelity", length = 32)
    private String inputFidelity;

    @Column(name = "user_text", length = 128)
    private String userText;

    @Column(name = "image_count", nullable = false)
    private Integer imageCount;

    @Column(name = "source_image_file_name", length = 255)
    private String sourceImageFileName;

    @Column(name = "source_image_path", length = 512)
    private String sourceImagePath;

    @Column(name = "source_image_content_type", length = 128)
    private String sourceImageContentType;

    @Column(name = "source_image_size")
    private Long sourceImageSize;

    @Column(name = "mask_image_file_name", length = 255)
    private String maskImageFileName;

    @Column(name = "mask_image_path", length = 512)
    private String maskImagePath;

    @Column(name = "mask_image_content_type", length = 128)
    private String maskImageContentType;

    @Column(name = "mask_image_size")
    private Long maskImageSize;

    @Column(name = "result_file_name", length = 255)
    private String resultFileName;

    @Column(name = "result_file_path", length = 512)
    private String resultFilePath;

    @Column(name = "result_file_content_type", length = 128)
    private String resultFileContentType;

    @Column(name = "result_file_size")
    private Long resultFileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImageGenerationStatus status;

    @Column(name = "request_payload", columnDefinition = "LONGTEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "LONGTEXT")
    private String responsePayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
