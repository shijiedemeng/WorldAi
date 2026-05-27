package com.aiapi.system.dto;

import com.aiapi.common.enums.AiModelPurpose;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveAiModelSettingRequest {

    @NotBlank
    private String settingKey;

    @NotBlank
    private String providerName;

    private String baseUrl;
    private String apiKey;

    @NotBlank
    private String modelName;

    private AiModelPurpose modelPurpose;

    private Integer vectorChunkSize;

    private Integer vectorChunkOverlap;

    private Boolean supportImageFlag;

    private String promptTemplate;

    private Boolean enabledFlag;
}
