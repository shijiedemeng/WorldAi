package com.aiapi.skill.service;

import com.aiapi.common.exception.BizException;
import com.aiapi.agent.repository.AgentInfoRepository;
import com.aiapi.skill.dto.SaveSkillRequest;
import com.aiapi.skill.dto.SkillPageResponse;
import com.aiapi.skill.dto.SkillResponse;
import com.aiapi.skill.entity.SkillInfo;
import com.aiapi.skill.repository.SkillInfoRepository;
import jakarta.persistence.criteria.Predicate;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillInfoRepository skillInfoRepository;
    private final AgentInfoRepository agentInfoRepository;

    @Transactional(readOnly = true)
    public List<SkillResponse> list(Boolean enabledFlag) {
        return skillInfoRepository.findAll().stream()
                .filter(item -> enabledFlag == null || enabledFlag.equals(item.getEnabledFlag()))
                .sorted(Comparator.comparing(SkillInfo::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SkillPageResponse page(String keyword, Boolean enabledFlag, Integer page, Integer pageSize) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        Page<SkillInfo> result = skillInfoRepository.findAll(
                buildSpecification(keyword, enabledFlag),
                PageRequest.of(normalizedPage - 1, normalizedPageSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return SkillPageResponse.builder()
                .items(result.getContent().stream().map(this::toResponse).toList())
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public SkillResponse get(String skillCode) {
        return toResponse(findEntity(skillCode));
    }

    @Transactional
    public SkillResponse create(SaveSkillRequest request) {
        String skillCode = required(request.getSkillCode(), "skillCode");
        skillInfoRepository.findBySkillCode(skillCode).ifPresent(skill -> {
            throw new BizException(400, "skillCode already exists");
        });
        SkillInfo entity = new SkillInfo();
        entity.setSkillCode(skillCode);
        applySaveRequest(entity, request);
        return toResponse(skillInfoRepository.save(entity));
    }

    @Transactional
    public SkillResponse createWithArchive(SaveSkillRequest request, MultipartFile file) {
        String skillCode = required(request.getSkillCode(), "skillCode");
        skillInfoRepository.findBySkillCode(skillCode).ifPresent(skill -> {
            throw new BizException(400, "skillCode already exists");
        });
        SkillInfo entity = new SkillInfo();
        entity.setSkillCode(skillCode);
        applySaveRequest(entity, request);
        applyArchive(entity, file, skillCode);
        return toResponse(skillInfoRepository.save(entity));
    }

    @Transactional
    public SkillResponse update(String skillCode, SaveSkillRequest request) {
        SkillInfo entity = findEntity(skillCode);
        applySaveRequest(entity, request);
        return toResponse(skillInfoRepository.save(entity));
    }

    @Transactional
    public SkillResponse uploadArchive(String skillCode, MultipartFile file) {
        SkillInfo entity = findEntity(skillCode);
        applyArchive(entity, file, skillCode);
        return toResponse(skillInfoRepository.save(entity));
    }

    @Transactional
    public void delete(String skillCode) {
        String normalized = required(skillCode, "skillCode");
        if (agentInfoRepository.findAll().stream().anyMatch(agent -> containsSkillCode(agent.getSkillCodes(), normalized))) {
            throw new BizException(400, "skill is referenced by agents, cannot delete");
        }
        skillInfoRepository.delete(findEntity(normalized));
    }

    @Transactional(readOnly = true)
    public SkillInfo findEntity(String skillCode) {
        String normalized = required(skillCode, "skillCode");
        return skillInfoRepository.findBySkillCode(normalized)
                .orElseThrow(() -> new BizException(404, "skill not found"));
    }

    private void applySaveRequest(SkillInfo entity, SaveSkillRequest request) {
        entity.setSkillName(required(request.getSkillName(), "skillName"));
        entity.setSkillDesc(trimToNull(request.getSkillDesc()));
        entity.setContentText(trimToNull(request.getContentText()));
        entity.setEnabledFlag(request.getEnabledFlag() == null || request.getEnabledFlag());
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

    private void applyArchive(SkillInfo entity, MultipartFile file, String skillCode) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "archive file is required");
        }
        String filename = trimToNull(file.getOriginalFilename());
        if (filename != null && !filename.toLowerCase().endsWith(".zip")) {
            throw new BizException(400, "only .zip skill archive is supported");
        }
        try {
            byte[] content = file.getBytes();
            entity.setArchiveContent(content);
            entity.setContentText(extractSkillMarkdown(content));
        } catch (IOException ex) {
            throw new BizException(400, "failed to read archive file");
        }
        entity.setArchiveFileName(defaultText(filename, skillCode + ".zip"));
        entity.setArchiveContentType(defaultText(file.getContentType(), "application/octet-stream"));
        entity.setArchiveSize(file.getSize());
        entity.setLastArchiveUploadTime(LocalDateTime.now());
    }

    private String extractSkillMarkdown(byte[] archiveContent) {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archiveContent))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName().replace("\\", "/");
                int slashIndex = entryName.lastIndexOf('/');
                String filename = slashIndex >= 0 ? entryName.substring(slashIndex + 1) : entryName;
                if ("skill.md".equalsIgnoreCase(filename)) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
                }
            }
        } catch (IOException ex) {
            throw new BizException(400, "failed to extract SKILL.md from archive");
        }
        throw new BizException(400, "archive must contain SKILL.md");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean containsSkillCode(String skillCodes, String skillCode) {
        if (skillCodes == null || skillCodes.isBlank()) {
            return false;
        }
        return List.of(skillCodes.split(",")).stream()
                .map(this::trimToNull)
                .filter(item -> item != null)
                .anyMatch(skillCode::equals);
    }

    private Specification<SkillInfo> buildSpecification(String keyword, Boolean enabledFlag) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (enabledFlag != null) {
                predicates.add(builder.equal(root.get("enabledFlag"), enabledFlag));
            }
            String normalizedKeyword = trimToNull(keyword);
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword.toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("skillCode")), pattern),
                        builder.like(builder.lower(root.get("skillName")), pattern),
                        builder.like(builder.lower(root.get("skillDesc")), pattern),
                        builder.like(builder.lower(root.get("archiveFileName")), pattern)
                ));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SkillResponse toResponse(SkillInfo entity) {
        return SkillResponse.builder()
                .id(entity.getId())
                .skillCode(entity.getSkillCode())
                .skillName(entity.getSkillName())
                .skillDesc(entity.getSkillDesc())
                .contentText(entity.getContentText())
                .archiveFileName(entity.getArchiveFileName())
                .archiveContentType(entity.getArchiveContentType())
                .archiveSize(entity.getArchiveSize())
                .hasArchive(entity.getArchiveContent() != null && entity.getArchiveContent().length > 0)
                .enabledFlag(entity.getEnabledFlag())
                .lastArchiveUploadTime(entity.getLastArchiveUploadTime())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
