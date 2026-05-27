package com.aiapi.testrecord.service;

import com.aiapi.agent.service.AgentService;
import com.aiapi.requirement.service.RequirementService;
import com.aiapi.testrecord.dto.CreateTestRecordRequest;
import com.aiapi.testrecord.dto.TestRecordResponse;
import com.aiapi.testrecord.entity.RequirementTestRecord;
import com.aiapi.testrecord.repository.RequirementTestRecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequirementTestRecordService {

    private final RequirementTestRecordRepository requirementTestRecordRepository;
    private final RequirementService requirementService;
    private final AgentService agentService;

    @Transactional
    public TestRecordResponse create(String requirementNo, CreateTestRecordRequest request) {
        requirementService.findEntity(requirementNo);
        if (request.getTesterAgentCode() != null && !request.getTesterAgentCode().isBlank()) {
            agentService.findEntity(request.getTesterAgentCode());
        }
        RequirementTestRecord entity = new RequirementTestRecord();
        entity.setRequirementNo(requirementNo);
        entity.setTestType(request.getTestType());
        entity.setTestTitle(request.getTestTitle());
        entity.setTestContent(request.getTestContent());
        entity.setTesterAgentCode(request.getTesterAgentCode());
        entity.setTesterName(request.getTesterName());
        entity.setTestResult(request.getTestResult());
        entity.setBugCount(request.getBugCount());
        entity.setRiskDesc(request.getRiskDesc());
        entity.setSuggestion(request.getSuggestion());
        entity.setAttachments(request.getAttachments());
        entity.setTestedAt(request.getTestedAt());
        return toResponse(requirementTestRecordRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<TestRecordResponse> listByRequirementNo(String requirementNo) {
        requirementService.findEntity(requirementNo);
        return requirementTestRecordRepository.findByRequirementNo(requirementNo).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RequirementTestRecord> findEntitiesByRequirementNo(String requirementNo) {
        return requirementTestRecordRepository.findByRequirementNo(requirementNo);
    }

    private TestRecordResponse toResponse(RequirementTestRecord entity) {
        return TestRecordResponse.builder()
                .id(entity.getId())
                .requirementNo(entity.getRequirementNo())
                .testType(entity.getTestType())
                .testTitle(entity.getTestTitle())
                .testContent(entity.getTestContent())
                .testerAgentCode(entity.getTesterAgentCode())
                .testerName(entity.getTesterName())
                .testResult(entity.getTestResult())
                .bugCount(entity.getBugCount())
                .riskDesc(entity.getRiskDesc())
                .suggestion(entity.getSuggestion())
                .attachments(entity.getAttachments())
                .testedAt(entity.getTestedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
