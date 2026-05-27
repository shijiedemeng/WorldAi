package com.aiapi.session.dto;

import com.aiapi.common.enums.SessionBindingType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RequirementSessionBindingResponse {
    Long id;
    String requirementNo;
    String rootRequirementNo;
    String sessionCode;
    String sessionName;
    String agentCode;
    String clientCode;
    SessionBindingType bindingType;
    Boolean primaryFlag;
    String remark;
    LocalDateTime sessionLastActiveTime;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
