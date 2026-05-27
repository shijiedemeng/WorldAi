package com.aiapi.image.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateImageEditRequest {

    @NotBlank
    private String aiSettingKey;
    private List<String> promptTemplateCodes;
    private List<String> negativeTemplateCodes;
    private List<String> positivePromptLines;
    private List<String> negativePromptLines;
    private String size;
    private String quality;
    private String outputFormat;
    private String background;
    private String moderation;
    private String responseFormat;
    private String inputFidelity;
    private Integer n;
    private String user;
}
