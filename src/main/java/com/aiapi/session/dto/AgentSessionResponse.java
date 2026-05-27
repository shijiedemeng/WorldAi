package com.aiapi.session.dto;

import com.aiapi.common.enums.AgentRuntimeType;
import com.aiapi.common.enums.AgentSessionStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AgentSessionResponse {
    Long id;
    String sessionCode;
    String sessionName;
    AgentRuntimeType sessionType;
    String projectCode;
    String requirementNo;
    String rootRequirementNo;
    String agentCode;
    String clientCode;
    String externalSessionId;
    AgentSessionStatus status;
    Boolean reusableFlag;
    LocalDateTime lastActiveTime;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
