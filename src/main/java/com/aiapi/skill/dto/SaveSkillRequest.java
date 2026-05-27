package com.aiapi.skill.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveSkillRequest {

    private String skillCode;
    private String skillName;
    private String skillDesc;
    private String contentText;
    private Boolean enabledFlag;
}
