package com.aiapi.requirement.service;

import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.common.enums.RequirementType;
import com.aiapi.requirement.dto.AnalyzeRequirementRequest;
import com.aiapi.requirement.repository.RequirementInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RequirementAiAnalysisAsyncService {

    private final RequirementAiAnalysisService requirementAiAnalysisService;
    private final RequirementInfoRepository requirementInfoRepository;

    public RequirementAiAnalysisAsyncService(@Lazy RequirementAiAnalysisService requirementAiAnalysisService,
                                             RequirementInfoRepository requirementInfoRepository) {
        this.requirementAiAnalysisService = requirementAiAnalysisService;
        this.requirementInfoRepository = requirementInfoRepository;
    }

    @Async("aiApiTaskExecutor")
    public void submit(String requirementNo, AnalyzeRequirementRequest request) {
        try {
            requirementAiAnalysisService.executeAnalysis(requirementNo, request);
        } catch (Exception ex) {
            log.error("requirement ai analysis failed for {}: {}", requirementNo, ex.getMessage(), ex);
            releaseLock(requirementNo);
        }
    }

    private void releaseLock(String requirementNo) {
        requirementInfoRepository.findByRequirementNo(requirementNo).ifPresent(entity -> {
            if (entity.getRequirementType() == RequirementType.MASTER && entity.getStatus() == RequirementStatus.ANALYZING) {
                entity.setStatus(RequirementStatus.PENDING);
                requirementInfoRepository.save(entity);
            }
        });
    }
}
