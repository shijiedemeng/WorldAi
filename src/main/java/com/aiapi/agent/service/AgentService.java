package com.aiapi.agent.service;

import com.aiapi.agent.dto.AgentResponse;
import com.aiapi.agent.dto.CreateAgentRequest;
import com.aiapi.agent.dto.UpdateAgentRequest;
import com.aiapi.agent.entity.AgentInfo;
import com.aiapi.agent.repository.AgentInfoRepository;
import com.aiapi.common.enums.AgentEngineType;
import com.aiapi.common.enums.AgentStatus;
import com.aiapi.common.enums.McpTransportProtocol;
import com.aiapi.common.exception.BizException;
import com.aiapi.link.repository.RequirementDevLinkRepository;
import com.aiapi.project.service.ProjectService;
import com.aiapi.project.repository.ProjectInfoRepository;
import com.aiapi.requirement.repository.RequirementInfoRepository;
import com.aiapi.requirement.repository.RequirementModuleInfoRepository;
import com.aiapi.skill.repository.SkillInfoRepository;
import com.aiapi.session.repository.AgentSessionRepository;
import com.aiapi.testrecord.repository.RequirementTestRecordRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentInfoRepository agentInfoRepository;
    private final ProjectService projectService;
    private final ProjectInfoRepository projectInfoRepository;
    private final RequirementInfoRepository requirementInfoRepository;
    private final RequirementModuleInfoRepository requirementModuleInfoRepository;
    private final RequirementDevLinkRepository requirementDevLinkRepository;
    private final RequirementTestRecordRepository requirementTestRecordRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final SkillInfoRepository skillInfoRepository;

    @Transactional
    public AgentResponse create(CreateAgentRequest request) {
        agentInfoRepository.findByAgentCode(request.getAgentCode()).ifPresent(agent -> {
            throw new BizException(400, "agentCode already exists");
        });
        projectService.findEntity(request.getProjectCode());
        AgentInfo entity = new AgentInfo();
        entity.setAgentCode(request.getAgentCode());
        entity.setAgentName(request.getAgentName());
        entity.setProjectCode(request.getProjectCode());
        entity.setAgentEngineType(defaultEngineType(request.getAgentEngineType()));
        entity.setAgentRole(request.getAgentRole());
        entity.setAgentDesc(request.getAgentDesc());
        entity.setCapabilityTags(request.getCapabilityTags());
        entity.setSupportedLinkTypes(request.getSupportedLinkTypes());
        entity.setSkillCodes(joinSkillCodes(request.getSkillCodes()));
        entity.setCallbackMode(request.getCallbackMode());
        entity.setEndpointUrl(request.getEndpointUrl());
        entity.setMcpTransportProtocol(defaultMcpTransportProtocol(request.getMcpTransportProtocol()));
        entity.setStatus(request.getStatus());
        return toResponse(agentInfoRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public AgentResponse get(String agentCode) {
        return toResponse(findEntity(agentCode));
    }

    @Transactional
    public AgentResponse update(String agentCode, UpdateAgentRequest request) {
        AgentInfo entity = findEntity(agentCode);
        projectService.findEntity(request.getProjectCode());
        if (!Objects.equals(entity.getProjectCode(), request.getProjectCode())) {
            ensureAgentProjectCanChange(agentCode);
        }
        entity.setAgentName(request.getAgentName());
        entity.setProjectCode(request.getProjectCode());
        entity.setAgentEngineType(defaultEngineType(request.getAgentEngineType()));
        entity.setAgentRole(request.getAgentRole());
        entity.setAgentDesc(request.getAgentDesc());
        entity.setCapabilityTags(request.getCapabilityTags());
        entity.setSupportedLinkTypes(request.getSupportedLinkTypes());
        entity.setSkillCodes(joinSkillCodes(request.getSkillCodes()));
        entity.setCallbackMode(request.getCallbackMode());
        entity.setEndpointUrl(request.getEndpointUrl());
        entity.setMcpTransportProtocol(defaultMcpTransportProtocol(request.getMcpTransportProtocol()));
        entity.setStatus(request.getStatus());
        return toResponse(agentInfoRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> list(String projectCode) {
        List<AgentInfo> entities = projectCode == null || projectCode.isBlank()
                ? agentInfoRepository.findAll()
                : agentInfoRepository.findByProjectCode(projectCode);
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(String agentCode) {
        AgentInfo entity = findEntity(agentCode);
        ensureAgentDeletable(agentCode);
        agentInfoRepository.delete(entity);
    }

    @Transactional
    public AgentResponse heartbeat(String agentCode) {
        AgentInfo entity = findEntity(agentCode);
        entity.setLastHeartbeatTime(LocalDateTime.now());
        if (entity.getStatus() == AgentStatus.OFFLINE) {
            entity.setStatus(AgentStatus.ONLINE);
        }
        return toResponse(agentInfoRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public AgentInfo findEntity(String agentCode) {
        return agentInfoRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new BizException(404, "agent not found"));
    }

    @Transactional(readOnly = true)
    public List<AgentInfo> findOnlineAgents() {
        return agentInfoRepository.findByStatus(AgentStatus.ONLINE);
    }

    @Transactional(readOnly = true)
    public List<String> listSkillCodes(String agentCode) {
        AgentInfo entity = findEntity(agentCode);
        return splitSkillCodes(entity.getSkillCodes());
    }

    private void ensureAgentProjectCanChange(String agentCode) {
        if (hasAgentReferences(agentCode)) {
            throw new BizException(400, "agent is referenced, cannot change project");
        }
    }

    private void ensureAgentDeletable(String agentCode) {
        if (projectInfoRepository.countByOwnerAgentCode(agentCode) > 0) {
            throw new BizException(400, "agent is project owner, cannot delete");
        }
        if (requirementInfoRepository.countByMainAgentCode(agentCode) > 0
                || requirementModuleInfoRepository.countByMainAgentCode(agentCode) > 0) {
            throw new BizException(400, "agent is referenced by requirements, cannot delete");
        }
        if (requirementDevLinkRepository.countByAgentCode(agentCode) > 0) {
            throw new BizException(400, "agent is referenced by links, cannot delete");
        }
        if (requirementTestRecordRepository.countByTesterAgentCode(agentCode) > 0) {
            throw new BizException(400, "agent is referenced by test records, cannot delete");
        }
        if (agentSessionRepository.countByAgentCode(agentCode) > 0) {
            throw new BizException(400, "agent has sessions, cannot delete");
        }
    }

    private boolean hasAgentReferences(String agentCode) {
        return projectInfoRepository.countByOwnerAgentCode(agentCode) > 0
                || requirementInfoRepository.countByMainAgentCode(agentCode) > 0
                || requirementModuleInfoRepository.countByMainAgentCode(agentCode) > 0
                || requirementDevLinkRepository.countByAgentCode(agentCode) > 0
                || requirementTestRecordRepository.countByTesterAgentCode(agentCode) > 0
                || agentSessionRepository.countByAgentCode(agentCode) > 0;
    }

    private AgentResponse toResponse(AgentInfo entity) {
        return AgentResponse.builder()
                .id(entity.getId())
                .agentCode(entity.getAgentCode())
                .agentName(entity.getAgentName())
                .projectCode(entity.getProjectCode())
                .agentEngineType(defaultEngineType(entity.getAgentEngineType()))
                .agentRole(entity.getAgentRole())
                .agentDesc(entity.getAgentDesc())
                .capabilityTags(entity.getCapabilityTags())
                .supportedLinkTypes(entity.getSupportedLinkTypes())
                .skillCodes(splitSkillCodes(entity.getSkillCodes()))
                .callbackMode(entity.getCallbackMode())
                .endpointUrl(entity.getEndpointUrl())
                .mcpTransportProtocol(defaultMcpTransportProtocol(entity.getMcpTransportProtocol()))
                .status(entity.getStatus())
                .lastHeartbeatTime(entity.getLastHeartbeatTime())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String joinSkillCodes(List<String> skillCodes) {
        List<String> normalized = normalizeSkillCodes(skillCodes);
        if (normalized.isEmpty()) {
            return null;
        }
        validateSkillCodesExist(normalized);
        return String.join(",", normalized);
    }

    private AgentEngineType defaultEngineType(AgentEngineType agentEngineType) {
        return agentEngineType == null ? AgentEngineType.QODER : agentEngineType;
    }

    private McpTransportProtocol defaultMcpTransportProtocol(McpTransportProtocol protocol) {
        return protocol == null ? McpTransportProtocol.SSE : protocol;
    }

    private List<String> normalizeSkillCodes(List<String> skillCodes) {
        if (skillCodes == null || skillCodes.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String skillCode : skillCodes) {
            String trimmed = trimToNull(skillCode);
            if (trimmed != null && !normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private void validateSkillCodesExist(List<String> skillCodes) {
        if (skillCodes.isEmpty()) {
            return;
        }
        long count = skillInfoRepository.countBySkillCodeIn(skillCodes);
        if (count != skillCodes.size()) {
            throw new BizException(400, "some skillCodes do not exist");
        }
    }

    private List<String> splitSkillCodes(String skillCodes) {
        if (skillCodes == null || skillCodes.isBlank()) {
            return List.of();
        }
        return List.of(skillCodes.split(",")).stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
