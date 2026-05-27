package com.aiapi.client.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class CompleteClientAgentSessionRequest {
    private String sessionId;
    private String status;
    private String runtimeType;
    private String errorMessage;
    private List<Object> initializedSkills;
}
