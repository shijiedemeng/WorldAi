package com.aiapi.image.service;

import com.aiapi.common.enums.ImagePromptTemplateType;
import com.aiapi.common.exception.BizException;
import com.aiapi.image.dto.ImagePromptTemplatePageResponse;
import com.aiapi.image.dto.ImagePromptTemplateResponse;
import com.aiapi.image.dto.SaveImagePromptTemplateRequest;
import com.aiapi.image.entity.ImagePromptTemplate;
import com.aiapi.image.repository.ImagePromptTemplateRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImagePromptTemplateService {

    private final ImagePromptTemplateRepository repository;

    @Transactional(readOnly = true)
    public List<ImagePromptTemplateResponse> list(String templateType, Boolean enabledFlag, String keyword) {
        return repository.findAll(
                        buildSpecification(templateType, enabledFlag, keyword),
                        defaultSort())
                .stream()
                .map(this::toListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImagePromptTemplatePageResponse page(String templateType,
                                                Boolean enabledFlag,
                                                String keyword,
                                                Integer page,
                                                Integer pageSize) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<ImagePromptTemplate> result = repository.findAll(
                buildSpecification(templateType, enabledFlag, keyword),
                PageRequest.of(normalizedPage - 1, normalizedPageSize, defaultSort()));
        return ImagePromptTemplatePageResponse.builder()
                .items(result.getContent().stream().map(this::toListResponse).toList())
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public ImagePromptTemplateResponse get(String templateCode) {
        return toResponse(findEntity(templateCode));
    }

    @Transactional
    public ImagePromptTemplateResponse save(SaveImagePromptTemplateRequest request) {
        String templateCode = required(request.getTemplateCode(), "templateCode");
        ImagePromptTemplate entity = repository.findByTemplateCode(templateCode).orElseGet(ImagePromptTemplate::new);
        entity.setTemplateCode(templateCode);
        entity.setTemplateName(required(request.getTemplateName(), "templateName"));
        entity.setTemplateType(request.getTemplateType());
        entity.setContentText(required(request.getContentText(), "contentText"));
        entity.setEnabledFlag(request.getEnabledFlag() == null || request.getEnabledFlag());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(String templateCode) {
        repository.delete(findEntity(templateCode));
    }

    @Transactional(readOnly = true)
    public ImagePromptTemplate findEntity(String templateCode) {
        String normalized = required(templateCode, "templateCode");
        return repository.findByTemplateCode(normalized)
                .orElseThrow(() -> new BizException(404, "image prompt template not found"));
    }

    private Specification<ImagePromptTemplate> buildSpecification(String templateType, Boolean enabledFlag, String keyword) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            ImagePromptTemplateType normalizedType = parseType(templateType);
            if (normalizedType != null) {
                predicates.add(builder.equal(root.get("templateType"), normalizedType));
            }
            if (enabledFlag != null) {
                predicates.add(builder.equal(root.get("enabledFlag"), enabledFlag));
            }
            String normalizedKeyword = trimToNull(keyword);
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword.toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("templateCode")), pattern),
                        builder.like(builder.lower(root.get("templateName")), pattern),
                        builder.like(builder.lower(root.get("contentText")), pattern)
                ));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort defaultSort() {
        return Sort.by(
                Sort.Order.asc("templateType"),
                Sort.Order.asc("sortOrder"),
                Sort.Order.desc("updatedAt"));
    }

    private ImagePromptTemplateResponse toResponse(ImagePromptTemplate entity) {
        return ImagePromptTemplateResponse.builder()
                .id(entity.getId())
                .templateCode(entity.getTemplateCode())
                .templateName(entity.getTemplateName())
                .templateType(entity.getTemplateType())
                .contentText(entity.getContentText())
                .enabledFlag(entity.getEnabledFlag())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ImagePromptTemplateResponse toListResponse(ImagePromptTemplate entity) {
        return ImagePromptTemplateResponse.builder()
                .id(entity.getId())
                .templateCode(entity.getTemplateCode())
                .templateName(entity.getTemplateName())
                .templateType(entity.getTemplateType())
                .contentText(firstChars(entity.getContentText(), 10))
                .enabledFlag(entity.getEnabledFlag())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String firstChars(String value, int maxChars) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "";
        }
        int codePointCount = normalized.codePointCount(0, normalized.length());
        if (codePointCount <= maxChars) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, maxChars);
        return normalized.substring(0, endIndex);
    }

    private ImagePromptTemplateType parseType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return ImagePromptTemplateType.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(400, "invalid image prompt template type");
        }
    }

    private String required(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(400, fieldName + " is required");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
