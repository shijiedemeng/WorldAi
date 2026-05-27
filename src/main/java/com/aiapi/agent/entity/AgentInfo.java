package com.aiapi.agent.entity;

import com.aiapi.common.entity.BaseEntity;
import com.aiapi.common.enums.AgentEngineType;
import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.AgentStatus;
import com.aiapi.common.enums.McpTransportProtocol;
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
@Table(name = "agent_info")
public class AgentInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_code", nullable = false, unique = true, length = 64)
    private String agentCode;

    @Column(name = "agent_name", nullable = false, length = 128)
    private String agentName;

    @Column(name = "project_code", length = 64)
    private String projectCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_engine_type", nullable = false, length = 32)
    private AgentEngineType agentEngineType;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_role", nullable = false, length = 32)
    private AgentRole agentRole;

    @Column(name = "agent_desc")
    private String agentDesc;

    @Column(name = "capability_tags")
    private String capabilityTags;

    @Column(name = "supported_link_types")
    private String supportedLinkTypes;

    @Column(name = "skill_codes")
    private String skillCodes;

    @Column(name = "callback_mode", length = 32)
    private String callbackMode;

    @Column(name = "endpoint_url", length = 512)
    private String endpointUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "mcp_transport_protocol", nullable = false, length = 32)
    private McpTransportProtocol mcpTransportProtocol;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentStatus status;

    @Column(name = "last_heartbeat_time")
    private LocalDateTime lastHeartbeatTime;
}
