package com.aiapi.mcp.service;

import com.aiapi.common.enums.ProjectKnowledgeType;
import com.aiapi.common.exception.BizException;
import com.aiapi.knowledge.dto.ProjectKnowledgeSearchResponse;
import com.aiapi.knowledge.dto.SearchProjectKnowledgeRequest;
import com.aiapi.knowledge.service.ProjectKnowledgeReserveService;
import com.aiapi.project.dto.ProjectResponse;
import com.aiapi.project.service.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.UriUtils;

@Service
@RequiredArgsConstructor
public class McpProjectKnowledgeSseService {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    private final ObjectMapper objectMapper;
    private final ProjectService projectService;
    private final ProjectKnowledgeReserveService projectKnowledgeReserveService;
    private final Map<String, McpSseSession> sessions = new ConcurrentHashMap<>();

    public SseEmitter open() {
        String sessionId = "MCP_KNOWLEDGE_SSE-" + UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        McpSseSession session = new McpSseSession(sessionId, emitter);
        sessions.put(sessionId, session);
        emitter.onCompletion(() -> sessions.remove(sessionId));
        emitter.onTimeout(() -> sessions.remove(sessionId));
        emitter.onError(error -> sessions.remove(sessionId));
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/api/mcp/project-knowledge/messages?sessionId=%s".formatted(
                            UriUtils.encodeQueryParam(sessionId, StandardCharsets.UTF_8)
                    )));
        } catch (IOException ex) {
            sessions.remove(sessionId);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void handleMessage(String sessionId, JsonNode payload) {
        String normalizedSessionId = trimToNull(sessionId);
        if (normalizedSessionId == null) {
            throw new BizException(400, "sessionId is required");
        }
        McpSseSession session = sessions.get(normalizedSessionId);
        if (session == null) {
            throw new BizException(404, "mcp project knowledge sse session not found");
        }
        JsonNode id = payload == null ? null : payload.get("id");
        if (id == null || id.isNull()) {
            return;
        }
        ObjectNode response;
        try {
            response = successResponse(id, handleRequest(payload));
        } catch (BizException ex) {
            response = errorResponse(id, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            response = errorResponse(id, -32603, "%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()));
        }
        sendMessage(session, response);
    }

    private JsonNode handleRequest(JsonNode payload) {
        String method = payload == null ? "" : payload.path("method").asText("");
        return switch (method) {
            case "initialize" -> initializeResult(payload);
            case "ping" -> objectMapper.createObjectNode();
            case "tools/list" -> toolsListResult();
            case "tools/call" -> toolsCallResult(payload.path("params"));
            default -> throw new BizException(-32601, "method not found: " + method);
        };
    }

    private ObjectNode initializeResult(JsonNode payload) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", payload.path("params").path("protocolVersion").asText("2024-11-05"));
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "ai-api-project-knowledge-search");
        serverInfo.put("version", "1.0.0");
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        tools.add(projectsTool());
        tools.add(languageSearchTool());
        return result;
    }

    private ObjectNode projectsTool() {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "project_knowledge_projects");
        tool.put("description", "返回项目编码和项目名称列表，用于后续项目储备库语言搜索。");
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode keyword = properties.putObject("keyword");
        keyword.put("type", "string");
        keyword.put("description", "可选，按项目编码或名称过滤");
        ObjectNode limit = properties.putObject("limit");
        limit.put("type", "integer");
        limit.put("description", "返回数量，默认 50");
        return tool;
    }

    private ObjectNode languageSearchTool() {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "project_knowledge_language_search");
        tool.put("description", "按自然语言查询项目储备库向量内容，返回综合匹配度。");
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        schema.putArray("required").add("projectCode").add("query");
        ObjectNode properties = schema.putObject("properties");
        putStringProperty(properties, "projectCode", "项目编码。可先调用 project_knowledge_projects 获取。");
        putStringProperty(properties, "query", "自然语言搜索内容，可从当前技能会话上下文中提取。");
        putStringProperty(properties, "knowledgeType", "可选，COMMON_ISSUE 或 PROCESS_GUIDE。");
        putStringProperty(properties, "embeddingSettingKey", "可选，向量模型配置键；不传时使用第一个启用的向量模型。");
        ObjectNode limit = properties.putObject("limit");
        limit.put("type", "integer");
        limit.put("description", "返回列表数量，默认 5，最大 20。");
        ObjectNode minMatchScore = properties.putObject("minMatchScore");
        minMatchScore.put("type", "number");
        minMatchScore.put("description", "最低综合匹配值，支持 0-100 或 0-1。例如 75 或 0.75。");
        return tool;
    }

    private void putStringProperty(ObjectNode properties, String name, String description) {
        ObjectNode node = properties.putObject(name);
        node.put("type", "string");
        node.put("description", description);
    }

    private ObjectNode toolsCallResult(JsonNode params) {
        String name = params.path("name").asText("");
        JsonNode arguments = params.path("arguments");
        Object result = switch (normalizeToolName(name)) {
            case "project_knowledge_projects" -> listProjects(arguments);
            case "project_knowledge_language_search" -> languageSearch(arguments);
            default -> throw new BizException(400, "unknown tool: " + name);
        };
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("isError", false);
        ArrayNode content = wrapper.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", toPrettyJson(result));
        wrapper.set("structuredContent", objectMapper.valueToTree(result));
        return wrapper;
    }

    private String normalizeToolName(String name) {
        String normalized = trimToNull(name);
        if (normalized == null) {
            return "";
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "projects", "project_list", "project_knowledge_project_list" -> "project_knowledge_projects";
            case "language_search", "semantic_search", "project_knowledge_search" -> "project_knowledge_language_search";
            default -> normalized;
        };
    }

    private List<ProjectResponse> listProjects(JsonNode arguments) {
        String keyword = trimToNull(arguments.path("keyword").asText(null));
        int limit = normalizeLimit(arguments.path("limit").canConvertToInt() ? arguments.path("limit").asInt() : 50, 100);
        return projectService.list().stream()
                .filter(project -> matchesProjectKeyword(project, keyword))
                .limit(limit)
                .toList();
    }

    private boolean matchesProjectKeyword(ProjectResponse project, String keyword) {
        if (keyword == null) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return contains(project.getProjectCode(), normalized)
                || contains(project.getProjectName(), normalized)
                || contains(project.getProjectDesc(), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private ProjectKnowledgeSearchResponse languageSearch(JsonNode arguments) {
        SearchProjectKnowledgeRequest request = new SearchProjectKnowledgeRequest();
        request.setProjectCode(requiredText(arguments, "projectCode"));
        request.setQuery(requiredText(arguments, "query"));
        String knowledgeType = trimToNull(arguments.path("knowledgeType").asText(null));
        if (knowledgeType != null) {
            request.setKnowledgeType(ProjectKnowledgeType.valueOf(knowledgeType.toUpperCase(Locale.ROOT)));
        }
        request.setEmbeddingSettingKey(trimToNull(arguments.path("embeddingSettingKey").asText(null)));
        if (arguments.has("limit") && arguments.path("limit").canConvertToInt()) {
            request.setLimit(normalizeLimit(arguments.path("limit").asInt(), 20));
        }
        if (arguments.has("minMatchScore") && arguments.path("minMatchScore").isNumber()) {
            request.setMinMatchScore(arguments.path("minMatchScore").asDouble());
        }
        return projectKnowledgeReserveService.search(request);
    }

    private int normalizeLimit(Integer value, int max) {
        if (value == null || value < 1) {
            return Math.min(5, max);
        }
        return Math.min(value, max);
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = trimToNull(node.path(fieldName).asText(null));
        if (value == null) {
            throw new BizException(400, fieldName + " is required");
        }
        return value;
    }

    private ObjectNode successResponse(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode errorResponse(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message == null ? "mcp request failed" : message);
        return response;
    }

    private void sendMessage(McpSseSession session, JsonNode response) {
        try {
            session.emitter().send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(response)));
        } catch (IOException ex) {
            sessions.remove(session.sessionId());
            session.emitter().completeWithError(ex);
        }
    }

    private String toPrettyJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record McpSseSession(String sessionId, SseEmitter emitter) {
    }
}
