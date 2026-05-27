package com.aiapi.project.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.ProjectMarkdownBaseKey;
import com.aiapi.common.enums.ProjectMarkdownFileType;
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
@Table(name = "project_markdown_file")
public class ProjectMarkdownFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_role", length = 32)
    private AgentRole agentRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 16)
    private ProjectMarkdownFileType fileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_key", length = 32)
    private ProjectMarkdownBaseKey baseKey;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "last_sync_source", length = 32)
    private String lastSyncSource;
}
