package com.aiapi.project.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.ProjectMarkdownBaseSyncMode;
import com.aiapi.common.enums.ProjectMarkdownSyncMode;
import com.aiapi.common.enums.ProjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "project_info")
public class ProjectInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_code", nullable = false, unique = true, length = 64)
    private String projectCode;

    @Column(name = "project_name", nullable = false, length = 128)
    private String projectName;

    @Column(name = "project_desc")
    private String projectDesc;

    @Column(name = "business_goal")
    private String businessGoal;

    @Column(name = "tech_stack")
    private String techStack;

    @Column(name = "repository_url", length = 512)
    private String repositoryUrl;

    @Column(name = "owner_agent_code", length = 64)
    private String ownerAgentCode;

    @Column(name = "file_search_mcp_agent_codes", length = 512)
    private String fileSearchMcpAgentCodes;

    @Enumerated(EnumType.STRING)
    @Column(name = "markdown_sync_mode", nullable = false, length = 32)
    private ProjectMarkdownSyncMode markdownSyncMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_markdown_sync_mode", nullable = false, length = 32)
    private ProjectMarkdownBaseSyncMode baseMarkdownSyncMode;

    @Column(name = "base_markdown_allow_client_upload", nullable = false)
    private Boolean baseMarkdownAllowClientUpload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProjectStatus status;
}
