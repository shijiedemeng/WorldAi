package com.aiapi.requirement.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.RequirementExecutionMode;
import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.common.enums.RequirementType;
import com.aiapi.common.enums.SessionStrategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "requirement_info")
public class RequirementInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_no", nullable = false, unique = true, length = 64)
    private String requirementNo;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "requirement_desc")
    private String requirementDesc;

    @Column(name = "priority", length = 32)
    private String priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RequirementStatus status;

    @Column(name = "source", length = 128)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "requirement_type", nullable = false, length = 16)
    private RequirementType requirementType;

    @Column(name = "parent_requirement_no", length = 64)
    private String parentRequirementNo;

    @Column(name = "root_requirement_no", nullable = false, length = 64)
    private String rootRequirementNo;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;

    @Column(name = "main_agent_code", length = 64)
    private String mainAgentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_strategy", length = 32)
    private SessionStrategy sessionStrategy;

    @Column(name = "preferred_session_code", length = 64)
    private String preferredSessionCode;

    @Column(name = "current_stage", length = 64)
    private String currentStage;

    @Column(name = "expected_deadline")
    private LocalDateTime expectedDeadline;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "execution_steps", columnDefinition = "TEXT")
    private String executionSteps;

    @Column(name = "auto_execute_flag", nullable = false)
    private Boolean autoExecuteFlag;

    @Column(name = "file_search_mcp_agent_codes", length = 512)
    private String fileSearchMcpAgentCodes;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 32)
    private RequirementExecutionMode executionMode;

    @Column(name = "result_extractable_flag", nullable = false)
    private Boolean resultExtractableFlag;

    @Column(name = "review_required_flag", nullable = false)
    private Boolean reviewRequiredFlag;

    @Column(name = "review_approved_flag", nullable = false)
    private Boolean reviewApprovedFlag;
}
