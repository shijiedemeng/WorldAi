package com.aiapi.session.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.AgentRuntimeType;
import com.aiapi.common.enums.AgentSessionStatus;
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
@Table(name = "agent_session")
public class AgentSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, unique = true, length = 64)
    private String sessionCode;

    @Column(name = "session_name", length = 128)
    private String sessionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 32)
    private AgentRuntimeType sessionType;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Column(name = "requirement_no", length = 64)
    private String requirementNo;

    @Column(name = "root_requirement_no", length = 64)
    private String rootRequirementNo;

    @Column(name = "agent_code", nullable = false, length = 64)
    private String agentCode;

    @Column(name = "client_code", nullable = false, length = 64)
    private String clientCode;

    @Column(name = "external_session_id", length = 128)
    private String externalSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentSessionStatus status;

    @Column(name = "reusable_flag", nullable = false)
    private Boolean reusableFlag;

    @Column(name = "last_active_time")
    private LocalDateTime lastActiveTime;
}
