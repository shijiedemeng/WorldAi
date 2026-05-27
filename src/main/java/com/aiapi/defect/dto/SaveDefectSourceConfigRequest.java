package com.aiapi.defect.dto;

import com.aiapi.common.enums.DefectPlatformType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveDefectSourceConfigRequest {

    @NotBlank
    private String sourceCode;

    @NotBlank
    private String sourceName;

    @NotBlank
    private String projectCode;

    @NotNull
    private DefectPlatformType platformType;

    @NotBlank
    private String baseUrl;

    private String username;
    private String passwordValue;
    private String accessToken;

    @NotNull
    private Boolean enabledFlag;

    private String extraConfig;

    private String externalProjectKey;

    private String externalProjectName;
}
