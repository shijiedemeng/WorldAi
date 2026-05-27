package com.aiapi.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DispatchClientCommandRequest {

    @NotBlank
    private String clientCode;

    @NotBlank
    private String agentCode;

    private String sessionId;
    private String sessionCode;

    @NotBlank
    private String requirementNo;

    private Long linkId;

    private String title;
    private String prompt;
}
