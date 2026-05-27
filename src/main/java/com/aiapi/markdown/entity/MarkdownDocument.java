package com.aiapi.markdown.entity;

import com.aiapi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "markdown_document")
public class MarkdownDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, unique = true, length = 128)
    private String documentId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "node_type", nullable = false, length = 32)
    private String nodeType;

    @Column(name = "document_type", nullable = false, length = 64)
    private String documentType;

    @Column(name = "document_status", nullable = false, length = 64)
    private String documentStatus;

    @Column(name = "parent_id", length = 128)
    private String parentId;

    @Column(name = "refs", columnDefinition = "TEXT")
    private String refs;

    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;
}
