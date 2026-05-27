package com.aiapi.requirement.service;

import com.aiapi.agent.entity.AgentInfo;
import com.aiapi.agent.repository.AgentInfoRepository;
import com.aiapi.common.enums.AgentStatus;
import com.aiapi.common.enums.ProjectDocumentUsage;
import com.aiapi.common.enums.RequirementExecutionMode;
import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.common.enums.RequirementType;
import com.aiapi.common.exception.BizException;
import com.aiapi.log.service.AiAnalysisLogService;
import com.aiapi.project.service.ProjectMarkdownService;
import com.aiapi.requirement.dto.AnalyzeRequirementRequest;
import com.aiapi.requirement.dto.CreateRequirementRequest;
import com.aiapi.requirement.dto.RequirementResponse;
import com.aiapi.requirement.dto.SaveRequirementWorkflowRequest;
import com.aiapi.requirement.entity.RequirementInfo;
import com.aiapi.requirement.repository.RequirementInfoRepository;
import com.aiapi.requirement.repository.RequirementModuleInfoRepository;
import com.aiapi.system.entity.AiModelSetting;
import com.aiapi.system.repository.AiModelSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class RequirementAiAnalysisService {

    private static final int MAX_MODULES = 12;
    private static final boolean TEMP_LOG_AI_REQUEST_ONLY = false;
    private static final Path TEMP_AI_REQUEST_LOG_DIR = Path.of("ai-debug");
    private static final DateTimeFormatter TEMP_AI_REQUEST_LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final RequirementService requirementService;
    private final RequirementAiAnalysisAsyncService requirementAiAnalysisAsyncService;
    private final RequirementInfoRepository requirementInfoRepository;
    private final RequirementModuleInfoRepository requirementModuleInfoRepository;
    private final AgentInfoRepository agentInfoRepository;
    private final AiModelSettingRepository aiModelSettingRepository;
    private final AiAnalysisLogService aiAnalysisLogService;
    private final ProjectMarkdownService projectMarkdownService;
    private final RequirementWorkflowService requirementWorkflowService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = buildRestTemplate();

    public RequirementResponse submitAnalysis(String requirementNo, AnalyzeRequirementRequest request) {
        RequirementInfo master = requirementService.findEntity(requirementNo);
        if (master.getRequirementType() != RequirementType.MASTER) {
            throw new BizException(400, "only MASTER requirement can be analyzed");
        }
        if (master.getStatus() == RequirementStatus.ANALYZING) {
            throw new BizException(400, "requirement is already analyzing");
        }
        if (requirementModuleInfoRepository.countByParentRequirementNo(master.getRequirementNo()) > 0) {
            throw new BizException(400, "child requirements already exist");
        }
        List<AgentInfo> agents = findAvailableAgents(master.getProjectCode());
        if (agents.isEmpty()) {
            throw new BizException(400, "project has no available agents");
        }
        resolveAiSetting(request == null ? null : request.getAiSettingKey());
        master.setStatus(RequirementStatus.ANALYZING);
        requirementInfoRepository.save(master);
        try {
            requirementAiAnalysisAsyncService.submit(requirementNo, request);
        } catch (RuntimeException exc) {
            requirementInfoRepository.findByRequirementNo(requirementNo).ifPresent(entity -> {
                if (entity.getStatus() == RequirementStatus.ANALYZING) {
                    entity.setStatus(RequirementStatus.PENDING);
                    requirementInfoRepository.save(entity);
                }
            });
            throw exc;
        }
        return requirementService.get(requirementNo);
    }

    @Transactional
    public List<RequirementResponse> executeAnalysis(String requirementNo, AnalyzeRequirementRequest request) {
        RequirementInfo master = requirementService.findEntity(requirementNo);
        if (master.getRequirementType() != RequirementType.MASTER) {
            throw new BizException(400, "only MASTER requirement can be analyzed");
        }
        if (master.getStatus() != RequirementStatus.ANALYZING) {
            throw new BizException(400, "requirement is not in analyzing state");
        }
        if (requirementModuleInfoRepository.countByParentRequirementNo(master.getRequirementNo()) > 0) {
            throw new BizException(400, "child requirements already exist");
        }
        List<AgentInfo> agents = findAvailableAgents(master.getProjectCode());
        if (agents.isEmpty()) {
            throw new BizException(400, "project has no available agents");
        }

        AiModelSetting setting = resolveAiSetting(request == null ? null : request.getAiSettingKey());
        RequirementExecutionMode targetMode = defaultExecutionMode(master.getExecutionMode());
        AiSplitResult splitResult = callAiForModules(master, agents, setting, request == null ? null : request.getPromptText(), targetMode);
        master.setExecutionMode(targetMode);
        requirementInfoRepository.save(master);
        List<RequirementResponse> created = new ArrayList<>(splitResult.modules().size());
        Map<String, String> moduleKeyToRequirementNo = new LinkedHashMap<>();
        int index = 1;
        for (AiSplitModule module : splitResult.modules()) {
            AgentInfo agent = resolveAgent(module.agentCode(), agents);
            int moduleIndex = index++;
            String childNo = nextChildRequirementNo(master.getRequirementNo(), moduleIndex);
            CreateRequirementRequest child = new CreateRequirementRequest();
            child.setRequirementNo(childNo);
            child.setProjectCode(master.getProjectCode());
            child.setTitle(defaultText(module.title(), "子模块 " + moduleIndex));
            child.setRequirementDesc(defaultText(module.taskDesc(), module.title()));
            child.setPriority(defaultText(module.priority(), master.getPriority()));
            child.setStatus(RequirementStatus.PENDING);
            child.setSource("AI_SPLIT:" + master.getRequirementNo());
            child.setRequirementType(RequirementType.SUB);
            child.setSortNo(module.sortNo() == null || module.sortNo() <= 0 ? moduleIndex : module.sortNo());
            child.setMainAgentCode(agent.getAgentCode());
            child.setSessionStrategy(master.getSessionStrategy());
            child.setCurrentStage(defaultText(module.currentStage(), "待开发"));
            child.setCreatedBy("AI_ANALYSIS");
            child.setExecutionSteps(defaultText(module.executionSteps(), module.taskDesc()));
            child.setExecutionMode(targetMode);
            child.setResultExtractableFlag(module.resultExtractable());
            child.setReviewRequiredFlag(Boolean.TRUE.equals(module.reviewRequired()));
            RequirementResponse response = requirementService.createChildWithoutRefresh(master.getRequirementNo(), child);
            created.add(response);
            moduleKeyToRequirementNo.put(module.moduleKey(), response.getRequirementNo());
        }
        saveWorkflowIfNeeded(master.getRequirementNo(), targetMode, created, moduleKeyToRequirementNo, splitResult.edges());
        requirementService.refreshMasterStatusAfterAnalysis(master.getRequirementNo());
        return created;
    }

    private List<AgentInfo> findAvailableAgents(String projectCode) {
        return agentInfoRepository.findByProjectCode(projectCode).stream()
                .filter(item -> item.getStatus() != AgentStatus.DISABLED)
                .sorted(Comparator.comparing(AgentInfo::getAgentRole).thenComparing(AgentInfo::getAgentCode))
                .toList();
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
                .max(Comparator.comparing(AiModelSetting::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new BizException(400, "no enabled ai setting"));
    }

    private AiSplitResult callAiForModules(RequirementInfo master,
                                           List<AgentInfo> agents,
                                           AiModelSetting setting,
                                           String extraPrompt,
                                           RequirementExecutionMode targetMode) {
        String baseUrl = trimToNull(setting.getBaseUrl());
        if (baseUrl == null) {
            throw new BizException(400, "ai setting baseUrl is required");
        }
        String prompt = buildPrompt(master, agents, setting, extraPrompt, targetMode);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String apiKey = trimToNull(setting.getApiKey());
        if (apiKey != null) {
            headers.setBearerAuth(apiKey);
        }
        String requestUrl = responsesUrl(baseUrl);
        List<AiRequestAttempt> attempts = buildRequestAttempts(requestUrl, setting, prompt);
        List<Map<String, Object>> requestAttempts = new ArrayList<>();
        List<Map<String, Object>> responseAttempts = new ArrayList<>();
        String lastError = null;
        for (AiRequestAttempt attempt : attempts) {
            requestAttempts.add(requestAttemptPayload(attempt));
            boolean attemptReceivedResponse = false;
            try {
                ResponseEntity<String> response = postForString(attempt.url(), attempt.body(), headers);
                attemptReceivedResponse = true;
                responseAttempts.add(responsePayload(attempt.mode(), response));
                String content = extractAssistantContent(response.getBody());
                AiSplitResult splitResult = parseModules(content, agents, targetMode);
                aiAnalysisLogService.record(
                        AiAnalysisLogService.SOURCE_REQUIREMENT_AI_SPLIT,
                        master.getRequirementNo(),
                        master.getProjectCode(),
                        setting.getSettingKey(),
                        setting.getModelName(),
                        AiAnalysisLogService.STATUS_SUCCESS,
                        attemptsPayload(requestAttempts),
                        attemptsPayload(responseAttempts),
                        null);
                return splitResult;
            } catch (org.springframework.web.client.HttpStatusCodeException httpExc) {
                lastError = httpExc.getMessage();
                responseAttempts.add(errorResponsePayload(attempt.mode(), httpExc));
                aiAnalysisLogService.record(
                        AiAnalysisLogService.SOURCE_REQUIREMENT_AI_SPLIT,
                        master.getRequirementNo(),
                        master.getProjectCode(),
                        setting.getSettingKey(),
                        setting.getModelName(),
                        AiAnalysisLogService.STATUS_FAILED,
                        attemptsPayload(requestAttempts),
                        attemptsPayload(responseAttempts),
                        lastError);
                throw new BizException(502, "ai analysis failed: " + lastError);
            } catch (Exception exc) {
                lastError = exc.getMessage();
                if (!attemptReceivedResponse) {
                    responseAttempts.add(errorResponsePayload(attempt.mode(), exc));
                    aiAnalysisLogService.record(
                            AiAnalysisLogService.SOURCE_REQUIREMENT_AI_SPLIT,
                            master.getRequirementNo(),
                            master.getProjectCode(),
                            setting.getSettingKey(),
                            setting.getModelName(),
                            AiAnalysisLogService.STATUS_FAILED,
                            attemptsPayload(requestAttempts),
                            attemptsPayload(responseAttempts),
                            lastError);
                    throw exc instanceof BizException bizException
                            ? bizException
                            : new BizException(502, "ai analysis failed: " + lastError);
                }
                // The provider returned HTTP 200 but no usable assistant content or invalid JSON.
                // Generate fallback modules locally instead of sending duplicate requests.
            }
        }
        String reason = defaultText(lastError, "AI 模型没有返回可解析子模块");
        aiAnalysisLogService.record(
                AiAnalysisLogService.SOURCE_REQUIREMENT_AI_SPLIT,
                master.getRequirementNo(),
                master.getProjectCode(),
                setting.getSettingKey(),
                setting.getModelName(),
                AiAnalysisLogService.STATUS_FALLBACK,
                attemptsPayload(requestAttempts),
                attemptsPayload(responseAttempts),
                "AI response cannot be used, fallback modules were generated: " + reason);
        return new AiSplitResult(targetMode, buildFallbackModules(master, agents, reason, targetMode), List.of());
    }

    private List<AiSplitModule> buildFallbackModules(RequirementInfo master,
                                                     List<AgentInfo> agents,
                                                     String reason,
                                                     RequirementExecutionMode targetMode) {
        AgentInfo primary = agents.get(0);
        String title = defaultText(cleanText(master.getTitle()), master.getRequirementNo());
        String context = defaultText(cleanText(master.getRequirementDesc()), title);
        String fallbackReason = defaultText(cleanText(reason), "AI 模型没有返回可解析子模块，系统按规则生成兜底拆分。");
        String agentCode = primary.getAgentCode();
        String workflowHint = targetMode == RequirementExecutionMode.WORKFLOW
                ? "该总需求为工作流模式，本节点完成后会把可提取回执提供给后续节点。"
                : "该总需求为正常模式，本子模块需要能独立下发和独立验收，不依赖其他子模块回执。";
        return List.of(
                new AiSplitModule(
                        "M1",
                        "复现确认与影响范围梳理",
                        agentCode,
                        """
                                兜底拆分原因：%s
                                围绕总需求「%s」复现问题，确认搜索结果页、详情页、封面图和 Series Title 展示的实际异常，并记录复现链接、截图和影响范围。
                                %s
                                需求上下文：
                                %s
                                """.formatted(fallbackReason, title, workflowHint, context),
                        "待复现",
                        defaultText(master.getPriority(), "中"),
                        """
                                1. 打开需求描述中的搜索结果页和详情页链接。
                                2. 对比封面图、Series Title 字段和接口/页面展示结果。
                                3. 汇总可复现步骤、影响页面、异常截图和相关接口地址。
                        """,
                        1,
                        true,
                        false
                ),
                new AiSplitModule(
                        "M2",
                        "数据与接口证据补充",
                        agentCode,
                        """
                                围绕封面图与 Series Title 的展示异常，补充接口返回样本、字段映射、页面渲染差异和缓存/转义差异的证据，明确问题落点。
                                %s
                                需求上下文：
                                %s
                                """.formatted(workflowHint, context),
                        "待核查",
                        defaultText(master.getPriority(), "中"),
                        """
                                1. 核对搜索结果页和详情页使用的 Series Title 与封面图来源接口。
                                2. 记录接口返回、页面展示和截图差异，确认是哪一层丢失字段。
                                3. 汇总可直接交给开发处理的证据、结论和待确认项。
                        """,
                        2,
                        true,
                        false
                ),
                new AiSplitModule(
                        "M3",
                        "回归验证与交付说明",
                        agentCode,
                        """
                                对修复后的搜索结果页和详情页做回归，确认封面图和 Series Title 在目标链接及相近数据上显示正确，并输出交付说明。
                                %s
                                """.formatted(workflowHint),
                        "待验证",
                        defaultText(master.getPriority(), "中"),
                        """
                                1. 使用原始缺陷链接回归搜索结果页和详情页。
                                2. 覆盖至少一条相近馆藏数据，确认没有引入展示回归。
                                3. 记录验证结果、截图和剩余风险。
                        """,
                        3,
                        true,
                        false
                )
        );
    }

    private String buildPrompt(RequirementInfo master,
                               List<AgentInfo> agents,
                               AiModelSetting setting,
                               String extraPrompt,
                               RequirementExecutionMode targetMode) {
        String agentLines = agents.stream()
                .map(item -> "- %s | %s | %s | supported=%s | capability=%s".formatted(
                        item.getAgentCode(),
                        item.getAgentName(),
                        item.getAgentRole(),
                        defaultText(item.getSupportedLinkTypes(), "-"),
                        defaultText(item.getCapabilityTags(), "-")))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("-");
        String modeRules = targetMode == RequirementExecutionMode.WORKFLOW
                ? """
                当前总需求执行模式：WORKFLOW（工作流模式）。
                工作流拆分规则：
                1. 必须返回 {"executionMode":"WORKFLOW","modules":[...],"edges":[...]}。
                2. modules 每项必须包含 moduleKey，建议使用 M1、M2、M3 这种唯一编号。
                3. modules 每项字段：moduleKey、title、agentCode、taskDesc、currentStage、priority、executionSteps、sortNo、resultExtractable、reviewRequired、dependsOn。
                4. dependsOn 是当前节点依赖的上游 moduleKey 数组；起点节点用空数组 []。
                5. edges 是可选冗余字段，格式 [{"from":"M1","to":"M2"}]；如果同时提供 dependsOn 和 edges，系统会合并。
                6. 必须表达真实串并行关系：可以并行的节点不要互相依赖；必须等待上游结果的节点必须写 dependsOn。
                7. 除非只有一个节点，否则所有节点必须通过 dependsOn/edges 接入同一张无环流程图，不能出现孤立节点。
                8. sortNo 只表示画布层级；同一 sortNo 的节点表示同层并行候选，运行时以 dependsOn/edges 为准。
                9. 后续节点如果需要读取上游执行回执，resultExtractable 应保证上游节点为 true；纯准备或无需给后续读取的节点可为 false。
                10. 如果原始需求或任务文字出现“需要人工审核、人工确认、人工审批、人工复核、human review、manual review、approval”等含义，对应模块必须设置 reviewRequired=true，不能只写在描述里。
                """
                : """
                当前总需求执行模式：NORMAL（正常模式）。
                正常拆分规则：
                1. 必须返回 {"executionMode":"NORMAL","modules":[...]}。
                2. modules 每项字段：moduleKey、title、agentCode、taskDesc、currentStage、priority、executionSteps、sortNo、resultExtractable、reviewRequired。
                3. 每个子模块都必须能独立下发、独立执行、独立验收，不能依赖其他子模块的执行回执。
                4. 不要返回 dependsOn 或 edges；即使任务有先后建议，也要写进各自 executionSteps，不要构造工作流关系。
                5. sortNo 只用于列表排序，不表示执行先后；resultExtractable 默认 true。
                6. 如果原始需求或任务文字出现“需要人工审核、人工确认、人工审批、人工复核、human review、manual review、approval”等含义，对应模块必须设置 reviewRequired=true，不能只写在描述里。
                """;
        return """
                请根据总需求内容和可用 Agent 拆分可执行子模块。
                通用要求：
                1. 只使用下面 Agent 列表中的 agentCode。
                2. 粒度不要过大，title 使用中文短标题，taskDesc 写清楚输入、处理范围、输出结果。
                3. 只分析并创建子模块，不要生成开发链路、执行链路、代码实现内容或任务执行内容。
                4. executionSteps 可以是数组或换行字符串。
                5. reviewRequired 是布尔值，默认 false；遇到需要人工审核/确认/审批/复核的任务必须设置 true。
                6. 返回合法 JSON，不要输出 Markdown。

                %s

                项目关联 Markdown 文档：
                %s

                模型配置提示：
                %s

                总需求：
                编号：%s
                标题：%s
                优先级：%s
                当前阶段：%s
                描述：
                %s

                原始执行步骤：
                %s

                可用 Agent：
                %s

                额外要求：
                %s
                """.formatted(
                modeRules,
                defaultText(cleanText(projectMarkdownService.buildAiContext(
                        master.getProjectCode(),
                        ProjectDocumentUsage.REQUIREMENT_AI_ANALYSIS)), "-"),
                defaultText(setting.getPromptTemplate(), "-"),
                master.getRequirementNo(),
                defaultText(cleanText(master.getTitle()), "-"),
                defaultText(cleanText(master.getPriority()), "-"),
                defaultText(cleanText(master.getCurrentStage()), "-"),
                defaultText(cleanText(master.getRequirementDesc()), "-"),
                defaultText(cleanText(master.getExecutionSteps()), "-"),
                agentLines,
                defaultText(cleanText(extraPrompt), "-"));
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
            List<String> parts = new ArrayList<>();
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

    private AiSplitResult parseModules(String content, List<AgentInfo> agents, RequirementExecutionMode targetMode) throws Exception {
        JsonNode root = objectMapper.readTree(extractJson(content));
        JsonNode modulesNode = root.isArray() ? root : root.path("modules");
        if (!modulesNode.isArray() || modulesNode.isEmpty()) {
            throw new BizException(502, "ai response modules is empty");
        }
        List<AiSplitModule> result = new ArrayList<>();
        List<JsonNode> validModuleNodes = new ArrayList<>();
        Map<String, String> titleToModuleKey = new LinkedHashMap<>();
        for (JsonNode item : modulesNode) {
            if (result.size() >= MAX_MODULES) {
                break;
            }
            String title = trimToNull(item.path("title").asText(null));
            if (title == null) {
                continue;
            }
            String moduleKey = normalizeModuleKey(readFirstText(item, "moduleKey", "key", "id", "code"));
            if (moduleKey != null) {
                String candidateModuleKey = moduleKey;
                if (result.stream().anyMatch(module -> module.moduleKey().equals(candidateModuleKey))) {
                    moduleKey = null;
                }
            }
            if (moduleKey == null) {
                moduleKey = "M" + (result.size() + 1);
            }
            String requestedAgentCode = trimToNull(item.path("agentCode").asText(null));
            String resolvedAgentCode = requestedAgentCode != null && agents.stream().anyMatch(agent -> agent.getAgentCode().equals(requestedAgentCode))
                    ? requestedAgentCode
                    : agents.get(0).getAgentCode();
            titleToModuleKey.put(title, moduleKey);
            result.add(new AiSplitModule(
                    moduleKey,
                    title,
                    resolvedAgentCode,
                    trimToNull(item.path("taskDesc").asText(null)),
                    trimToNull(item.path("currentStage").asText(null)),
                    trimToNull(item.path("priority").asText(null)),
                    readExecutionSteps(item.path("executionSteps")),
                    item.path("sortNo").isInt() ? item.path("sortNo").asInt() : null,
                    item.path("resultExtractable").isMissingNode() || item.path("resultExtractable").asBoolean(true),
                    resolveReviewRequired(item)
            ));
            validModuleNodes.add(item);
        }
        if (result.isEmpty()) {
            throw new BizException(502, "ai response has no valid modules");
        }
        List<AiSplitEdge> edges = targetMode == RequirementExecutionMode.WORKFLOW
                ? parseWorkflowEdges(root, validModuleNodes, result, titleToModuleKey)
                : List.of();
        return new AiSplitResult(targetMode, result, edges);
    }

    private boolean resolveReviewRequired(JsonNode item) {
        for (String field : List.of(
                "reviewRequired",
                "reviewRequiredFlag",
                "manualReview",
                "humanReview",
                "humanReviewRequired",
                "requiresReview",
                "requiresHumanReview",
                "requiresApproval")) {
            JsonNode node = item.path(field);
            if (!node.isMissingNode() && !node.isNull()) {
                return node.asBoolean(false);
            }
        }
        return false;
    }

    private List<AiSplitEdge> parseWorkflowEdges(JsonNode root,
                                                 List<JsonNode> moduleNodes,
                                                 List<AiSplitModule> modules,
                                                 Map<String, String> titleToModuleKey) {
        Map<String, String> moduleKeyLookup = new LinkedHashMap<>();
        for (AiSplitModule module : modules) {
            moduleKeyLookup.put(module.moduleKey(), module.moduleKey());
            moduleKeyLookup.put(module.title(), module.moduleKey());
        }
        moduleKeyLookup.putAll(titleToModuleKey);
        List<AiSplitEdge> edges = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();

        JsonNode edgesNode = root.path("edges");
        if (edgesNode.isArray()) {
            for (JsonNode edgeNode : edgesNode) {
                String from = resolveModuleRef(readFirstText(edgeNode, "from", "fromKey", "fromModuleKey", "source"), moduleKeyLookup);
                String to = resolveModuleRef(readFirstText(edgeNode, "to", "toKey", "toModuleKey", "target"), moduleKeyLookup);
                addAiSplitEdge(edges, seen, from, to);
            }
        }

        int moduleIndex = 0;
        for (JsonNode moduleNode : moduleNodes) {
            if (moduleIndex >= modules.size()) {
                break;
            }
            String currentKey = modules.get(moduleIndex++).moduleKey();
            for (String dependency : readTextArray(readFirstNode(moduleNode, "dependsOn", "previous", "previousKeys", "dependencies"))) {
                String from = resolveModuleRef(dependency, moduleKeyLookup);
                addAiSplitEdge(edges, seen, from, currentKey);
            }
        }
        return edges;
    }

    private void addAiSplitEdge(List<AiSplitEdge> edges, java.util.Set<String> seen, String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        String key = from + "->" + to;
        if (seen.add(key)) {
            edges.add(new AiSplitEdge(from, to));
        }
    }

    private String resolveModuleRef(String value, Map<String, String> moduleKeyLookup) {
        String normalized = normalizeModuleKey(value);
        if (normalized == null) {
            return null;
        }
        return moduleKeyLookup.getOrDefault(normalized, moduleKeyLookup.get(value));
    }

    private JsonNode readFirstNode(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String readFirstText(JsonNode node, String... keys) {
        JsonNode value = readFirstNode(node, keys);
        if (value == null) {
            return null;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return trimToNull(value.asText());
        }
        return null;
    }

    private List<String> readTextArray(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> result = new ArrayList<>();
            for (JsonNode item : node) {
                String text = item.isObject()
                        ? readFirstText(item, "moduleKey", "key", "id", "title", "from", "source")
                        : trimToNull(item.asText(null));
                if (text != null) {
                    java.util.Arrays.stream(text.split("[,，、\\n]"))
                            .map(this::trimToNull)
                            .filter(value -> value != null)
                            .forEach(result::add);
                }
            }
            return result;
        }
        if (node.isObject()) {
            String text = readFirstText(node, "moduleKey", "key", "id", "title", "from", "source");
            return text == null ? List.of() : List.of(text);
        }
        String text = trimToNull(node.asText(null));
        if (text == null) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split("[,，、\\n]"))
                .map(this::trimToNull)
                .filter(item -> item != null)
                .toList();
    }

    private String normalizeModuleKey(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized;
    }

    private String readExecutionSteps(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<String> lines = new ArrayList<>();
            int index = 1;
            for (JsonNode item : node) {
                String text = trimToNull(item.asText(null));
                if (text != null) {
                    lines.add(index++ + ". " + text);
                }
            }
            return lines.isEmpty() ? null : String.join("\n", lines);
        }
        return trimToNull(node.asText(null));
    }

    private String extractJson(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start;
        if (objectStart < 0) {
            start = arrayStart;
        } else if (arrayStart < 0) {
            start = objectStart;
        } else {
            start = Math.min(objectStart, arrayStart);
        }
        if (start > 0) {
            text = text.substring(start);
        }
        int objectEnd = text.lastIndexOf('}');
        int arrayEnd = text.lastIndexOf(']');
        int end = Math.max(objectEnd, arrayEnd);
        return end >= 0 ? text.substring(0, end + 1) : text;
    }

    private AgentInfo resolveAgent(String agentCode, List<AgentInfo> agents) {
        return agents.stream()
                .filter(item -> item.getAgentCode().equals(agentCode))
                .findFirst()
                .orElse(agents.get(0));
    }

    private void saveWorkflowIfNeeded(String masterRequirementNo,
                                      RequirementExecutionMode targetMode,
                                      List<RequirementResponse> created,
                                      Map<String, String> moduleKeyToRequirementNo,
                                      List<AiSplitEdge> aiEdges) {
        if (targetMode != RequirementExecutionMode.WORKFLOW || created == null || created.isEmpty()) {
            return;
        }
        if (!trySaveAiWorkflowEdges(masterRequirementNo, created, moduleKeyToRequirementNo, aiEdges)) {
            requirementWorkflowService.saveDefaultEdgesFromSortOrder(masterRequirementNo, created);
        }
    }

    private boolean trySaveAiWorkflowEdges(String masterRequirementNo,
                                           List<RequirementResponse> created,
                                           Map<String, String> moduleKeyToRequirementNo,
                                           List<AiSplitEdge> aiEdges) {
        if (created.size() <= 1) {
            return true;
        }
        if (aiEdges == null || aiEdges.isEmpty()) {
            return false;
        }
        SaveRequirementWorkflowRequest request = new SaveRequirementWorkflowRequest();
        List<SaveRequirementWorkflowRequest.Edge> requestEdges = new ArrayList<>();
        for (AiSplitEdge aiEdge : aiEdges) {
            String from = moduleKeyToRequirementNo.get(aiEdge.fromModuleKey());
            String to = moduleKeyToRequirementNo.get(aiEdge.toModuleKey());
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            SaveRequirementWorkflowRequest.Edge edge = new SaveRequirementWorkflowRequest.Edge();
            edge.setFromRequirementNo(from);
            edge.setToRequirementNo(to);
            requestEdges.add(edge);
        }
        if (!workflowEdgesCoverAllNodes(created, requestEdges)) {
            return false;
        }
        request.setEdges(requestEdges);
        try {
            requirementWorkflowService.saveWorkflow(masterRequirementNo, request);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean workflowEdgesCoverAllNodes(List<RequirementResponse> nodes, List<SaveRequirementWorkflowRequest.Edge> edges) {
        if (nodes.size() <= 1) {
            return true;
        }
        if (edges.isEmpty()) {
            return false;
        }
        java.util.Set<String> linked = new java.util.HashSet<>();
        for (SaveRequirementWorkflowRequest.Edge edge : edges) {
            linked.add(edge.getFromRequirementNo());
            linked.add(edge.getToRequirementNo());
        }
        return nodes.stream().map(RequirementResponse::getRequirementNo).allMatch(linked::contains);
    }

    private String nextChildRequirementNo(String masterNo, int index) {
        int next = index;
        while (next < 1000) {
            String suffix = "-S%02d".formatted(next);
            String prefix = masterNo.length() + suffix.length() > 64
                    ? masterNo.substring(0, 64 - suffix.length())
                    : masterNo;
            String candidate = prefix + suffix;
            if (requirementInfoRepository.findByRequirementNo(candidate).isEmpty()) {
                return candidate;
            }
            next++;
        }
        throw new BizException(400, "cannot allocate child requirementNo");
    }

    private List<AiRequestAttempt> buildRequestAttempts(String requestUrl, AiModelSetting setting, String prompt) {
        Map<String, Object> responsesBody = baseResponsesBody(setting, prompt,
                "你是需求分析助手。必须在可见输出里直接返回合法 JSON，不要输出 Markdown。",
                true);
        applyResponsesOptions(responsesBody, setting, 4096);

        return List.of(new AiRequestAttempt("responses", requestUrl, responsesBody));
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
        String modelName = defaultText(setting.getModelName(), "").toLowerCase(Locale.ROOT);
        if (isReasoningModel(modelName)) {
            body.put("reasoning", Map.of("effort", "low"));
        }
        body.put("max_output_tokens", maxOutputTokens);
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
        if (TEMP_LOG_AI_REQUEST_ONLY) {
            printFullAiRequestForDebug(url, body, headers);
           // throw new BizException(502, "TEMP_LOG_AI_REQUEST_ONLY enabled: AI request was logged locally and not sent");
        }

        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
        return ResponseEntity
                .status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(decodeResponseBody(response.getBody(), response.getHeaders()));

        //throw new BizException(502, "AI request sender is temporarily commented out");
    }

    private void printFullAiRequestForDebug(String url, Object body, HttpHeaders headers) {
        try {
            String bodyJson = safeJson(body);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("notice", "AI request is NOT sent because TEMP_LOG_AI_REQUEST_ONLY=true");
            payload.put("url", url);
            payload.put("headers", sanitizedHeaders(headers));
            payload.put("bodyChars", bodyJson.length());
            payload.put("body", body);
            Files.createDirectories(TEMP_AI_REQUEST_LOG_DIR);
            Path file = TEMP_AI_REQUEST_LOG_DIR.resolve(
                    "requirement-ai-request-" + LocalDateTime.now().format(TEMP_AI_REQUEST_LOG_TIME_FORMAT) + ".json");
            Files.writeString(file, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload), StandardCharsets.UTF_8);
            System.out.println("\nTEMP AI request saved without sending: " + file.toAbsolutePath());
            System.out.println("TEMP AI request body chars: " + bodyJson.length() + "\n");
        } catch (Exception ex) {
            throw new BizException(500, "failed to write temp ai request log: " + ex.getMessage());
        }
    }

    private Map<String, Object> sanitizedHeaders(HttpHeaders headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        headers.forEach((key, values) -> {
            if ("authorization".equalsIgnoreCase(key)) {
                result.put(key, List.of("Bearer ***"));
            } else {
                result.put(key, values);
            }
        });
        return result;
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

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }

    private RequirementExecutionMode defaultExecutionMode(RequirementExecutionMode value) {
        return value == null ? RequirementExecutionMode.NORMAL : value;
    }

    private String defaultText(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String cleanText(String value) {
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record AiSplitResult(
            RequirementExecutionMode executionMode,
            List<AiSplitModule> modules,
            List<AiSplitEdge> edges
    ) {
    }

    private record AiSplitModule(
            String moduleKey,
            String title,
            String agentCode,
            String taskDesc,
            String currentStage,
            String priority,
            String executionSteps,
            Integer sortNo,
            Boolean resultExtractable,
            Boolean reviewRequired
    ) {
    }

    private record AiSplitEdge(
            String fromModuleKey,
            String toModuleKey
    ) {
    }

    private record AiRequestAttempt(
            String mode,
            String url,
            Map<String, Object> body
    ) {
    }
}
