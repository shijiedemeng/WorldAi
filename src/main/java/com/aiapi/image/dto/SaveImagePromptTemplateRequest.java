package com.aiapi.image.dto;

import com.aiapi.common.enums.ImagePromptTemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveImagePromptTemplateRequest {

    @NotBlank
    private String templateCode;

    @NotBlank
    private String templateName;

    @NotNull
    private ImagePromptTemplateType templateType;

    @NotBlank
    private String contentText;

    private Boolean enabledFlag;

    private Integer sortOrder;
}
