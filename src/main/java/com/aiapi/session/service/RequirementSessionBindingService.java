package com.aiapi.session.service;

import com.aiapi.common.enums.SessionBindingType;
import com.aiapi.requirement.entity.RequirementInfo;
import com.aiapi.requirement.service.RequirementService;
import com.aiapi.session.dto.BindRequirementSessionRequest;
import com.aiapi.session.dto.RequirementSessionBindingResponse;
import com.aiapi.session.entity.AgentSession;
import com.aiapi.session.entity.RequirementSessionBinding;
import com.aiapi.session.repository.RequirementSessionBindingRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequirementSessionBindingService {

    private final RequirementSessionBindingRepository requirementSessionBindingRepository;
    private final RequirementService requirementService;
    private final AgentSessionService agentSessionService;

    @Transactional
    public RequirementSessionBindingResponse bind(String requirementNo, BindRequirementSessionRequest request) {
        RequirementInfo requirement = requirementService.findEntity(requirementNo);
        AgentSession session = agentSessionService.findEntity(request.getSessionCode());

        List<RequirementSessionBinding> existing = requirementSessionBindingRepository.findByRequirementNoOrderByPrimaryFlagDescCreatedAtAsc(requirementNo);
        if (Boolean.TRUE.equals(request.getPrimaryFlag())) {
            for (RequirementSessionBinding item : existing) {
                if (Boolean.TRUE.equals(item.getPrimaryFlag())) {
                    item.setPrimaryFlag(Boolean.FALSE);
                }
            }
            requirementSessionBindingRepository.saveAll(existing);
        }

        RequirementSessionBinding binding = new RequirementSessionBinding();
        binding.setRequirementNo(requirementNo);
        binding.setRootRequirementNo(requirement.getRootRequirementNo());
        binding.setSessionCode(session.getSessionCode());
        binding.setBindingType(request.getBindingType() == null ? SessionBindingType.PREFERRED : request.getBindingType());
        binding.setPrimaryFlag(Boolean.TRUE.equals(request.getPrimaryFlag()));
        binding.setRemark(request.getRemark());
        return toResponse(requirementSessionBindingRepository.save(binding), session);
    }

    @Transactional(readOnly = true)
    public List<RequirementSessionBindingResponse> listByRequirement(String requirementNo) {
        requirementService.findEntity(requirementNo);
        List<RequirementSessionBindingResponse> result = new ArrayList<>();
        for (RequirementSessionBinding binding : requirementSessionBindingRepository.findByRequirementNoOrderByPrimaryFlagDescCreatedAtAsc(requirementNo)) {
            AgentSession session = agentSessionService.findEntity(binding.getSessionCode());
            result.add(toResponse(binding, session));
        }
        result.sort(Comparator.comparing(RequirementSessionBindingResponse::getPrimaryFlag).reversed()
                .thenComparing(RequirementSessionBindingResponse::getCreatedAt));
        return result;
    }

    private RequirementSessionBindingResponse toResponse(RequirementSessionBinding entity, AgentSession session) {
        return RequirementSessionBindingResponse.builder()
                .id(entity.getId())
                .requirementNo(entity.getRequirementNo())
                .rootRequirementNo(entity.getRootRequirementNo())
                .sessionCode(entity.getSessionCode())
                .sessionName(session.getSessionName())
                .agentCode(session.getAgentCode())
                .clientCode(session.getClientCode())
                .bindingType(entity.getBindingType())
                .primaryFlag(entity.getPrimaryFlag())
                .remark(entity.getRemark())
                .sessionLastActiveTime(session.getLastActiveTime())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
