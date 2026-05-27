package com.aiapi.requirement.dto;

import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.common.enums.RequirementType;
import com.aiapi.common.enums.RequirementExecutionMode;
import com.aiapi.common.enums.SessionStrategy;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RequirementResponse {
    Long id;
    String requirementNo;
    String projectCode;
    String title;
    String requirementDesc;
    String priority;
    RequirementStatus status;
    String source;
    RequirementType requirementType;
    String parentRequirementNo;
    String rootRequirementNo;
    Integer sortNo;
    String mainAgentCode;
    SessionStrategy sessionStrategy;
    String preferredSessionCode;
    String currentStage;
    LocalDateTime expectedDeadline;
    String createdBy;
    String executionSteps;
    Boolean autoExecuteFlag;
    String fileSearchMcpAgentCodes;
    Boolean mcpFileSearchEnabledFlag;
    String documentIds;
    Boolean projectKnowledgeSearchEnabledFlag;
    Integer projectKnowledgeSearchLimit;
    Double projectKnowledgeSearchMinScore;
    RequirementExecutionMode executionMode;
    Boolean resultExtractableFlag;
    Boolean reviewRequiredFlag;
    Boolean reviewApprovedFlag;
    String executionMarker;
    String executionClientCode;
    String executionCommandId;
    String executionSessionId;
    String executionAgentCode;
    Boolean executionAcpFlag;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
