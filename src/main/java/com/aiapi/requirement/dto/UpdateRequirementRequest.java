package com.aiapi.requirement.dto;

import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.common.enums.RequirementExecutionMode;
import com.aiapi.common.enums.SessionStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UpdateRequirementRequest {

    @NotBlank
    private String title;

    private String requirementDesc;
    private String priority;

    @NotNull
    private RequirementStatus status;

    private String source;
    private Integer sortNo;
    private String mainAgentCode;
    private SessionStrategy sessionStrategy;
    private String preferredSessionCode;
    private String currentStage;
    private LocalDateTime expectedDeadline;
    private String createdBy;
    private String executionSteps;
    private Boolean autoExecuteFlag;
    private String fileSearchMcpAgentCodes;
    private Boolean mcpFileSearchEnabledFlag;
    private String documentIds;
    private Boolean projectKnowledgeSearchEnabledFlag;
    private Integer projectKnowledgeSearchLimit;
    private Double projectKnowledgeSearchMinScore;
    private RequirementExecutionMode executionMode;
    private Boolean resultExtractableFlag;
    private Boolean reviewRequiredFlag;
}
