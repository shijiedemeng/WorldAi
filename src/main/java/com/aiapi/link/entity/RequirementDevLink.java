package com.aiapi.link.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.enums.LinkType;
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
@Table(name = "requirement_dev_link")
public class RequirementDevLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_no", nullable = false, length = 64)
    private String requirementNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 32)
    private LinkType linkType;

    @Column(name = "task_title", nullable = false, length = 256)
    private String taskTitle;

    @Column(name = "task_desc")
    private String taskDesc;

    @Column(name = "agent_code", nullable = false, length = 64)
    private String agentCode;

    @Column(name = "developer_name", length = 128)
    private String developerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private LinkStatus status;

    @Column(name = "result_summary")
    private String resultSummary;

    @Column(name = "execution_details", columnDefinition = "TEXT")
    private String executionDetails;

    @Column(name = "deliverable_path", length = 512)
    private String deliverablePath;

    @Column(name = "depends_on_link_id")
    private Long dependsOnLinkId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
