package com.aiapi.defect.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FetchYunxiaoOrganizationsRequest {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String accessToken;

    private String userId;
}
