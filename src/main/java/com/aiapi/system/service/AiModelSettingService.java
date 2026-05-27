package com.aiapi.system.service;

import com.aiapi.common.enums.AiModelPurpose;
import com.aiapi.system.dto.AiModelSettingResponse;
import com.aiapi.system.dto.SaveAiModelSettingRequest;
import com.aiapi.system.entity.AiModelSetting;
import com.aiapi.system.repository.AiModelSettingRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiModelSettingService {

    private final AiModelSettingRepository repository;

    @Transactional(readOnly = true)
    public List<AiModelSettingResponse> list(Boolean supportImageFlag, Boolean enabledFlag, AiModelPurpose modelPurpose) {
        return repository.findAll().stream()
                .filter(item -> modelPurpose == null || modelPurpose.equals(normalizePurpose(item)))
                .filter(item -> supportImageFlag == null || supportImageFlag.equals(item.getSupportImageFlag()))
                .filter(item -> enabledFlag == null || enabledFlag.equals(item.getEnabledFlag()))
                .sorted(Comparator.comparing(AiModelSetting::getUpdatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AiModelSettingResponse save(SaveAiModelSettingRequest request) {
        String settingKey = request.getSettingKey().trim();
        AiModelSetting entity = repository.findBySettingKey(settingKey).orElseGet(AiModelSetting::new);
        entity.setSettingKey(settingKey);
        entity.setProviderName(request.getProviderName().trim());
        entity.setBaseUrl(trimToNull(request.getBaseUrl()));
        entity.setApiKey(trimToNull(request.getApiKey()));
        entity.setModelName(request.getModelName().trim());
        AiModelPurpose purpose = normalizeRequestPurpose(request);
        entity.setModelPurpose(purpose);
        entity.setVectorChunkSize(normalizeChunkSize(request.getVectorChunkSize(), purpose));
        entity.setVectorChunkOverlap(normalizeChunkOverlap(request.getVectorChunkOverlap(), entity.getVectorChunkSize(), purpose));
        entity.setSupportImageFlag(purpose == AiModelPurpose.IMAGE);
        entity.setPromptTemplate(trimToNull(request.getPromptTemplate()));
        entity.setEnabledFlag(request.getEnabledFlag() == null || request.getEnabledFlag());
        return toResponse(repository.save(entity));
    }

    private AiModelSettingResponse toResponse(AiModelSetting entity) {
        return AiModelSettingResponse.builder()
                .id(entity.getId())
                .settingKey(entity.getSettingKey())
                .providerName(entity.getProviderName())
                .baseUrl(entity.getBaseUrl())
                .apiKey(entity.getApiKey())
                .modelName(entity.getModelName())
                .modelPurpose(normalizePurpose(entity))
                .vectorChunkSize(entity.getVectorChunkSize() == null ? 500 : entity.getVectorChunkSize())
                .vectorChunkOverlap(entity.getVectorChunkOverlap() == null ? 100 : entity.getVectorChunkOverlap())
                .supportImageFlag(entity.getSupportImageFlag())
                .promptTemplate(entity.getPromptTemplate())
                .enabledFlag(entity.getEnabledFlag())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AiModelPurpose normalizeRequestPurpose(SaveAiModelSettingRequest request) {
        if (request.getModelPurpose() != null) {
            return request.getModelPurpose();
        }
        if (Boolean.TRUE.equals(request.getSupportImageFlag())) {
            return AiModelPurpose.IMAGE;
        }
        return AiModelPurpose.LANGUAGE;
    }

    private AiModelPurpose normalizePurpose(AiModelSetting entity) {
        if (entity.getModelPurpose() != null) {
            return entity.getModelPurpose();
        }
        return Boolean.TRUE.equals(entity.getSupportImageFlag()) ? AiModelPurpose.IMAGE : AiModelPurpose.LANGUAGE;
    }

    private Integer normalizeChunkSize(Integer value, AiModelPurpose purpose) {
        int chunkSize = value == null ? 500 : value;
        if (purpose != AiModelPurpose.VECTOR) {
            return 500;
        }
        return Math.max(100, Math.min(chunkSize, 8000));
    }

    private Integer normalizeChunkOverlap(Integer value, Integer chunkSize, AiModelPurpose purpose) {
        int overlap = value == null ? 100 : value;
        if (purpose != AiModelPurpose.VECTOR) {
            return 100;
        }
        int normalizedChunkSize = chunkSize == null ? 500 : chunkSize;
        return Math.max(0, Math.min(overlap, normalizedChunkSize - 1));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
