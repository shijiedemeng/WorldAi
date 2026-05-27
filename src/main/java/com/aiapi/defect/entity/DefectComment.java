package com.aiapi.defect.entity;

import com.aiapi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "defect_comment")
public class DefectComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "defect_record_id", nullable = false)
    private Long defectRecordId;

    @Column(name = "external_comment_id", length = 128)
    private String externalCommentId;

    @Column(name = "author_name", length = 128)
    private String authorName;

    @Column(name = "comment_content", columnDefinition = "TEXT")
    private String commentContent;

    @Column(name = "commented_at")
    private LocalDateTime commentedAt;
}
