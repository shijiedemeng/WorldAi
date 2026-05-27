package com.aiapi.knowledge.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.ProjectKnowledgeStatus;
import com.aiapi.common.enums.ProjectKnowledgeType;
import com.aiapi.common.enums.ProjectKnowledgeVectorStatus;
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
@Table(name = "project_knowledge_item")
public class ProjectKnowledgeItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_type", nullable = false, length = 32)
    private ProjectKnowledgeType knowledgeType;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "simple_desc", length = 1024)
    private String simpleDesc;

    @Column(name = "detail_content", columnDefinition = "LONGTEXT")
    private String detailContent;

    @Column(name = "organized_content", columnDefinition = "LONGTEXT")
    private String organizedContent;

    @Column(name = "confirmed_content", columnDefinition = "LONGTEXT")
    private String confirmedContent;

    @Column(name = "source_requirement_no", length = 64)
    private String sourceRequirementNo;

    @Column(name = "source_summary", columnDefinition = "LONGTEXT")
    private String sourceSummary;

    @Column(name = "ai_setting_key", length = 64)
    private String aiSettingKey;

    @Column(name = "embedding_setting_key", length = 64)
    private String embeddingSettingKey;

    @Column(name = "document_ids", length = 1024)
    private String documentIds;

    @Column(name = "selected_similar_knowledge_id")
    private Long selectedSimilarKnowledgeId;

    @Column(name = "selected_similar_score")
    private Double selectedSimilarScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProjectKnowledgeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "vector_status", nullable = false, length = 32)
    private ProjectKnowledgeVectorStatus vectorStatus;

    @Column(name = "vector_dirty_flag", nullable = false)
    private Boolean vectorDirtyFlag;

    @Column(name = "vector_collection", length = 128)
    private String vectorCollection;

    @Column(name = "vector_id", length = 128)
    private String vectorId;

    @Column(name = "vector_chunk_count", nullable = false)
    private Integer vectorChunkCount;

    @Column(name = "vector_file_path", length = 512)
    private String vectorFilePath;

    @Column(name = "vector_updated_at")
    private LocalDateTime vectorUpdatedAt;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;
}
