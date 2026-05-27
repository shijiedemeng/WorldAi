package com.aiapi.project.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.ProjectDocumentUsage;
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
@Table(name = "project_markdown_document_link")
public class ProjectMarkdownDocumentLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 64)
    private ProjectDocumentUsage usageType;

    @Column(name = "document_id", nullable = false, length = 128)
    private String documentId;

    @Column(name = "sort_no", nullable = false)
    private Integer sortNo;
}
