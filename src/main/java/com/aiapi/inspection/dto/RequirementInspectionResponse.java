package com.aiapi.inspection.dto;

import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.enums.LinkType;
import com.aiapi.common.enums.RequirementStatus;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RequirementInspectionResponse {
    String requirementNo;
    String title;
    RequirementStatus requirementStatus;
    List<LinkType> existingLinks;
    List<String> missingItems;
    List<LinkIssue> incompleteLinks;
    List<LinkIssue> blockedLinks;
    List<NotifyAgent> suggestedAgents;

    @Value
    @Builder
    public static class LinkIssue {
        Long linkId;
        LinkType linkType;
        String agentCode;
        LinkStatus status;
        String summary;
    }

    @Value
    @Builder
    public static class NotifyAgent {
        String agentCode;
        String reason;
    }
}
