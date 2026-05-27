package com.aiapi.link.service;

import com.aiapi.agent.entity.AgentInfo;
import com.aiapi.agent.service.AgentService;
import com.aiapi.common.enums.AgentStatus;
import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.exception.BizException;
import com.aiapi.link.dto.CreateRequirementLinkRequest;
import com.aiapi.link.dto.RequirementLinkResponse;
import com.aiapi.link.dto.UpdateLinkProgressRequest;
import com.aiapi.link.entity.RequirementDevLink;
import com.aiapi.link.repository.RequirementDevLinkRepository;
import com.aiapi.requirement.event.RequirementTaskCompletedEvent;
import com.aiapi.requirement.entity.RequirementInfo;
import com.aiapi.requirement.service.RequirementService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequirementDevLinkService {

    private final RequirementDevLinkRepository requirementDevLinkRepository;
    private final RequirementService requirementService;
    private final AgentService agentService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RequirementLinkResponse create(String requirementNo, CreateRequirementLinkRequest request) {
        requirementService.findEntity(requirementNo);
        AgentInfo agent = agentService.findEntity(request.getAgentCode());
        if (agent.getStatus() == AgentStatus.DISABLED) {
            throw new BizException(400, "agent is disabled");
        }
        RequirementDevLink entity = new RequirementDevLink();
        entity.setRequirementNo(requirementNo);
        entity.setLinkType(request.getLinkType());
        entity.setTaskTitle(request.getTaskTitle());
        entity.setTaskDesc(request.getTaskDesc());
        entity.setAgentCode(request.getAgentCode());
        entity.setDeveloperName(request.getDeveloperName());
        entity.setStatus(request.getStatus());
        entity.setDependsOnLinkId(request.getDependsOnLinkId());
        RequirementDevLink saved = requirementDevLinkRepository.save(entity);
        requirementService.syncRequirementStatusFromLinks(requirementNo);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RequirementLinkResponse> listByRequirementNo(String requirementNo, boolean includeChildren) {
        RequirementInfo requirement = requirementService.findEntity(requirementNo);
        Set<String> requirementNos = includeChildren && requirement.getRequirementType() == com.aiapi.common.enums.RequirementType.MASTER
                ? requirementService.collectRequirementNosUnderRoot(requirementNo)
                : Set.of(requirementNo);
        List<RequirementDevLink> entities = requirementNos.size() > 1
                ? requirementDevLinkRepository.findByRequirementNoIn(requirementNos)
                : requirementDevLinkRepository.findByRequirementNo(requirementNo);
        return entities.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RequirementLinkResponse> listAgentTasks(String agentCode) {
        agentService.findEntity(agentCode);
        return requirementDevLinkRepository.findByAgentCodeAndStatusIn(agentCode, List.of(LinkStatus.TODO, LinkStatus.DOING, LinkStatus.BLOCKED))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RequirementLinkResponse updateProgress(Long linkId, UpdateLinkProgressRequest request) {
        RequirementDevLink entity = requirementDevLinkRepository.findById(linkId)
                .orElseThrow(() -> new BizException(404, "link not found"));
        validateReceiptFields(request);
        entity.setStatus(request.getStatus());
        entity.setResultSummary(request.getResultSummary());
        entity.setExecutionDetails(trimToNull(request.getExecutionDetails()));
        entity.setDeliverablePath(request.getDeliverablePath());
        entity.setStartedAt(request.getStartedAt());
        entity.setFinishedAt(request.getFinishedAt());
        RequirementDevLink saved = requirementDevLinkRepository.save(entity);
        requirementService.syncRequirementStatusFromLinks(entity.getRequirementNo());
        if (request.getStatus() == LinkStatus.DONE || request.getStatus() == LinkStatus.SKIPPED) {
            eventPublisher.publishEvent(new RequirementTaskCompletedEvent(entity.getRequirementNo()));
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RequirementDevLink> findEntitiesByRequirementNo(String requirementNo) {
        return requirementDevLinkRepository.findByRequirementNo(requirementNo);
    }

    private RequirementLinkResponse toResponse(RequirementDevLink entity) {
        return RequirementLinkResponse.builder()
                .id(entity.getId())
                .requirementNo(entity.getRequirementNo())
                .linkType(entity.getLinkType())
                .taskTitle(entity.getTaskTitle())
                .taskDesc(entity.getTaskDesc())
                .agentCode(entity.getAgentCode())
                .developerName(entity.getDeveloperName())
                .status(entity.getStatus())
                .resultSummary(entity.getResultSummary())
                .executionDetails(entity.getExecutionDetails())
                .deliverablePath(entity.getDeliverablePath())
                .dependsOnLinkId(entity.getDependsOnLinkId())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void validateReceiptFields(UpdateLinkProgressRequest request) {
        if (request.getStatus() != LinkStatus.DONE
                && request.getStatus() != LinkStatus.BLOCKED
                && request.getStatus() != LinkStatus.SKIPPED) {
            return;
        }
        if (trimToNull(request.getResultSummary()) == null) {
            throw new BizException(400, "resultSummary is required when status is DONE/BLOCKED/SKIPPED");
        }
        if (trimToNull(request.getExecutionDetails()) == null) {
            throw new BizException(400, "executionDetails is required when status is DONE/BLOCKED/SKIPPED");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
