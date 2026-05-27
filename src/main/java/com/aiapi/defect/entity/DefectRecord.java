package com.aiapi.defect.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.DefectPlatformType;
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
@Table(name = "defect_record")
public class DefectRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_code", nullable = false, length = 64)
    private String syncCode;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Column(name = "account_code", nullable = false, length = 64)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_type", nullable = false, length = 32)
    private DefectPlatformType platformType;

    @Column(name = "external_defect_id", nullable = false, length = 128)
    private String externalDefectId;

    @Column(name = "external_defect_key", length = 128)
    private String externalDefectKey;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "defect_status", length = 32)
    private String defectStatus;

    @Column(name = "defect_type", length = 64)
    private String defectType;

    @Column(name = "assigned_to", length = 128)
    private String assignedTo;

    @Column(name = "reporter_name", length = 128)
    private String reporterName;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "updated_at_remote")
    private LocalDateTime updatedAtRemote;

    @Column(name = "has_image_flag", nullable = false)
    private Boolean hasImageFlag;

    @Column(name = "tags", length = 512)
    private String tags;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "description_text", columnDefinition = "TEXT")
    private String descriptionText;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;
}
