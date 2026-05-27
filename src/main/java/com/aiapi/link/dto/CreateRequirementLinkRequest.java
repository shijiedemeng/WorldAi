package com.aiapi.link.dto;

import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.enums.LinkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRequirementLinkRequest {

    @NotNull
    private LinkType linkType;

    @NotBlank
    private String taskTitle;

    private String taskDesc;

    @NotBlank
    private String agentCode;

    private String developerName;

    @NotNull
    private LinkStatus status;

    private Long dependsOnLinkId;
}
