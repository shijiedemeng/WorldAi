package com.aiapi.knowledge.service;

import com.aiapi.common.enums.AiModelPurpose;
import com.aiapi.common.enums.ProjectKnowledgeStatus;
import com.aiapi.common.enums.ProjectKnowledgeType;
import com.aiapi.common.enums.ProjectKnowledgeVectorStatus;
import com.aiapi.common.exception.BizException;
import com.aiapi.knowledge.dto.ConfirmProjectKnowledgeRequest;
import com.aiapi.knowledge.dto.CreateKnowledgeFromRequirementRequest;
import com.aiapi.knowledge.dto.CreateProjectKnowledgeRequest;
import com.aiapi.knowledge.dto.OrganizeProjectKnowledgeRequest;
import com.aiapi.knowledge.dto.ProjectKnowledgePageResponse;
import com.aiapi.knowledge.dto.ProjectKnowledgeResponse;
import com.aiapi.knowledge.dto.ProjectKnowledgeSearchResponse;
import com.aiapi.knowledge.dto.SearchProjectKnowledgeRequest;
import com.aiapi.knowledge.dto.UpdateProjectKnowledgeRequest;
import com.aiapi.knowledge.entity.ProjectKnowledgeItem;
import com.aiapi.knowledge.repository.ProjectKnowledgeItemRepository;
import com.aiapi.log.service.AiAnalysisLogService;
import com.aiapi.markdown.entity.MarkdownDocument;
import com.aiapi.markdown.repository.MarkdownDocumentRepository;
import com.aiapi.project.service.ProjectService;
import com.aiapi.requirement.dto.RequirementResponse;
import com.aiapi.requirement.dto.RequirementWorkflowResultResponse;
import com.aiapi.requirement.service.RequirementService;
import com.aiapi.system.entity.AiModelSetting;
import com.aiapi.system.repository.AiModelSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ProjectKnowledgeReserveService {

    private final ProjectKnowledgeItemRepository repository;
    private final ProjectService projectService;
    private final RequirementService requirementService;
    private final MarkdownDocumentRepository markdownDocumentRepository;
    private final AiModelSettingRepository aiModelSettingRepository;
    private final ProjectKnowledgeVectorClient vectorClient;
    private final ProjectKnowledgeOrganizeAsyncService organizeAsyncService;
    private final AiAnalysisLogService aiAnalysisLogService;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProjectKnowledgePageResponse page(String projectCode,
                                             ProjectKnowledgeType knowledgeType,
                                             ProjectKnowledgeStatus status,
                                             ProjectKnowledgeVectorStatus vectorStatus,
                                             String keyword,
                                             Integer page,
                                             Integer pageSize) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<ProjectKnowledgeItem> result = repository.findAll(
                buildSpecification(projectCode, knowledgeType, status, vectorStatus, keyword),
                PageRequest.of(normalizedPage - 1, normalizedPageSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return ProjectKnowledgePageResponse.builder()
                .items(result.getContent().stream().map(this::toResponse).toList())
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public ProjectKnowledgeResponse get(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<ProjectKnowledgeResponse> listByRequirement(String requirementNo) {
        String normalized = required(requirementNo, "requirementNo");
        return repository.findBySourceRequirementNo(normalized).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectKnowledgeResponse create(CreateProjectKnowledgeRequest request) {
        projectService.findEntity(request.getProjectCode());
        ProjectKnowledgeItem entity = new ProjectKnowledgeItem();
        entity.setProjectCode(request.getProjectCode().trim());
        entity.setKnowledgeType(request.getKnowledgeType());
        entity.setTitle(request.getTitle().trim());
        entity.setSimpleDesc(trimToNull(request.getSimpleDesc()));
        entity.setDetailContent(trimToNull(request.getDetailContent()));
        entity.setAiSettingKey(trimToNull(request.getAiSettingKey()));
        entity.setEmbeddingSettingKey(trimToNull(request.getEmbeddingSettingKey()));
        entity.setDocumentIds(trimCsvToNull(request.getDocumentIds()));
        entity.setStatus(ProjectKnowledgeStatus.DRAFT);
        entity.setVectorStatus(ProjectKnowledgeVectorStatus.PENDING);
        entity.setVectorDirtyFlag(true);
        entity.setVectorChunkCount(0);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ProjectKnowledgeResponse createFromRequirement(String requirementNo, CreateKnowledgeFromRequirementRequest request) {
        RequirementResponse requirement = requirementService.get(requirementNo);
        List<ProjectKnowledgeItem> existing = repository.findBySourceRequirementNo(requirementNo).stream()
                .filter(item -> item.getStatus() == ProjectKnowledgeStatus.CONFIRMED
                        && item.getVectorStatus() == ProjectKnowledgeVectorStatus.READY)
                .toList();
        if (!existing.isEmpty()) {
            throw new BizException(400, "current requirement already has confirmed vector knowledge");
        }
        ProjectKnowledgeItem entity = new ProjectKnowledgeItem();
        entity.setProjectCode(requirement.getProjectCode());
        entity.setKnowledgeType(request.getKnowledgeType());
        entity.setTitle(request.getTitle().trim());
        entity.setSimpleDesc(trimToNull(request.getSimpleDesc()));
        entity.setDetailContent(buildRequirementKnowledgeSeed(requirementNo, request.getReceiptRequirementNos()));
        entity.setSourceRequirementNo(requirementNo);
        entity.setSourceSummary(buildRequirementSourceSummary(requirement));
        entity.setAiSettingKey(trimToNull(request.getAiSettingKey()));
        entity.setEmbeddingSettingKey(trimToNull(request.getEmbeddingSettingKey()));
        entity.setDocumentIds(trimCsvToNull(request.getDocumentIds()));
        entity.setSelectedSimilarKnowledgeId(request.getSelectedSimilarKnowledgeId());
        entity.setStatus(ProjectKnowledgeStatus.DRAFT);
        entity.setVectorStatus(ProjectKnowledgeVectorStatus.PENDING);
        entity.setVectorDirtyFlag(true);
        entity.setVectorChunkCount(0);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ProjectKnowledgeResponse update(Long id, UpdateProjectKnowledgeRequest request) {
        ProjectKnowledgeItem entity = findEntity(id);
        assertNotOrganizing(entity);
        entity.setKnowledgeType(request.getKnowledgeType());
        entity.setTitle(request.getTitle().trim());
        entity.setSimpleDesc(trimToNull(request.getSimpleDesc()));
        entity.setDetailContent(trimToNull(request.getDetailContent()));
        entity.setOrganizedContent(trimToNull(request.getOrganizedContent()));
        entity.setConfirmedContent(trimToNull(request.getConfirmedContent()));
        entity.setAiSettingKey(trimToNull(request.getAiSettingKey()));
        entity.setEmbeddingSettingKey(trimToNull(request.getEmbeddingSettingKey()));
        entity.setDocumentIds(trimCsvToNull(request.getDocumentIds()));
        entity.setSelectedSimilarKnowledgeId(request.getSelectedSimilarKnowledgeId());
        markVectorDirty(entity);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public ProjectKnowledgeResponse organize(Long id, OrganizeProjectKnowledgeRequest request) {
        ProjectKnowledgeItem entity = findEntity(id);
        if (entity.getStatus() == ProjectKnowledgeStatus.AI_ORGANIZING) {
            throw new BizException(400, "项目储备知识正在 AI 整理中，请稍后再操作");
        }
        String aiSettingKey = trimToNull(request == null ? null : request.getAiSettingKey());
        if (aiSettingKey != null) {
            entity.setAiSettingKey(aiSettingKey);
        }
        String documentIds = trimCsvToNull(request == null ? null : request.getDocumentIds());
        if (documentIds != null) {
            entity.setDocumentIds(documentIds);
        }
        Long selectedSimilarId = request == null ? null : request.getSelectedSimilarKnowledgeId();
        if (selectedSimilarId != null) {
            ProjectKnowledgeItem similar = findEntity(selectedSimilarId);
            if (!similar.getProjectCode().equals(entity.getProjectCode())) {
                throw new BizException(400, "selected similar knowledge must belong to same project");
            }
            entity.setSelectedSimilarKnowledgeId(selectedSimilarId);
        }
        entity.setStatus(ProjectKnowledgeStatus.AI_ORGANIZING);
        entity.setErrorMessage(null);
        ProjectKnowledgeItem saved = repository.save(entity);
        organizeAsyncService.submit(saved.getId());
        return toResponse(saved);
    }

    public void executeOrganize(Long id) {
        ProjectKnowledgeItem entity = findEntity(id);
        if (entity.getStatus() != ProjectKnowledgeStatus.AI_ORGANIZING) {
            return;
        }
        AiModelSetting setting = resolveSetting(entity.getAiSettingKey(), "aiSettingKey");
        String content = callTextAi(setting, entity, buildOrganizePrompt(entity, null));
        completeOrganize(id, content);
    }

    @Transactional
    public void completeOrganize(Long id, String content) {
        ProjectKnowledgeItem entity = findEntity(id);
        if (entity.getStatus() != ProjectKnowledgeStatus.AI_ORGANIZING) {
            return;
        }
        entity.setOrganizedContent(content);
        entity.setConfirmedContent(content);
        entity.setStatus(ProjectKnowledgeStatus.AI_READY);
        entity.setErrorMessage(null);
        markVectorDirty(entity);
        repository.save(entity);
    }

    @Transactional
    public void markOrganizeFailed(Long id, String errorMessage) {
        repository.findById(id).ifPresent(entity -> {
            if (entity.getStatus() == ProjectKnowledgeStatus.AI_ORGANIZING) {
                entity.setStatus(ProjectKnowledgeStatus.DRAFT);
                entity.setErrorMessage(truncate(errorMessage, 4000));
                repository.save(entity);
            }
        });
    }

    @Transactional
    public ProjectKnowledgeResponse confirmAndVectorize(Long id, ConfirmProjectKnowledgeRequest request) {
        ProjectKnowledgeItem entity = findEntity(id);
        assertNotOrganizing(entity);
        String content = trimToNull(request == null ? null : request.getConfirmedContent());
        if (content != null) {
            entity.setConfirmedContent(content);
        }
        String embeddingSettingKey = trimToNull(request == null ? null : request.getEmbeddingSettingKey());
        if (embeddingSettingKey != null) {
            entity.setEmbeddingSettingKey(embeddingSettingKey);
        }
        String finalContent = vectorizableKnowledgeContent(entity);
        if (finalContent == null) {
            throw new BizException(400, "请先填写或生成 AI 整理结果，再确认向量入库");
        }
        String pendingVectorId = null;
        int insertedChunkCount = 0;
        try {
            AiModelSetting setting = resolveSetting(entity.getEmbeddingSettingKey(), "embeddingSettingKey");
            List<String> chunks = splitVectorChunks(finalContent, setting);
            deleteExistingVectors(entity);
            String vectorId = "knowledge-" + entity.getId();
            pendingVectorId = vectorId;
            ProjectKnowledgeVectorClient.VectorUpsertResult result = null;
            for (int index = 0; index < chunks.size(); index++) {
                String chunk = chunks.get(index);
                List<Double> embedding = callEmbedding(setting, chunk);
                result = vectorClient.upsert(
                        entity.getProjectCode(),
                        chunkVectorId(vectorId, index + 1),
                        embedding,
                        buildVectorMetadata(entity, vectorId, index + 1, chunks.size()),
                        chunk);
                insertedChunkCount++;
            }
            entity.setStatus(ProjectKnowledgeStatus.CONFIRMED);
            entity.setVectorStatus(ProjectKnowledgeVectorStatus.READY);
            entity.setVectorDirtyFlag(false);
            entity.setVectorCollection(result == null ? null : result.collection());
            entity.setVectorId(vectorId);
            entity.setVectorChunkCount(chunks.size());
            entity.setVectorFilePath(result == null ? null : result.dbPath());
            entity.setVectorUpdatedAt(LocalDateTime.now());
            entity.setErrorMessage(null);
            return toResponse(repository.save(entity));
        } catch (RuntimeException ex) {
            deleteInsertedVectorChunks(entity.getProjectCode(), pendingVectorId, insertedChunkCount);
            entity.setVectorStatus(ProjectKnowledgeVectorStatus.FAILED);
            entity.setErrorMessage(truncate(ex.getMessage(), 4000));
            repository.save(entity);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ProjectKnowledgeSearchResponse search(SearchProjectKnowledgeRequest request) {
        String projectCode = required(request.getProjectCode(), "projectCode");
        projectService.findEntity(projectCode);
        String embeddingSettingKey = trimToNull(request.getEmbeddingSettingKey());
        AiModelSetting setting = resolveSetting(embeddingSettingKey == null ? defaultEmbeddingSettingKey() : embeddingSettingKey,
                "embeddingSettingKey");
        List<Double> embedding = callEmbedding(setting, request.getQuery());
        int limit = normalizeSearchLimit(request.getLimit());
        int rawLimit = Math.min(100, Math.max(limit, limit * 8));
        List<ProjectKnowledgeSearchResponse.Item> items = vectorClient.search(projectCode, embedding, rawLimit);
        ProjectKnowledgeType type = request.getKnowledgeType();
        if (type != null) {
            items = items.stream()
                    .filter(item -> type.name().equals(item.getKnowledgeType()))
                    .toList();
        }
        items = aggregateSearchItems(items, limit);
        Double minMatchScore = normalizeMinMatchScore(request.getMinMatchScore());
        if (minMatchScore != null) {
            items = items.stream()
                    .filter(item -> item.getMatchScore() != null && item.getMatchScore() >= minMatchScore)
                    .toList();
        }
        return ProjectKnowledgeSearchResponse.builder()
                .projectCode(projectCode)
                .query(request.getQuery())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public ProjectKnowledgeSearchResponse similar(Long id, String embeddingSettingKey, Integer limit) {
        ProjectKnowledgeItem entity = findEntity(id);
        SearchProjectKnowledgeRequest request = new SearchProjectKnowledgeRequest();
        request.setProjectCode(entity.getProjectCode());
        request.setKnowledgeType(entity.getKnowledgeType());
        request.setQuery(Objects.requireNonNullElse(finalKnowledgeContent(entity), ""));
        request.setEmbeddingSettingKey(trimToNull(embeddingSettingKey) == null ? entity.getEmbeddingSettingKey() : embeddingSettingKey);
        request.setLimit(limit);
        ProjectKnowledgeSearchResponse response = search(request);
        return ProjectKnowledgeSearchResponse.builder()
                .projectCode(response.getProjectCode())
                .query(response.getQuery())
                .items(response.getItems().stream()
                        .filter(item -> item.getId() == null || !item.getId().equals(entity.getId()))
                        .toList())
                .build();
    }

    @Transactional
    public void delete(Long id) {
        ProjectKnowledgeItem entity = findEntity(id);
        assertNotOrganizing(entity);
        deleteExistingVectors(entity);
        repository.delete(entity);
    }

    private int normalizeSearchLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 5;
        }
        return Math.min(limit, 20);
    }

    private Double normalizeMinMatchScore(Double minMatchScore) {
        if (minMatchScore == null || minMatchScore.isNaN() || minMatchScore.isInfinite()) {
            return null;
        }
        double normalized = minMatchScore;
        if (normalized <= 0D) {
            return null;
        }
        if (normalized <= 1D) {
            normalized = normalized * 100D;
        }
        return Math.min(normalized, 100D);
    }

    private List<ProjectKnowledgeSearchResponse.Item> aggregateSearchItems(List<ProjectKnowledgeSearchResponse.Item> rawItems,
                                                                          int limit) {
        Map<String, KnowledgeSearchAggregate> aggregateMap = new LinkedHashMap<>();
        for (int index = 0; index < rawItems.size(); index++) {
            ProjectKnowledgeSearchResponse.Item item = rawItems.get(index);
            String key = item.getId() == null ? "__chunk_" + index : String.valueOf(item.getId());
            KnowledgeSearchAggregate aggregate = aggregateMap.computeIfAbsent(key, ignored -> new KnowledgeSearchAggregate(item));
            aggregate.add(item);
        }
        return aggregateMap.values().stream()
                .map(this::toAggregatedSearchItem)
                .sorted(Comparator.comparing(ProjectKnowledgeSearchResponse.Item::getMatchScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private ProjectKnowledgeSearchResponse.Item toAggregatedSearchItem(KnowledgeSearchAggregate aggregate) {
        double bestScore = aggregate.normalizedScores.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0D);
        double averageTopScore = aggregate.normalizedScores.stream()
                .sorted(Comparator.reverseOrder())
                .limit(3)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(bestScore);
        double matchScore = Math.min(1D, bestScore * 0.7D + averageTopScore * 0.3D);
        return ProjectKnowledgeSearchResponse.Item.builder()
                .id(aggregate.id)
                .title(aggregate.title)
                .simpleDesc(aggregate.simpleDesc)
                .content(aggregate.bestContent)
                .knowledgeType(aggregate.knowledgeType)
                .score(toPercent(matchScore))
                .matchScore(toPercent(matchScore))
                .bestChunkScore(toPercent(bestScore))
                .matchedChunkCount(aggregate.normalizedScores.size())
                .build();
    }

    private double normalizeVectorScore(Double score) {
        if (score == null || score.isNaN() || score.isInfinite()) {
            return 0D;
        }
        if (score <= 0D) {
            return 0D;
        }
        if (score <= 1D) {
            return score;
        }
        if (score <= 100D) {
            return score / 100D;
        }
        return 1D;
    }

    private double toPercent(double score) {
        return Math.round(score * 10000D) / 100D;
    }

    private final class KnowledgeSearchAggregate {
        private final Long id;
        private final String title;
        private final String simpleDesc;
        private final String knowledgeType;
        private final List<Double> normalizedScores = new ArrayList<>();
        private String bestContent;
        private double bestScore;

        private KnowledgeSearchAggregate(ProjectKnowledgeSearchResponse.Item item) {
            this.id = item.getId();
            this.title = item.getTitle();
            this.simpleDesc = item.getSimpleDesc();
            this.knowledgeType = item.getKnowledgeType();
        }

        private void add(ProjectKnowledgeSearchResponse.Item item) {
            double normalizedScore = normalizeVectorScore(item.getScore());
            normalizedScores.add(normalizedScore);
            if (bestContent == null || normalizedScore >= bestScore) {
                bestScore = normalizedScore;
                bestContent = item.getContent();
            }
        }
    }

    private Specification<ProjectKnowledgeItem> buildSpecification(String projectCode,
                                                                   ProjectKnowledgeType knowledgeType,
                                                                   ProjectKnowledgeStatus status,
                                                                   ProjectKnowledgeVectorStatus vectorStatus,
                                                                   String keyword) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            String normalizedProjectCode = trimToNull(projectCode);
            if (normalizedProjectCode != null) {
                predicates.add(builder.equal(root.get("projectCode"), normalizedProjectCode));
            }
            if (knowledgeType != null) {
                predicates.add(builder.equal(root.get("knowledgeType"), knowledgeType));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (vectorStatus != null) {
                predicates.add(builder.equal(root.get("vectorStatus"), vectorStatus));
            }
            String normalizedKeyword = trimToNull(keyword);
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("simpleDesc")), pattern),
                        builder.like(builder.lower(root.get("detailContent")), pattern),
                        builder.like(builder.lower(root.get("organizedContent")), pattern),
                        builder.like(builder.lower(root.get("confirmedContent")), pattern)
                ));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String buildRequirementKnowledgeSeed(String requirementNo, String receiptRequirementNos) {
        RequirementResponse requirement = requirementService.get(requirementNo);
        List<String> selectedReceiptNos = csvToList(receiptRequirementNos);
        if (selectedReceiptNos.isEmpty()) {
            throw new BizException(400, "请选择至少一条执行回执");
        }
        RequirementWorkflowResultResponse results = requirementService.workflowResults(requirementNo);
        List<RequirementWorkflowResultResponse.Item> receiptItems = results.getItems();
        LinkedHashSet<String> selectedNoSet = new LinkedHashSet<>(selectedReceiptNos);
        receiptItems = receiptItems.stream()
                .filter(item -> selectedNoSet.contains(item.getRequirementNo()))
                .toList();
        if (receiptItems.isEmpty()) {
            throw new BizException(400, "选中的执行回执不存在或不可提取，无法沉淀项目储备知识");
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "需求编号：" + requirement.getRequirementNo());
        appendLine(builder, "标题：" + requirement.getTitle());
        appendLine(builder, "状态：" + requirement.getStatus());
        appendLine(builder, "");
        appendLine(builder, "可提取执行回执：");
        for (RequirementWorkflowResultResponse.Item item : receiptItems) {
            appendLine(builder, "## " + item.getRequirementNo() + " " + item.getTitle());
            appendLine(builder, "结果摘要：" + nullToDash(item.getResultSummary()));
            appendLine(builder, "执行详情：");
            appendLine(builder, item.getExecutionDetails());
        }
        return builder.toString().trim();
    }

    private String buildRequirementSourceSummary(RequirementResponse requirement) {
        return "需求编号：" + requirement.getRequirementNo()
                + "\n项目编码：" + requirement.getProjectCode()
                + "\n标题：" + requirement.getTitle()
                + "\n状态：" + requirement.getStatus();
    }

    private String buildOrganizePrompt(ProjectKnowledgeItem entity, String extraPrompt) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "请把下面项目储备知识整理成可复用的知识条目。");
        appendLine(builder, "输出中文纯文本，不要 Markdown 代码块。");
        appendLine(builder, "类型：" + knowledgeTypeLabel(entity.getKnowledgeType()));
        appendLine(builder, "标题：" + entity.getTitle());
        appendLine(builder, "简单描述：" + nullToDash(entity.getSimpleDesc()));
        appendLine(builder, "详细内容：");
        appendLine(builder, entity.getDetailContent());
        if (trimToNull(entity.getSourceSummary()) != null) {
            appendLine(builder, "\n来源：");
            appendLine(builder, entity.getSourceSummary());
        }
        String documentContext = buildDocumentContext(entity.getDocumentIds());
        if (documentContext != null) {
            appendLine(builder, "\n可参考文档标准：");
            appendLine(builder, documentContext);
        }
        ProjectKnowledgeItem similar = entity.getSelectedSimilarKnowledgeId() == null
                ? null
                : repository.findById(entity.getSelectedSimilarKnowledgeId()).orElse(null);
        if (similar != null) {
            appendLine(builder, "\n已选择的相似旧知识，请整合去重：");
            appendLine(builder, finalKnowledgeContent(similar));
        }
        if (trimToNull(extraPrompt) != null) {
            appendLine(builder, "\n额外要求：");
            appendLine(builder, extraPrompt);
        }
        appendLine(builder, "\n整理要求：");
        appendLine(builder, "1. 原始材料来自执行回执，人工只做微调确认；不要凭空补充未在回执中出现的结论。");
        appendLine(builder, "2. 去掉无关日志、重复回执和临时对话。");
        appendLine(builder, "3. 如果是常见问题，突出症状、原因、排查步骤和解决方式。");
        appendLine(builder, "4. 如果是流程说明，突出适用场景、输入、步骤、输出和边界。");
        return builder.toString();
    }

    private String buildDocumentContext(String documentIds) {
        List<String> ids = csvToList(documentIds);
        if (ids.isEmpty()) {
            return null;
        }
        Map<String, MarkdownDocument> documents = markdownDocumentRepository.findByDocumentIdIn(ids).stream()
                .collect(Collectors.toMap(MarkdownDocument::getDocumentId, item -> item));
        List<String> parts = new ArrayList<>();
        for (String id : ids) {
            MarkdownDocument document = documents.get(id);
            if (document == null || "FOLDER".equalsIgnoreCase(document.getNodeType())) {
                continue;
            }
            parts.add("### " + document.getTitle() + "（" + document.getDocumentId() + "）\n" + document.getContent());
        }
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    private String callTextAi(AiModelSetting setting, ProjectKnowledgeItem entity, String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", setting.getModelName());
        body.put("instructions", "你是项目知识库整理助手，输出可直接沉淀到项目储备库的中文纯文本。");
        body.put("input", prompt);
        body.put("max_output_tokens", 4096);
        body.put("store", false);
        String requestUrl = resolveEndpoint(setting, "/responses");
        Map<String, Object> requestLog = new LinkedHashMap<>();
        requestLog.put("url", requestUrl);
        requestLog.put("body", body);
        String requestPayload = jsonPayload(requestLog);
        String responsePayload = null;
        try {
            ResponseEntity<byte[]> response = restTemplate().exchange(
                    requestUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, aiHeaders(setting)),
                    byte[].class);
            String bodyText = bodyText(response.getBody());
            responsePayload = responsePayload(response.getStatusCode().value(), response.getHeaders().toSingleValueMap(), bodyText);
            if (bodyText == null || bodyText.isBlank()) {
                recordKnowledgeOrganizeLog(entity, setting, requestPayload, responsePayload,
                        AiAnalysisLogService.STATUS_FAILED, "ai api returned empty response");
                throw new BizException(502, "ai api returned empty response");
            }
            JsonNode root = objectMapper.readTree(bodyText);
            String text = extractText(root);
            if (text == null || text.isBlank()) {
                recordKnowledgeOrganizeLog(entity, setting, requestPayload, responsePayload,
                        AiAnalysisLogService.STATUS_FAILED, "ai organize response missing text");
                throw new BizException(502, "ai organize response missing text");
            }
            recordKnowledgeOrganizeLog(entity, setting, requestPayload, responsePayload,
                    AiAnalysisLogService.STATUS_SUCCESS, null);
            return text.trim();
        } catch (RestClientResponseException ex) {
            responsePayload = responsePayload(
                    ex.getStatusCode().value(),
                    ex.getResponseHeaders() == null ? Map.of() : ex.getResponseHeaders().toSingleValueMap(),
                    ex.getResponseBodyAsString(StandardCharsets.UTF_8));
            recordKnowledgeOrganizeLog(entity, setting, requestPayload, responsePayload,
                    AiAnalysisLogService.STATUS_FAILED, ex.getMessage());
            if (isContextTooLong(ex.getResponseBodyAsString(StandardCharsets.UTF_8))) {
                throw new BizException(400, "AI整理输入内容超过模型上下文限制，请减少原始回执或关联文档后重试。");
            }
            throw new BizException(502, "ai api request failed: " + truncate(ex.getResponseBodyAsString(StandardCharsets.UTF_8), 1000));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            recordKnowledgeOrganizeLog(entity, setting, requestPayload, responsePayload,
                    AiAnalysisLogService.STATUS_FAILED, ex.getMessage());
            throw new BizException(502, "ai api request failed: " + ex.getMessage());
        }
    }

    private List<Double> callEmbedding(AiModelSetting setting, String input) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", setting.getModelName());
        body.put("input", input);
        JsonNode root;
        try {
            root = postAi(setting, "/embeddings", body);
        } catch (BizException ex) {
            if (isContextTooLong(ex.getMessage())) {
                throw new BizException(400, "向量化输入内容超过模型上下文限制，请缩短确认内容后再入库。");
            }
            if (isEmbeddingUnsupported(ex.getMessage())) {
                throw new BizException(400, "当前向量配置「" + setting.getSettingKey()
                        + " / " + setting.getModelName()
                        + "」不支持 Embeddings API。请在向量配置中选择 embedding 模型配置后再确认入库。");
            }
            throw ex;
        }
        JsonNode embedding = root.path("data").path(0).path("embedding");
        if (!embedding.isArray() || embedding.isEmpty()) {
            throw new BizException(502, "embedding response missing vector");
        }
        List<Double> result = new ArrayList<>(embedding.size());
        for (JsonNode item : embedding) {
            result.add(item.asDouble());
        }
        return result;
    }

    private JsonNode postAi(AiModelSetting setting, String path, Map<String, Object> body) {
        try {
            ResponseEntity<byte[]> response = restTemplate().exchange(
                    resolveEndpoint(setting, path),
                    HttpMethod.POST,
                    new HttpEntity<>(body, aiHeaders(setting)),
                    byte[].class);
            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new BizException(502, "ai api returned empty response");
            }
            return objectMapper.readTree(bytes);
        } catch (RestClientResponseException ex) {
            String bodyText = ex.getResponseBodyAsString(StandardCharsets.UTF_8);
            throw new BizException(502, "ai api request failed: " + truncate(bodyText, 1000));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(502, "ai api request failed: " + ex.getMessage());
        }
    }

    private String extractText(JsonNode root) {
        String outputText = text(root, "output_text");
        if (outputText != null) {
            return outputText;
        }
        JsonNode output = root.path("output");
        if (output.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    String text = text(part, "text");
                    if (text != null) {
                        appendLine(builder, text);
                    }
                }
            }
            if (!builder.isEmpty()) {
                return builder.toString().trim();
            }
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return text(choices.get(0).path("message"), "content");
        }
        return null;
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofMinutes(5))
                .build();
    }

    private HttpHeaders aiHeaders(AiModelSetting setting) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = trimToNull(setting.getApiKey());
        if (apiKey != null) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    private AiModelSetting resolveSetting(String settingKey, String fieldName) {
        String normalized;
        if ("embeddingSettingKey".equals(fieldName)) {
            normalized = trimToNull(settingKey);
            if (normalized == null) {
                throw new BizException(400, "请选择向量配置（Embedding 模型）");
            }
        } else {
            normalized = required(settingKey, fieldName);
        }
        AiModelSetting setting = aiModelSettingRepository.findBySettingKey(normalized)
                .orElseThrow(() -> new BizException(404, "ai setting not found: " + normalized));
        if (!Boolean.TRUE.equals(setting.getEnabledFlag())) {
            throw new BizException(400, "ai setting is disabled: " + normalized);
        }
        AiModelPurpose purpose = normalizePurpose(setting);
        if ("embeddingSettingKey".equals(fieldName) && purpose != AiModelPurpose.VECTOR) {
            throw new BizException(400, "请选择用途为“向量”的模型配置：" + normalized);
        }
        if ("aiSettingKey".equals(fieldName) && purpose != AiModelPurpose.LANGUAGE) {
            throw new BizException(400, "请选择用途为“语言”的模型配置：" + normalized);
        }
        if (trimToNull(setting.getBaseUrl()) == null) {
            throw new BizException(400, "ai setting baseUrl is required: " + normalized);
        }
        if (trimToNull(setting.getModelName()) == null) {
            throw new BizException(400, "ai setting modelName is required: " + normalized);
        }
        return setting;
    }

    private String resolveEndpoint(AiModelSetting setting, String path) {
        String baseUrl = setting.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }

    private List<String> splitVectorChunks(String content, AiModelSetting setting) {
        String normalized = required(content, "confirmedContent").replace("\r\n", "\n").replace('\r', '\n');
        int chunkSize = normalizeVectorChunkSize(setting.getVectorChunkSize());
        int overlap = normalizeVectorChunkOverlap(setting.getVectorChunkOverlap(), chunkSize);
        if (normalized.length() <= chunkSize) {
            return List.of(normalized);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            if (end < normalized.length()) {
                end = adjustChunkEnd(normalized, start, end, chunkSize);
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
            int nextStart = Math.max(end - overlap, start + 1);
            start = Math.min(nextStart, normalized.length());
        }
        return chunks.isEmpty() ? List.of(normalized) : chunks;
    }

    private int adjustChunkEnd(String content, int start, int end, int chunkSize) {
        int minEnd = start + Math.max(chunkSize / 2, 1);
        String[] separators = {"\n\n", "\n", "。", "！", "？", "；", ";", ".", "，", ",", " "};
        for (String separator : separators) {
            int index = content.lastIndexOf(separator, end - 1);
            if (index >= minEnd) {
                return Math.min(index + separator.length(), content.length());
            }
        }
        return end;
    }

    private int normalizeVectorChunkSize(Integer value) {
        int chunkSize = value == null ? 500 : value;
        return Math.max(100, Math.min(chunkSize, 8000));
    }

    private int normalizeVectorChunkOverlap(Integer value, int chunkSize) {
        int overlap = value == null ? 100 : value;
        return Math.max(0, Math.min(overlap, chunkSize - 1));
    }

    private String chunkVectorId(String vectorId, int chunkIndex) {
        return vectorId + "-chunk-" + String.format("%04d", chunkIndex);
    }

    private void deleteExistingVectors(ProjectKnowledgeItem entity) {
        String vectorId = trimToNull(entity.getVectorId());
        if (vectorId == null) {
            return;
        }
        try {
            vectorClient.delete(entity.getProjectCode(), vectorId);
        } catch (RuntimeException ignored) {
            // 兼容旧版本单向量记录，删除失败不影响后续覆盖新分片。
        }
        int chunkCount = entity.getVectorChunkCount() == null ? 0 : entity.getVectorChunkCount();
        for (int index = 1; index <= chunkCount; index++) {
            try {
                vectorClient.delete(entity.getProjectCode(), chunkVectorId(vectorId, index));
            } catch (RuntimeException ignored) {
                // 删除是清理动作，不能阻塞重新入库。
            }
        }
    }

    private void deleteInsertedVectorChunks(String projectCode, String vectorId, int chunkCount) {
        if (trimToNull(vectorId) == null || chunkCount < 1) {
            return;
        }
        for (int index = 1; index <= chunkCount; index++) {
            try {
                vectorClient.delete(projectCode, chunkVectorId(vectorId, index));
            } catch (RuntimeException ignored) {
                // 异常回滚清理尽力而为，保留原始错误给调用方。
            }
        }
    }

    private AiModelPurpose normalizePurpose(AiModelSetting setting) {
        if (setting.getModelPurpose() != null) {
            return setting.getModelPurpose();
        }
        return Boolean.TRUE.equals(setting.getSupportImageFlag()) ? AiModelPurpose.IMAGE : AiModelPurpose.LANGUAGE;
    }

    private String defaultEmbeddingSettingKey() {
        return aiModelSettingRepository.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabledFlag()))
                .filter(item -> normalizePurpose(item) == AiModelPurpose.VECTOR)
                .findFirst()
                .map(AiModelSetting::getSettingKey)
                .orElseThrow(() -> new BizException(400, "请先配置并启用用途为“向量”的模型配置"));
    }

    private Map<String, Object> buildVectorMetadata(ProjectKnowledgeItem entity,
                                                    String vectorId,
                                                    int chunkIndex,
                                                    int chunkTotal) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("knowledgeId", entity.getId());
        metadata.put("vectorId", vectorId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("chunkTotal", chunkTotal);
        metadata.put("projectCode", entity.getProjectCode());
        metadata.put("knowledgeType", entity.getKnowledgeType().name());
        metadata.put("title", entity.getTitle());
        metadata.put("simpleDesc", entity.getSimpleDesc());
        metadata.put("sourceRequirementNo", entity.getSourceRequirementNo());
        return metadata;
    }

    private String finalKnowledgeContent(ProjectKnowledgeItem entity) {
        String confirmed = trimToNull(entity.getConfirmedContent());
        if (confirmed != null) {
            return confirmed;
        }
        String organized = trimToNull(entity.getOrganizedContent());
        if (organized != null) {
            return organized;
        }
        String detail = trimToNull(entity.getDetailContent());
        if (detail != null) {
            return detail;
        }
        return trimToNull(entity.getSimpleDesc());
    }

    private String vectorizableKnowledgeContent(ProjectKnowledgeItem entity) {
        String confirmed = trimToNull(entity.getConfirmedContent());
        if (confirmed != null) {
            return confirmed;
        }
        String organized = trimToNull(entity.getOrganizedContent());
        if (organized != null) {
            return organized;
        }
        return trimToNull(entity.getDetailContent());
    }

    private void markVectorDirty(ProjectKnowledgeItem entity) {
        entity.setVectorDirtyFlag(true);
        if (entity.getVectorStatus() == ProjectKnowledgeVectorStatus.READY) {
            entity.setVectorStatus(ProjectKnowledgeVectorStatus.STALE);
        }
    }

    private ProjectKnowledgeItem findEntity(Long id) {
        if (id == null) {
            throw new BizException(400, "id is required");
        }
        return repository.findById(id)
                .orElseThrow(() -> new BizException(404, "project knowledge not found"));
    }

    private void assertNotOrganizing(ProjectKnowledgeItem entity) {
        if (entity.getStatus() == ProjectKnowledgeStatus.AI_ORGANIZING) {
            throw new BizException(400, "项目储备知识正在 AI 整理中，请稍后再操作");
        }
    }

    private ProjectKnowledgeResponse toResponse(ProjectKnowledgeItem entity) {
        return ProjectKnowledgeResponse.builder()
                .id(entity.getId())
                .projectCode(entity.getProjectCode())
                .knowledgeType(entity.getKnowledgeType())
                .title(entity.getTitle())
                .simpleDesc(entity.getSimpleDesc())
                .detailContent(entity.getDetailContent())
                .organizedContent(entity.getOrganizedContent())
                .confirmedContent(entity.getConfirmedContent())
                .sourceRequirementNo(entity.getSourceRequirementNo())
                .sourceSummary(entity.getSourceSummary())
                .aiSettingKey(entity.getAiSettingKey())
                .embeddingSettingKey(entity.getEmbeddingSettingKey())
                .documentIds(entity.getDocumentIds())
                .selectedSimilarKnowledgeId(entity.getSelectedSimilarKnowledgeId())
                .selectedSimilarScore(entity.getSelectedSimilarScore())
                .status(entity.getStatus())
                .vectorStatus(entity.getVectorStatus())
                .vectorDirtyFlag(entity.getVectorDirtyFlag())
                .vectorCollection(entity.getVectorCollection())
                .vectorId(entity.getVectorId())
                .vectorChunkCount(entity.getVectorChunkCount())
                .vectorFilePath(entity.getVectorFilePath())
                .vectorUpdatedAt(entity.getVectorUpdatedAt())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String knowledgeTypeLabel(ProjectKnowledgeType type) {
        if (type == ProjectKnowledgeType.COMMON_ISSUE) {
            return "项目常见问题";
        }
        if (type == ProjectKnowledgeType.PROCESS_GUIDE) {
            return "流程说明";
        }
        return type == null ? "-" : type.name();
    }

    private void recordKnowledgeOrganizeLog(ProjectKnowledgeItem entity,
                                            AiModelSetting setting,
                                            String requestPayload,
                                            String responsePayload,
                                            String status,
                                            String errorMessage) {
        aiAnalysisLogService.record(
                AiAnalysisLogService.SOURCE_PROJECT_KNOWLEDGE_ORGANIZE,
                knowledgeBusinessNo(entity),
                entity.getProjectCode(),
                setting.getSettingKey(),
                setting.getModelName(),
                status,
                requestPayload,
                responsePayload,
                errorMessage);
    }

    private String knowledgeBusinessNo(ProjectKnowledgeItem entity) {
        String sourceRequirementNo = trimToNull(entity.getSourceRequirementNo());
        if (sourceRequirementNo != null) {
            return sourceRequirementNo;
        }
        return entity.getId() == null ? "-" : "knowledge-" + entity.getId();
    }

    private String responsePayload(int statusCode, Map<String, String> headers, String bodyText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("statusCode", statusCode);
        payload.put("headers", headers);
        payload.put("body", bodyText);
        return jsonPayload(payload);
    }

    private String bodyText(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private String jsonPayload(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private boolean isEmbeddingUnsupported(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("embeddings api is not supported")
                || normalized.contains("not support embeddings")
                || normalized.contains("does not support embeddings")
                || normalized.contains("unsupported for embeddings");
    }

    private boolean isContextTooLong(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("input length exceeds the context length")
                || normalized.contains("context length")
                || normalized.contains("maximum context")
                || normalized.contains("too many tokens");
    }

    private String required(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BizException(400, fieldName + " is required");
        }
        return normalized;
    }

    private String trimCsvToNull(String value) {
        List<String> items = csvToList(value);
        return items.isEmpty() ? null : String.join(",", items);
    }

    private List<String> csvToList(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return List.of();
        }
        return Arrays.stream(normalized.split(","))
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToDash(String value) {
        return trimToNull(value) == null ? "-" : value.trim();
    }

    private void appendLine(StringBuilder builder, String value) {
        if (value == null) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
