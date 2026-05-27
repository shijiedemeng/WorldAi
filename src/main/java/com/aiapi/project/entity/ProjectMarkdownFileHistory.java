package com.aiapi.project.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.ProjectMarkdownBaseKey;
import com.aiapi.common.enums.ProjectMarkdownChangeSource;
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
@Table(name = "project_markdown_file_history")
public class ProjectMarkdownFileHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_role", length = 32)
    private AgentRole agentRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_key", nullable = false, length = 32)
    private ProjectMarkdownBaseKey baseKey;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_source", nullable = false, length = 32)
    private ProjectMarkdownChangeSource changeSource;

    @Column(name = "change_desc", length = 512)
    private String changeDesc;
}
