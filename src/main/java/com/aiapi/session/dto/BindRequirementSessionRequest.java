package com.aiapi.session.dto;

import com.aiapi.common.enums.SessionBindingType;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BindRequirementSessionRequest {

    @NotBlank
    private String sessionCode;

    private SessionBindingType bindingType;
    private Boolean primaryFlag;
    private String remark;
}
