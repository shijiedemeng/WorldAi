package com.aiapi.defect.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.DefectPushStatus;
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
@Table(name = "defect_analysis_record")
public class DefectAnalysisRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_no", nullable = false, unique = true, length = 64)
    private String analysisNo;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Column(name = "defect_record_id", nullable = false)
    private Long defectRecordId;

    @Column(name = "ai_setting_key", length = 64)
    private String aiSettingKey;

    @Column(name = "agent_scope", columnDefinition = "TEXT")
    private String agentScope;

    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;

    @Column(name = "edited_summary", columnDefinition = "TEXT")
    private String editedSummary;

    @Column(name = "analysis_result", columnDefinition = "TEXT")
    private String analysisResult;

    @Column(name = "next_requirement_no", length = 64)
    private String nextRequirementNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", nullable = false, length = 32)
    private DefectPushStatus pushStatus;

    @Column(name = "pushed_at")
    private LocalDateTime pushedAt;
}
