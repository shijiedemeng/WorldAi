package com.aiapi.client.dto;

import com.aiapi.common.enums.DispatchStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteClientCommandRequest {
    private DispatchStatus status;
    private String resultSummary;
    private String executionDetails;
    private String deliverablePath;
}
