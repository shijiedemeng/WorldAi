package com.aiapi.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FetchYunxiaoProjectsRequest {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String accessToken;

    @NotBlank
    private String organizationId;
}
