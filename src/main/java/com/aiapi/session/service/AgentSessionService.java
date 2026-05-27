package com.aiapi.session.service;

import com.aiapi.agent.service.AgentService;
import com.aiapi.common.enums.AgentRuntimeType;
import com.aiapi.common.enums.AgentSessionStatus;
import com.aiapi.common.exception.BizException;
import com.aiapi.project.service.ProjectService;
import com.aiapi.requirement.entity.RequirementInfo;
import com.aiapi.requirement.service.RequirementService;
import com.aiapi.session.dto.AgentSessionResponse;
import com.aiapi.session.dto.CreateAgentSessionRequest;
import com.aiapi.session.dto.UpdateAgentSessionStatusRequest;
import com.aiapi.session.entity.AgentSession;
import com.aiapi.session.repository.AgentSessionRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private final AgentSessionRepository agentSessionRepository;
    private final ProjectService projectService;
    private final RequirementService requirementService;
    private final AgentService agentService;

    @Transactional
    public AgentSessionResponse create(CreateAgentSessionRequest request) {
        agentSessionRepository.findBySessionCode(request.getSessionCode()).ifPresent(session -> {
            throw new BizException(400, "sessionCode already exists");
        });
        projectService.findEntity(request.getProjectCode());
        agentService.findEntity(request.getAgentCode());
        RequirementInfo requirement = null;
        if (request.getRequirementNo() != null && !request.getRequirementNo().isBlank()) {
            requirement = requirementService.findEntity(request.getRequirementNo());
        }

        AgentSession entity = new AgentSession();
        entity.setSessionCode(request.getSessionCode());
        entity.setSessionName(request.getSessionName());
        entity.setSessionType(request.getSessionType());
        entity.setProjectCode(request.getProjectCode());
        entity.setRequirementNo(requirement == null ? trimToNull(request.getRequirementNo()) : requirement.getRequirementNo());
        entity.setRootRequirementNo(requirement == null ? trimToNull(request.getRootRequirementNo()) : requirement.getRootRequirementNo());
        entity.setAgentCode(request.getAgentCode());
        entity.setClientCode(request.getClientCode());
        entity.setExternalSessionId(trimToNull(request.getExternalSessionId()));
        entity.setStatus(AgentSessionStatus.IDLE);
        entity.setReusableFlag(request.getReusableFlag() == null ? Boolean.TRUE : request.getReusableFlag());
        entity.setLastActiveTime(LocalDateTime.now());
        return toResponse(agentSessionRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public AgentSessionResponse get(String sessionCode) {
        return toResponse(findEntity(sessionCode));
    }

    @Transactional(readOnly = true)
    public List<AgentSessionResponse> list(String projectCode,
                                           String agentCode,
                                           String clientCode,
                                           String rootRequirementNo,
                                           Boolean reusableFlag,
                                           AgentSessionStatus status) {
        return agentSessionRepository.findAll().stream()
                .filter(item -> matches(item.getProjectCode(), projectCode))
                .filter(item -> matches(item.getAgentCode(), agentCode))
                .filter(item -> matches(item.getClientCode(), clientCode))
                .filter(item -> matches(item.getRootRequirementNo(), rootRequirementNo))
                .filter(item -> reusableFlag == null || reusableFlag.equals(item.getReusableFlag()))
                .filter(item -> status == null || status == item.getStatus())
                .sorted(Comparator.comparing(AgentSession::getLastActiveTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AgentSessionResponse updateStatus(String sessionCode, UpdateAgentSessionStatusRequest request) {
        AgentSession entity = findEntity(sessionCode);
        entity.setStatus(request.getStatus());
        if (request.getReusableFlag() != null) {
            entity.setReusableFlag(request.getReusableFlag());
        }
        entity.setLastActiveTime(LocalDateTime.now());
        return toResponse(agentSessionRepository.save(entity));
    }

    @Transactional
    public AgentSessionResponse recordExecutedClientSession(String clientCode,
                                                           String agentCode,
                                                           String externalSessionId,
                                                           AgentRuntimeType runtimeType,
                                                           String projectCode,
                                                           String requirementNo,
                                                           String rootRequirementNo,
                                                           String sessionName,
                                                           AgentSessionStatus status) {
        String normalizedClientCode = required(clientCode, "clientCode");
        String normalizedAgentCode = required(agentCode, "agentCode");
        String normalizedExternalSessionId = required(externalSessionId, "externalSessionId");
        String normalizedProjectCode = required(projectCode, "projectCode");
        projectService.findEntity(normalizedProjectCode);
        agentService.findEntity(normalizedAgentCode);

        LocalDateTime now = LocalDateTime.now();
        String sessionCode = buildAutoSessionCode(normalizedClientCode, normalizedAgentCode, normalizedExternalSessionId);
        AgentSession entity = agentSessionRepository
                .findByClientCodeAndAgentCodeAndExternalSessionId(normalizedClientCode, normalizedAgentCode, normalizedExternalSessionId)
                .or(() -> agentSessionRepository.findBySessionCode(sessionCode))
                .orElseGet(() -> {
                    AgentSession created = new AgentSession();
                    created.setSessionCode(sessionCode);
                    created.setReusableFlag(Boolean.TRUE);
                    return created;
                });
        entity.setSessionName(defaultText(sessionName, "客户端执行会话"));
        entity.setSessionType(runtimeType == null ? AgentRuntimeType.CODEX_CLI : runtimeType);
        entity.setProjectCode(normalizedProjectCode);
        entity.setRequirementNo(trimToNull(requirementNo));
        entity.setRootRequirementNo(defaultText(rootRequirementNo, trimToNull(requirementNo)));
        entity.setAgentCode(normalizedAgentCode);
        entity.setClientCode(normalizedClientCode);
        entity.setExternalSessionId(normalizedExternalSessionId);
        entity.setStatus(status == null ? AgentSessionStatus.IDLE : status);
        entity.setReusableFlag(entity.getReusableFlag() == null ? Boolean.TRUE : entity.getReusableFlag());
        entity.setLastActiveTime(now);
        return toResponse(agentSessionRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<AgentSessionResponse> listReusable(String agentCode, String projectCode, String rootRequirementNo, String clientCode) {
        agentService.findEntity(agentCode);
        return agentSessionRepository.findByAgentCodeAndReusableFlagTrue(agentCode).stream()
                .filter(item -> item.getStatus() != AgentSessionStatus.CLOSED && item.getStatus() != AgentSessionStatus.ARCHIVED)
                .filter(item -> matches(item.getProjectCode(), projectCode))
                .filter(item -> matches(item.getRootRequirementNo(), rootRequirementNo))
                .filter(item -> matches(item.getClientCode(), clientCode))
                .sorted(Comparator.comparing(AgentSession::getLastActiveTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AgentSession touch(String sessionCode) {
        AgentSession entity = findEntity(sessionCode);
        entity.setLastActiveTime(LocalDateTime.now());
        return agentSessionRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public AgentSession findEntity(String sessionCode) {
        return agentSessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new BizException(404, "session not found"));
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String required(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(400, fieldName + " is required");
        }
        return trimmed;
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String buildAutoSessionCode(String clientCode, String agentCode, String externalSessionId) {
        String seed = clientCode + ":" + agentCode + ":" + externalSessionId;
        return "AUTO-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private AgentSessionResponse toResponse(AgentSession entity) {
        return AgentSessionResponse.builder()
                .id(entity.getId())
                .sessionCode(entity.getSessionCode())
                .sessionName(entity.getSessionName())
                .sessionType(entity.getSessionType())
                .projectCode(entity.getProjectCode())
                .requirementNo(entity.getRequirementNo())
                .rootRequirementNo(entity.getRootRequirementNo())
                .agentCode(entity.getAgentCode())
                .clientCode(entity.getClientCode())
                .externalSessionId(entity.getExternalSessionId())
                .status(entity.getStatus())
                .reusableFlag(entity.getReusableFlag())
                .lastActiveTime(entity.getLastActiveTime())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
