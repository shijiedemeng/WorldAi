package com.aiapi.defect.service;

import com.aiapi.common.enums.DefectPushStatus;
import com.aiapi.common.enums.ProjectDocumentUsage;
import com.aiapi.common.enums.RequirementExecutionMode;
import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.common.exception.BizException;
import com.aiapi.defect.dto.AnalyzeDefectRequest;
import com.aiapi.defect.dto.DefectAnalysisResponse;
import com.aiapi.defect.dto.DefectAttachmentResponse;
import com.aiapi.defect.dto.DefectCommentResponse;
import com.aiapi.defect.dto.DefectRemoteQueryRequest;
import com.aiapi.defect.dto.DefectRecordResponse;
import com.aiapi.defect.dto.PushDefectToRequirementRequest;
import com.aiapi.defect.dto.RemoteDefectResponse;
import com.aiapi.defect.dto.RemoteDefectPageResponse;
import com.aiapi.defect.entity.DefectAnalysisRecord;
import com.aiapi.defect.entity.DefectComment;
import com.aiapi.defect.entity.DefectRecord;
import com.aiapi.defect.entity.DefectSyncAccount;
import com.aiapi.defect.entity.DefectSyncProject;
import com.aiapi.defect.integration.DefectPlatformBug;
import com.aiapi.defect.integration.DefectPlatformClient;
import com.aiapi.defect.integration.DefectPlatformComment;
import com.aiapi.defect.integration.DefectPlatformAttachment;
import com.aiapi.defect.integration.YunxiaoDefectPlatformClient;
import com.aiapi.defect.integration.ZentaoDefectPlatformClient;
import com.aiapi.defect.integration.DefectPlatformBugPage;
import com.aiapi.defect.repository.DefectAnalysisRecordRepository;
import com.aiapi.defect.repository.DefectCommentRepository;
import com.aiapi.defect.repository.DefectRecordRepository;
import com.aiapi.log.service.AiAnalysisLogService;
import com.aiapi.project.service.ProjectMarkdownService;
import com.aiapi.project.service.ProjectService;
import com.aiapi.requirement.dto.CreateRequirementRequest;
import com.aiapi.requirement.dto.RequirementResponse;
import com.aiapi.requirement.service.RequirementService;
import com.aiapi.system.entity.AiModelSetting;
import com.aiapi.system.repository.AiModelSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class DefectRecordService {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+");
    private static final String DEFECT_ANALYSIS_ROLE = """
            你是缺陷分析助手，负责把原始缺陷信息整理成可直接转需求的分析结果。
            要求：
            1. 输出中文纯文本，不要输出 HTML 标签、HTML 实体或 Markdown 代码块。
            2. 如果输入里出现 URL、地址、文档链接、接口链接或附件链接，要优先关注这些地址；如果当前模型环境可以直接访问，请主动阅读后再分析，不能访问时也要在结果里单独列出。
            3. 综合缺陷标题、状态、严重级别、负责人、创建人、描述、摘要、评论、附件和人工补充提示进行分析。
            4. 给出问题概述、影响范围、可能原因、建议处理方向和可拆分的需求方向。
            5. 结果尽量简洁，但要保留足够的上下文，便于后续推需求。
            """;

    private final DefectRecordRepository recordRepository;
    private final DefectCommentRepository commentRepository;
    private final DefectAnalysisRecordRepository analysisRepository;
    private final DefectSourceConfigService defectSourceConfigService;
    private final ZentaoDefectPlatformClient zentaoClient;
    private final YunxiaoDefectPlatformClient yunxiaoClient;
    private final ProjectService projectService;
    private final ProjectMarkdownService projectMarkdownService;
    private final RequirementService requirementService;
    private final AiModelSettingRepository aiModelSettingRepository;
    private final AiAnalysisLogService aiAnalysisLogService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final RestTemplate restTemplate = buildRestTemplate();

    @Transactional(readOnly = true)
    public RemoteDefectPageResponse viewRemoteDefects(String sourceCode, DefectRemoteQueryRequest request) {
        DefectSyncProject syncProject = defectSourceConfigService.findSourceProject(sourceCode);
        DefectSyncAccount account = defectSourceConfigService.findSourceAccount(sourceCode);
        if (!Boolean.TRUE.equals(account.getEnabledFlag())) {
            throw new BizException(400, "defect source is disabled");
        }
        DefectPlatformBugPage remotePage = resolveClient(account).fetchBugs(account, syncProject, request);
        List<RemoteDefectResponse> items = remotePage.getItems().stream()
                .map(item -> toRemoteResponse(syncProject, account, item, findPersistedRecordId(syncProject.getSyncCode(), item.getExternalDefectId())))
                .toList();
        return RemoteDefectPageResponse.builder()
                .items(items)
                .page(remotePage.getPage())
                .pageSize(remotePage.getPageSize())
                .total(remotePage.getTotal())
                .totalPages(remotePage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public RemoteDefectResponse viewRemoteDefectDetail(String sourceCode, String externalDefectId) {
        DefectSyncProject syncProject = defectSourceConfigService.findSourceProject(sourceCode);
        DefectSyncAccount account = defectSourceConfigService.findSourceAccount(sourceCode);
        if (!Boolean.TRUE.equals(account.getEnabledFlag())) {
            throw new BizException(400, "defect source is disabled");
        }
        DefectPlatformBug bug = resolveClient(account).fetchBug(account, syncProject, externalDefectId);
        return toRemoteResponse(syncProject, account, bug, findPersistedRecordId(syncProject.getSyncCode(), bug.getExternalDefectId()));
    }

    @Transactional(readOnly = true)
    public List<DefectAnalysisResponse> listAnalyses(String projectCode) {
        List<DefectAnalysisRecord> analyses = analysisRepository.findByProjectCodeOrderByUpdatedAtDesc(projectCode);
        Set<Long> recordIds = new LinkedHashSet<>();
        for (DefectAnalysisRecord analysis : analyses) {
            if (analysis.getDefectRecordId() != null) {
                recordIds.add(analysis.getDefectRecordId());
            }
        }
        Map<Long, DefectRecord> recordMap = new HashMap<>();
        if (!recordIds.isEmpty()) {
            recordRepository.findAllById(recordIds).forEach(record -> recordMap.put(record.getId(), record));
        }
        return analyses.stream().map(item -> toResponse(item, recordMap.get(item.getDefectRecordId()))).toList();
    }

    public DefectAnalysisResponse analyze(AnalyzeDefectRequest request) {
        AnalysisStart start = Objects.requireNonNull(transactionTemplate.execute(status -> beginAnalysis(request)));
        if (!start.started()) {
            return toResponse(start.analysis(), start.record());
        }
        try {
            String result = buildAnalysisResult(start.record(), request);
            return Objects.requireNonNull(transactionTemplate.execute(status ->
                    finishAnalysis(start.analysis().getId(), start.record().getId(), result)));
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status -> markAnalysisFailed(start.analysis().getId()));
            throw ex;
        }
    }

    private AnalysisStart beginAnalysis(AnalyzeDefectRequest request) {
        String analysisNo = requiredText(request.getAnalysisNo(), "analysisNo");
        projectService.findEntity(request.getProjectCode());
        DefectRecord record = request.getDefectRecordId() == null
                ? persistMinimalRecord(request)
                : findRecord(request.getDefectRecordId());
        if (!record.getProjectCode().equals(request.getProjectCode())) {
            throw new BizException(400, "defect record does not belong to project");
        }

        Optional<DefectAnalysisRecord> analysisByNo = analysisRepository.findByAnalysisNo(analysisNo);
        if (analysisByNo.isPresent()) {
            DefectAnalysisRecord existing = analysisByNo.get();
            if (!Objects.equals(existing.getDefectRecordId(), record.getId())) {
                throw new BizException(400, "analysisNo already belongs to another defect");
            }
            if (isReusableAnalysis(existing)) {
                return new AnalysisStart(record, existing, false);
            }
        }

        Optional<DefectAnalysisRecord> existingAnalysis = analysisRepository.findByDefectRecordIdOrderByUpdatedAtDesc(record.getId()).stream()
                .filter(this::isReusableAnalysis)
                .findFirst();
        if (existingAnalysis.isPresent()) {
            return new AnalysisStart(record, existingAnalysis.get(), false);
        }

        DefectAnalysisRecord entity = analysisByNo.orElseGet(DefectAnalysisRecord::new);
        entity.setAnalysisNo(analysisNo);
        entity.setProjectCode(request.getProjectCode().trim());
        entity.setDefectRecordId(record.getId());
        entity.setAiSettingKey(trimToNull(request.getAiSettingKey()));
        entity.setAgentScope(cleanText(request.getAgentScope()));
        entity.setPromptText(cleanText(request.getPromptText()));
        entity.setEditedSummary(cleanText(request.getEditedSummary()));
        entity.setAnalysisResult(null);
        entity.setNextRequirementNo(null);
        entity.setPushedAt(null);
        entity.setPushStatus(DefectPushStatus.ANALYZING);
        return new AnalysisStart(record, analysisRepository.save(entity), true);
    }

    private boolean isReusableAnalysis(DefectAnalysisRecord analysis) {
        return analysis.getPushStatus() == DefectPushStatus.ANALYZING
                || trimToNull(analysis.getAnalysisResult()) != null
                || trimToNull(analysis.getNextRequirementNo()) != null;
    }

    private DefectAnalysisResponse finishAnalysis(Long analysisId, Long defectRecordId, String result) {
        DefectAnalysisRecord entity = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new BizException(404, "defect analysis not found"));
        entity.setAnalysisResult(result);
        entity.setPushStatus(DefectPushStatus.ANALYZED);
        DefectRecord record = findRecord(defectRecordId);
        return toResponse(analysisRepository.save(entity), record);
    }

    private void markAnalysisFailed(Long analysisId) {
        analysisRepository.findById(analysisId).ifPresent(entity -> {
            if (entity.getPushStatus() == DefectPushStatus.ANALYZING) {
                entity.setPushStatus(DefectPushStatus.DRAFT);
                analysisRepository.save(entity);
            }
        });
    }

    @Transactional
    public DefectAnalysisResponse pushToRequirement(String analysisNo, PushDefectToRequirementRequest request) {
        DefectAnalysisRecord analysis = analysisRepository.findByAnalysisNo(analysisNo)
                .orElseThrow(() -> new BizException(404, "defect analysis not found"));
        DefectRecord record = findRecord(analysis.getDefectRecordId());
        if (analysis.getPushStatus() == DefectPushStatus.ANALYZING) {
            throw new BizException(400, "defect analysis is running, cannot push to requirement");
        }
        if (analysis.getPushStatus() == DefectPushStatus.PUSHED || trimToNull(analysis.getNextRequirementNo()) != null) {
            throw new BizException(400, "defect analysis has already been pushed to requirement");
        }
        boolean defectAlreadyPushed = analysisRepository.findByDefectRecordIdOrderByUpdatedAtDesc(record.getId()).stream()
                .anyMatch(item -> !item.getId().equals(analysis.getId())
                        && (item.getPushStatus() == DefectPushStatus.PUSHED || trimToNull(item.getNextRequirementNo()) != null));
        if (defectAlreadyPushed) {
            throw new BizException(400, "defect has already been pushed to requirement");
        }

        CreateRequirementRequest createRequest = new CreateRequirementRequest();
        createRequest.setRequirementNo(resolveRequirementNo(request, record));
        createRequest.setProjectCode(analysis.getProjectCode());
        createRequest.setTitle(defaultText(cleanText(request.getTitle()), "修复缺陷：" + defaultText(cleanText(record.getTitle()), cleanText(record.getExternalDefectKey()), cleanText(record.getExternalDefectId()), "未命名缺陷")));
        createRequest.setRequirementDesc(buildRequirementDesc(record, analysis));
        createRequest.setPriority(cleanText(record.getSeverity()));
        createRequest.setStatus(RequirementStatus.PENDING);
        createRequest.setSource("DEFECT_SYNC:" + record.getExternalDefectId());
        createRequest.setMainAgentCode(trimToNull(request.getMainAgentCode()));
        createRequest.setExecutionSteps(cleanText(request.getExecutionSteps()));
        createRequest.setExecutionMode(request.getExecutionMode() == null ? RequirementExecutionMode.NORMAL : request.getExecutionMode());
        RequirementResponse requirement = requirementService.createMaster(createRequest);

        analysis.setNextRequirementNo(requirement.getRequirementNo());
        analysis.setPushStatus(DefectPushStatus.PUSHED);
        analysis.setPushedAt(LocalDateTime.now());
        return toResponse(analysisRepository.save(analysis), record);
    }

    private String resolveRequirementNo(PushDefectToRequirementRequest request, DefectRecord record) {
        String requested = trimToNull(request.getRequirementNo());
        if (requested != null) {
            return requested;
        }
        String seed = defaultText(cleanText(record.getExternalDefectKey()), cleanText(record.getExternalDefectId()), String.valueOf(record.getId()));
        String base = ("BUG-" + seed)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "BUG-" + record.getId();
        }
        if (base.length() > 64) {
            base = base.substring(0, 64);
        }
        for (int index = 1; index < 1000; index++) {
            String suffix = index == 1 ? "" : "-" + index;
            String prefix = base.length() + suffix.length() > 64
                    ? base.substring(0, 64 - suffix.length())
                    : base;
            String candidate = prefix + suffix;
            if (requirementService.findNullable(candidate) == null) {
                return candidate;
            }
        }
        throw new BizException(400, "cannot allocate requirementNo for defect");
    }

    private DefectRecord persistMinimalRecord(AnalyzeDefectRequest request) {
        String sourceCode = trimToNull(request.getSourceCode());
        String externalDefectId = trimToNull(request.getExternalDefectId());
        if (sourceCode == null || externalDefectId == null) {
            throw new BizException(400, "sourceCode and externalDefectId are required when record is not persisted");
        }
        DefectSyncProject syncProject = defectSourceConfigService.findSourceProject(sourceCode);
        DefectSyncAccount account = defectSourceConfigService.findSourceAccount(sourceCode);
        DefectRecord entity = recordRepository.findBySyncCodeAndExternalDefectId(sourceCode, externalDefectId).orElseGet(DefectRecord::new);
        entity.setSyncCode(sourceCode);
        entity.setProjectCode(request.getProjectCode().trim());
        entity.setAccountCode(account.getAccountCode());
        entity.setPlatformType(account.getPlatformType());
        entity.setExternalDefectId(externalDefectId);
        entity.setExternalDefectKey(cleanText(request.getExternalDefectKey()));
        entity.setTitle(requiredCleanText(request.getTitle(), "title"));
        entity.setSeverity(cleanText(request.getSeverity()));
        entity.setDefectStatus(cleanText(request.getDefectStatus()));
        entity.setDefectType(cleanText(request.getDefectType()));
        entity.setAssignedTo(cleanText(request.getAssignedTo()));
        entity.setReporterName(cleanText(request.getReporterName()));
        entity.setOpenedAt(null);
        entity.setUpdatedAtRemote(null);
        entity.setHasImageFlag(Boolean.FALSE);
        entity.setTags(null);
        entity.setSummary(cleanText(request.getSummary()));
        entity.setDescriptionText(cleanText(request.getDescriptionText()));
        entity.setRawPayload("""
                {"sourceCode":"%s","externalProjectKey":"%s","storedAt":"minimal"}
                """.formatted(sourceCode, syncProject.getExternalProjectKey()));
        return recordRepository.save(entity);
    }

    private String buildAnalysisResult(DefectRecord record, AnalyzeDefectRequest request) {
        AiModelSetting setting = resolveAiSetting(trimToNull(request.getAiSettingKey()));
        String prompt = buildAnalysisPrompt(record, request, setting);
        return callAiForAnalysis(record, request, setting, prompt);
    }

    private String buildRequirementDesc(DefectRecord record, DefectAnalysisRecord analysis) {
        return """
                来源缺陷：%s
                外部编号：%s
                当前状态：%s

                缺陷描述：
                %s

                AI 分析结果：
                %s
                """.formatted(
                cleanText(record.getTitle()),
                cleanText(record.getExternalDefectId()),
                cleanText(record.getDefectStatus()),
                defaultText(cleanText(record.getDescriptionText(), record.getSummary())),
                defaultText(analysis.getAnalysisResult()));
    }

    private AiModelSetting resolveAiSetting(String aiSettingKey) {
        String normalized = trimToNull(aiSettingKey);
        if (normalized != null) {
            AiModelSetting setting = aiModelSettingRepository.findBySettingKey(normalized)
                    .orElseThrow(() -> new BizException(404, "ai setting not found"));
            if (!Boolean.TRUE.equals(setting.getEnabledFlag())) {
                throw new BizException(400, "ai setting is disabled");
            }
            return setting;
        }
        return aiModelSettingRepository.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabledFlag()))
                .max(java.util.Comparator.comparing(AiModelSetting::getUpdatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .orElseThrow(() -> new BizException(400, "no enabled ai setting"));
    }

    private String buildAnalysisPrompt(DefectRecord record, AnalyzeDefectRequest request, AiModelSetting setting) {
        String urls = collectUrls(
                record.getTitle(),
                record.getSummary(),
                record.getDescriptionText(),
                record.getRawPayload(),
                request.getEditedSummary(),
                request.getPromptText()
        );
        return """
                请基于下面的缺陷信息输出分析结果。

                模型配置：
                - provider: %s
                - model: %s

                项目关联 Markdown 文档：
                %s

                缺陷基础信息：
                - 标题：%s
                - 状态：%s
                - 严重级别：%s
                - 类型：%s
                - 负责人：%s
                - 创建人：%s
                - 标签：%s
                - 外部编号：%s
                - 外部键：%s
                - 当前 Agent：%s

                缺陷摘要：
                %s

                缺陷描述：
                %s

                人工摘要：
                %s

                人工提示：
                %s

                发现的地址：
                %s
                """.formatted(
                defaultText(cleanText(setting.getProviderName())),
                defaultText(cleanText(setting.getModelName())),
                defaultText(cleanText(projectMarkdownService.buildAiContext(
                        request.getProjectCode(),
                        ProjectDocumentUsage.DEFECT_AI_ANALYSIS)), "-"),
                defaultText(cleanText(record.getTitle())),
                defaultText(cleanText(record.getDefectStatus())),
                defaultText(cleanText(record.getSeverity())),
                defaultText(cleanText(record.getDefectType())),
                defaultText(cleanText(record.getAssignedTo())),
                defaultText(cleanText(record.getReporterName())),
                defaultText(cleanText(record.getTags())),
                defaultText(cleanText(record.getExternalDefectId())),
                defaultText(cleanText(record.getExternalDefectKey())),
                defaultText(cleanText(request.getAgentScope())),
                defaultText(cleanText(record.getSummary())),
                defaultText(cleanText(record.getDescriptionText())),
                defaultText(cleanText(request.getEditedSummary()), "-"),
                defaultText(cleanText(request.getPromptText()), "可继续人工编辑后再推送到需求列表。"),
                defaultText(urls, "未发现")
        );
    }

    private String callAiForAnalysis(DefectRecord record, AnalyzeDefectRequest request, AiModelSetting setting, String prompt) {
        String baseUrl = trimToNull(setting.getBaseUrl());
        if (baseUrl == null) {
            throw new BizException(400, "ai setting baseUrl is required");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String apiKey = trimToNull(setting.getApiKey());
        if (apiKey != null) {
            headers.setBearerAuth(apiKey);
        }
        String requestUrl = responsesUrl(baseUrl);
        List<AiRequestAttempt> attempts = buildAnalysisAttempts(requestUrl, setting, prompt);
        List<Map<String, Object>> requestAttempts = new ArrayList<>();
        List<Map<String, Object>> responseAttempts = new ArrayList<>();
        String lastError = null;
        for (AiRequestAttempt attempt : attempts) {
            requestAttempts.add(requestAttemptPayload(attempt));
            try {
                ResponseEntity<String> response = postForString(attempt.url(), attempt.body(), headers);
                responseAttempts.add(responsePayload(attempt.mode(), response));
                String result = normalizeAssistantText(extractAssistantContent(response.getBody()));
                aiAnalysisLogService.record(
                        AiAnalysisLogService.SOURCE_DEFECT_AI_ANALYSIS,
                        request.getAnalysisNo(),
                        request.getProjectCode(),
                        setting.getSettingKey(),
                        setting.getModelName(),
                        AiAnalysisLogService.STATUS_SUCCESS,
                        attemptsPayload(requestAttempts),
                        attemptsPayload(responseAttempts),
                        null);
                return result;
            } catch (Exception exc) {
                lastError = exc.getMessage();
                responseAttempts.add(errorResponsePayload(attempt.mode(), exc));
            }
        }
        aiAnalysisLogService.record(
                AiAnalysisLogService.SOURCE_DEFECT_AI_ANALYSIS,
                request.getAnalysisNo(),
                request.getProjectCode(),
                setting.getSettingKey(),
                setting.getModelName(),
                AiAnalysisLogService.STATUS_FALLBACK,
                attemptsPayload(requestAttempts),
                attemptsPayload(responseAttempts),
                "AI response cannot be used, fallback analysis was generated: "
                        + defaultText(lastError, "ai response missing assistant content"));
        return buildFallbackAnalysis(record, request, lastError);
    }

    private String buildFallbackAnalysis(DefectRecord record, AnalyzeDefectRequest request, String reason) {
        String urls = collectUrls(
                record.getTitle(),
                record.getSummary(),
                record.getDescriptionText(),
                record.getRawPayload(),
                request.getEditedSummary(),
                request.getPromptText()
        );
        return """
                AI 分析兜底结果
                兜底原因：%s

                问题概述：
                缺陷「%s」当前状态为「%s」，严重级别「%s」。现有描述和评论表明，问题集中在页面字段展示、数据来源或附件截图所指向的差异，需要先按原始缺陷链接和截图确认实际现象。

                影响范围：
                - 缺陷编号：%s
                - 外部编号：%s
                - 负责人：%s
                - 创建人：%s
                - 当前 Agent：%s

                可能原因：
                1. 页面展示字段名称、字段映射或字段取值与源数据不一致。
                2. 接口返回、缓存数据或环境更新后页面渲染结果不同步。
                3. 附件截图和最新评论里提到的现象没有被统一归纳，导致处理范围不清晰。

                建议处理方向：
                1. 打开原始缺陷链接、附件图片和评论中的接口地址，确认当前环境实际展示。
                2. 对比页面展示、接口返回和缺陷截图，整理字段差异、缺失位置和复现步骤。
                3. 将确认后的问题概述、影响范围、证据链接和验收点再推送到需求列表。

                可拆分的需求方向：
                1. 复现确认与证据整理：确认页面、截图、评论和接口地址中的实际异常。
                2. 字段来源核查：核对目标字段在源数据、接口返回和页面展示之间的对应关系。
                3. 回归验证说明：基于原始缺陷链接和相近数据补充验证范围与通过标准。

                发现的地址：
                %s
                """.formatted(
                defaultText(cleanText(reason), "AI 模型返回了 HTTP 200，但没有可用的 assistant 内容，系统生成基础分析。"),
                defaultText(cleanText(record.getTitle()), "-"),
                defaultText(cleanText(record.getDefectStatus()), "-"),
                defaultText(cleanText(record.getSeverity()), "-"),
                defaultText(cleanText(record.getExternalDefectKey()), "-"),
                defaultText(cleanText(record.getExternalDefectId()), "-"),
                defaultText(cleanText(record.getAssignedTo()), "-"),
                defaultText(cleanText(record.getReporterName()), "-"),
                defaultText(cleanText(request.getAgentScope()), "-"),
                defaultText(urls, "未发现"));
    }

    private String composeSystemPrompt(AiModelSetting setting) {
        String template = trimToNull(setting.getPromptTemplate());
        if (template == null) {
            return DEFECT_ANALYSIS_ROLE;
        }
        return template + "\n\n" + DEFECT_ANALYSIS_ROLE;
    }

    private String extractAssistantContent(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(defaultText(raw, "{}"));
        JsonNode message = root.path("choices").path(0).path("message");
        String content = readContentText(message.get("content"));
        if (content == null) {
            content = readContentText(message.get("output_text"));
        }
        if (content == null) {
            content = readContentText(message.get("text"));
        }
        if (content == null) {
            content = readContentText(root.get("output_text"));
        }
        if (content == null) {
            content = readContentText(root.get("output"));
        }
        if (content == null) {
            throw new BizException(502, "ai response missing assistant content, raw response: " + preview(raw));
        }
        return content;
    }

    private String readContentText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return trimToNull(node.asText());
        }
        if (node.isArray()) {
            List<String> parts = new java.util.ArrayList<>();
            for (JsonNode item : node) {
                String text = readContentText(item);
                if (text != null) {
                    parts.add(text);
                }
            }
            return parts.isEmpty() ? null : String.join("\n", parts);
        }
        if (node.isObject()) {
            String text = readContentText(node.get("text"));
            if (text != null) {
                return text;
            }
            text = readContentText(node.get("content"));
            if (text != null) {
                return text;
            }
            return readContentText(node.get("value"));
        }
        return null;
    }

    private String normalizeAssistantText(String content) {
        String text = trimToNull(content);
        if (text == null) {
            throw new BizException(502, "ai response content is empty");
        }
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z0-9_-]*", "").replaceFirst("```$", "").trim();
        }
        return text;
    }

    private String collectUrls(String... values) {
        Set<String> urls = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized == null) {
                continue;
            }
            Matcher matcher = URL_PATTERN.matcher(HtmlUtils.htmlUnescape(normalized));
            while (matcher.find()) {
                urls.add(matcher.group());
            }
        }
        return urls.isEmpty() ? null : String.join("\n", urls);
    }

    private String cleanText(String value) {
        return sanitizeText(value);
    }

    private String cleanText(String primary, String fallback) {
        String text = sanitizeText(primary);
        if (text != null) {
            return text;
        }
        return sanitizeText(fallback);
    }

    private String requiredCleanText(String value, String fieldName) {
        String result = cleanText(value);
        if (result == null) {
            throw new BizException(400, fieldName + " is required");
        }
        return result;
    }

    private String sanitizeText(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String text = HtmlUtils.htmlUnescape(normalized)
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)<li[^>]*>", "- ")
                .replaceAll("(?i)<[^>]+>", " ")
                .replaceAll("[\\t\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return text.isEmpty() ? null : text;
    }

    private List<AiRequestAttempt> buildAnalysisAttempts(String requestUrl, AiModelSetting setting, String prompt) {
        Map<String, Object> responseBody = baseResponsesBody(setting, prompt, composeSystemPrompt(setting), false);
        applyResponsesOptions(responseBody, setting, 4096);
        return List.of(new AiRequestAttempt("responses", requestUrl, responseBody));
    }

    private Map<String, Object> baseResponsesBody(AiModelSetting setting, String prompt, String instructions, boolean jsonOutput) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", setting.getModelName());
        body.put("instructions", instructions);
        body.put("input", List.of(
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "input_text", "text", prompt)
                        )
                )
        ));
        body.put("text", Map.of("format", Map.of("type", jsonOutput ? "json_object" : "text")));
        return body;
    }

    private void applyResponsesOptions(Map<String, Object> body, AiModelSetting setting, int maxOutputTokens) {
        String modelName = defaultText(setting.getModelName()).toLowerCase(Locale.ROOT);
        if (isReasoningModel(modelName)) {
            body.put("reasoning", Map.of("effort", "low"));
        }
        body.put("max_output_tokens", maxOutputTokens);
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }

    private boolean isReasoningModel(String modelName) {
        return modelName.startsWith("gpt-5")
                || modelName.startsWith("o1")
                || modelName.startsWith("o3")
                || modelName.startsWith("o4");
    }

    private String responsesUrl(String baseUrl) {
        String normalized = baseUrl.replaceAll("/+$", "");
        return normalized.endsWith("/responses") ? normalized : normalized + "/responses";
    }

    private Map<String, Object> requestAttemptPayload(AiRequestAttempt attempt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", attempt.mode());
        payload.put("url", attempt.url());
        payload.put("body", attempt.body());
        return payload;
    }

    private String attemptsPayload(List<Map<String, Object>> attempts) {
        return safeJson(Map.of("attempts", attempts));
    }

    private Map<String, Object> responsePayload(String mode, ResponseEntity<String> response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", mode);
        payload.put("statusCode", response.getStatusCode().value());
        payload.put("headers", response.getHeaders().toSingleValueMap());
        payload.put("body", response.getBody());
        return payload;
    }

    private ResponseEntity<String> postForString(String url, Object body, HttpHeaders headers) {
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
        return ResponseEntity
                .status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(decodeResponseBody(response.getBody(), response.getHeaders()));
    }

    private String decodeResponseBody(byte[] body, HttpHeaders headers) {
        if (body == null) {
            return null;
        }
        MediaType contentType = headers.getContentType();
        Charset charset = contentType == null || contentType.getCharset() == null
                ? StandardCharsets.UTF_8
                : contentType.getCharset();
        return new String(body, charset);
    }

    private Map<String, Object> errorResponsePayload(String mode, org.springframework.web.client.HttpStatusCodeException exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", mode);
        payload.put("statusCode", exception.getStatusCode().value());
        payload.put("headers", exception.getResponseHeaders() == null ? Map.of() : exception.getResponseHeaders().toSingleValueMap());
        payload.put("body", exception.getResponseBodyAsString());
        return payload;
    }

    private Map<String, Object> errorResponsePayload(String mode, Exception exception) {
        if (exception instanceof org.springframework.web.client.HttpStatusCodeException httpException) {
            return errorResponsePayload(mode, httpException);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", mode);
        payload.put("error", exception.getClass().getSimpleName());
        payload.put("message", exception.getMessage());
        return payload;
    }

    private String preview(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "-";
        }
        return normalized.length() > 2000 ? normalized.substring(0, 2000) + "...(truncated)" : normalized;
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private DefectPlatformClient resolveClient(DefectSyncAccount account) {
        return switch (account.getPlatformType()) {
            case ZENTAO -> zentaoClient;
            case YUNXIAO -> yunxiaoClient;
        };
    }

    private Long findPersistedRecordId(String sourceCode, String externalDefectId) {
        return recordRepository.findBySyncCodeAndExternalDefectId(sourceCode, externalDefectId)
                .map(DefectRecord::getId)
                .orElse(null);
    }

    private DefectRecord findRecord(Long defectRecordId) {
        return recordRepository.findById(defectRecordId)
                .orElseThrow(() -> new BizException(404, "defect record not found"));
    }

    private RemoteDefectResponse toRemoteResponse(DefectSyncProject syncProject,
                                                  DefectSyncAccount account,
                                                  DefectPlatformBug bug,
                                                  Long persistedRecordId) {
        return RemoteDefectResponse.builder()
                .sourceCode(syncProject.getSyncCode())
                .projectCode(syncProject.getProjectCode())
                .accountCode(account.getAccountCode())
                .platformType(account.getPlatformType())
                .persistedRecordId(persistedRecordId)
                .externalDefectId(cleanText(bug.getExternalDefectId()))
                .externalDefectKey(cleanText(bug.getExternalDefectKey()))
                .title(defaultText(cleanText(bug.getTitle()), cleanText(bug.getExternalDefectKey()), cleanText(bug.getExternalDefectId()), "未命名缺陷"))
                .severity(cleanText(bug.getSeverity()))
                .defectStatusId(bug.getDefectStatusId())
                .defectStatus(cleanText(bug.getDefectStatus()))
                .defectType(cleanText(bug.getDefectType()))
                .assignedTo(cleanText(bug.getAssignedTo()))
                .reporterName(cleanText(bug.getReporterName()))
                .openedAt(bug.getOpenedAt())
                .updatedAtRemote(bug.getUpdatedAtRemote())
                .hasImageFlag(Boolean.TRUE.equals(bug.getHasImageFlag()))
                .tags(cleanText(bug.getTags()))
                .summary(cleanText(bug.getSummary()))
                .descriptionText(cleanText(bug.getDescriptionText()))
                .comments((bug.getComments() == null ? List.<DefectPlatformComment>of() : bug.getComments()).stream()
                        .map(this::toResponse)
                        .toList())
                .attachments((bug.getAttachments() == null ? List.<DefectPlatformAttachment>of() : bug.getAttachments()).stream()
                        .map(this::toResponse)
                        .toList())
                .build();
    }

    private DefectRecordResponse toResponse(DefectRecord entity, List<DefectComment> comments) {
        return DefectRecordResponse.builder()
                .id(entity.getId())
                .syncCode(entity.getSyncCode())
                .projectCode(entity.getProjectCode())
                .accountCode(entity.getAccountCode())
                .platformType(entity.getPlatformType())
                .externalDefectId(cleanText(entity.getExternalDefectId()))
                .externalDefectKey(cleanText(entity.getExternalDefectKey()))
                .title(cleanText(entity.getTitle()))
                .severity(cleanText(entity.getSeverity()))
                .defectStatus(cleanText(entity.getDefectStatus()))
                .defectType(cleanText(entity.getDefectType()))
                .assignedTo(cleanText(entity.getAssignedTo()))
                .reporterName(cleanText(entity.getReporterName()))
                .openedAt(entity.getOpenedAt())
                .updatedAtRemote(entity.getUpdatedAtRemote())
                .hasImageFlag(entity.getHasImageFlag())
                .tags(cleanText(entity.getTags()))
                .summary(cleanText(entity.getSummary()))
                .descriptionText(cleanText(entity.getDescriptionText()))
                .rawPayload(entity.getRawPayload())
                .comments(comments.stream().map(this::toResponse).toList())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private DefectCommentResponse toResponse(DefectComment entity) {
        return DefectCommentResponse.builder()
                .id(entity.getId())
                .externalCommentId(entity.getExternalCommentId())
                .authorName(cleanText(entity.getAuthorName()))
                .commentContent(cleanText(entity.getCommentContent()))
                .commentedAt(entity.getCommentedAt())
                .build();
    }

    private DefectCommentResponse toResponse(DefectPlatformComment entity) {
        return DefectCommentResponse.builder()
                .id(null)
                .externalCommentId(entity.getExternalCommentId())
                .authorName(cleanText(entity.getAuthorName()))
                .commentContent(cleanText(entity.getCommentContent()))
                .commentedAt(entity.getCommentedAt())
                .build();
    }

    private DefectAttachmentResponse toResponse(DefectPlatformAttachment entity) {
        return DefectAttachmentResponse.builder()
                .externalAttachmentId(entity.getExternalAttachmentId())
                .fileId(entity.getFileId())
                .fileName(cleanText(entity.getFileName()))
                .suffix(entity.getSuffix())
                .size(entity.getSize())
                .url(entity.getUrl())
                .creatorName(cleanText(entity.getCreatorName()))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private DefectAnalysisResponse toResponse(DefectAnalysisRecord entity) {
        DefectRecord record = entity.getDefectRecordId() == null
                ? null
                : recordRepository.findById(entity.getDefectRecordId()).orElse(null);
        return toResponse(entity, record);
    }

    private DefectAnalysisResponse toResponse(DefectAnalysisRecord entity, DefectRecord record) {
        return DefectAnalysisResponse.builder()
                .id(entity.getId())
                .analysisNo(entity.getAnalysisNo())
                .projectCode(entity.getProjectCode())
                .defectRecordId(entity.getDefectRecordId())
                .sourceCode(record == null ? null : record.getSyncCode())
                .externalDefectId(record == null ? null : cleanText(record.getExternalDefectId()))
                .externalDefectKey(record == null ? null : cleanText(record.getExternalDefectKey()))
                .title(record == null ? null : cleanText(record.getTitle()))
                .aiSettingKey(entity.getAiSettingKey())
                .agentScope(entity.getAgentScope())
                .promptText(entity.getPromptText())
                .editedSummary(entity.getEditedSummary())
                .analysisResult(entity.getAnalysisResult())
                .nextRequirementNo(entity.getNextRequirementNo())
                .pushStatus(entity.getPushStatus())
                .pushedAt(entity.getPushedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String defaultText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "-";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requiredText(String value, String fieldName) {
        String result = trimToNull(value);
        if (result == null) {
            throw new BizException(400, fieldName + " is required");
        }
        return result;
    }

    private record AnalysisStart(DefectRecord record, DefectAnalysisRecord analysis, boolean started) {
    }

    private record AiRequestAttempt(String mode, String url, Map<String, Object> body) {
    }
}
