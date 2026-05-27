package com.aiapi.image.dto;

import com.aiapi.common.enums.ImageGenerationStatus;
import com.aiapi.common.enums.ImageGenerationType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ImageGenerationRecordResponse {
    Long id;
    ImageGenerationType generationType;
    String aiSettingKey;
    String providerName;
    String modelName;
    String promptTemplateCodes;
    String negativeTemplateCodes;
    String positivePromptText;
    String negativePromptText;
    String finalPrompt;
    String imageSize;
    String quality;
    String outputFormat;
    String background;
    String moderation;
    String responseFormat;
    String inputFidelity;
    String userText;
    Integer imageCount;
    String sourceImageFileName;
    String sourceImagePath;
    String sourceImageContentType;
    Long sourceImageSize;
    String sourceImageUrl;
    String maskImageFileName;
    String maskImagePath;
    String maskImageContentType;
    Long maskImageSize;
    String maskImageUrl;
    String resultFileName;
    String resultFilePath;
    String resultFileContentType;
    Long resultFileSize;
    String resultFileUrl;
    ImageGenerationStatus status;
    String requestPayload;
    String responsePayload;
    String errorMessage;
    LocalDateTime startedAt;
    LocalDateTime finishedAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
