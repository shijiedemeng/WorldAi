package com.aiapi.requirement.dto;

import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.enums.RequirementStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RequirementWorkflowResultResponse {
    String rootRequirementNo;
    String requirementNo;
    List<Item> items;

    @Value
    @Builder
    public static class Item {
        String requirementNo;
        String title;
        String agentCode;
        RequirementStatus requirementStatus;
        Long linkId;
        LinkStatus linkStatus;
        String resultSummary;
        String executionDetails;
        String deliverablePath;
        LocalDateTime finishedAt;
    }
}
