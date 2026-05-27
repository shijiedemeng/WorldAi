package com.aiapi.log.service;

import com.aiapi.log.dto.AiAnalysisLogPageResponse;
import com.aiapi.log.dto.AiAnalysisLogResponse;
import com.aiapi.log.entity.AiAnalysisLog;
import com.aiapi.log.repository.AiAnalysisLogListProjection;
import com.aiapi.log.repository.AiAnalysisLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisLogService {

    public static final String SOURCE_REQUIREMENT_AI_SPLIT = "REQUIREMENT_AI_SPLIT";
    public static final String SOURCE_DEFECT_AI_ANALYSIS = "DEFECT_AI_ANALYSIS";
    public static final String SOURCE_PROJECT_KNOWLEDGE_ORGANIZE = "PROJECT_KNOWLEDGE_ORGANIZE";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_FALLBACK = "FALLBACK";

    private final AiAnalysisLogRepository repository;

    @Transactional(readOnly = true)
    public AiAnalysisLogPageResponse list(String sourceType,
                                          String projectCode,
                                          String businessNo,
                                          String status,
                                          String keyword,
                                          Integer page,
                                          Integer pageSize) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<AiAnalysisLogListProjection> result = repository.findListPage(
                trimToNull(sourceType),
                trimToNull(projectCode),
                trimToNull(businessNo),
                trimToNull(status),
                trimToNull(keyword),
                PageRequest.of(normalizedPage - 1, normalizedPageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return AiAnalysisLogPageResponse.builder()
                .items(result.getContent().stream().map(this::toListResponse).toList())
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public AiAnalysisLogResponse detail(Long id) {
        AiAnalysisLog entity = repository.findById(id)
                .orElseThrow(() -> new com.aiapi.common.exception.BizException(404, "ai analysis log not found"));
        return toResponse(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String sourceType,
                       String businessNo,
                       String projectCode,
                       String aiSettingKey,
                       String modelName,
                       String status,
                       String requestPayload,
                       String responsePayload,
                       String errorMessage) {
        try {
            AiAnalysisLog entity = new AiAnalysisLog();
            entity.setSourceType(trimToNull(sourceType) == null ? "UNKNOWN" : sourceType.trim());
            entity.setBusinessNo(trimToNull(businessNo) == null ? "-" : businessNo.trim());
            entity.setProjectCode(trimToNull(projectCode));
            entity.setAiSettingKey(trimToNull(aiSettingKey));
            entity.setModelName(trimToNull(modelName));
            entity.setStatus(trimToNull(status) == null ? STATUS_SUCCESS : status.trim());
            entity.setRequestPayload(trimToNull(requestPayload));
            entity.setResponsePayload(trimToNull(responsePayload));
            entity.setErrorMessage(trimToNull(errorMessage));
            repository.save(entity);
        } catch (Exception ex) {
            log.warn("ai analysis log save failed: {}", ex.getMessage());
        }
    }

    private AiAnalysisLogResponse toListResponse(AiAnalysisLogListProjection entity) {
        return AiAnalysisLogResponse.builder()
                .id(entity.getId())
                .sourceType(entity.getSourceType())
                .businessNo(entity.getBusinessNo())
                .projectCode(entity.getProjectCode())
                .aiSettingKey(entity.getAiSettingKey())
                .modelName(entity.getModelName())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AiAnalysisLogResponse toResponse(AiAnalysisLog entity) {
        return AiAnalysisLogResponse.builder()
                .id(entity.getId())
                .sourceType(entity.getSourceType())
                .businessNo(entity.getBusinessNo())
                .projectCode(entity.getProjectCode())
                .aiSettingKey(entity.getAiSettingKey())
                .modelName(entity.getModelName())
                .status(entity.getStatus())
                .requestPayload(entity.getRequestPayload())
                .responsePayload(entity.getResponsePayload())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
