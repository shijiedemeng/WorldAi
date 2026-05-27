package com.aiapi.mcp.service;

import com.aiapi.common.exception.BizException;
import com.aiapi.mcp.dto.McpFileRequest;
import com.aiapi.mcp.dto.McpFileResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
public class McpSseService {

    private static final long SSE_TIMEOUT_MS = 30L * 60L * 1000L;

    private final ObjectMapper objectMapper;
    private final McpFileProxyService mcpFileProxyService;
    private final Map<String, McpSseSession> sessions = new ConcurrentHashMap<>();

    public SseEmitter open(String agentCode) {
        String normalizedAgentCode = trimToNull(agentCode);
        if (normalizedAgentCode == null) {
            throw new BizException(400, "agentCode is required");
        }
        String sessionId = "MCP_SSE-" + UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        McpSseSession session = new McpSseSession(sessionId, normalizedAgentCode, emitter);
        sessions.put(sessionId, session);
        emitter.onCompletion(() -> sessions.remove(sessionId));
        emitter.onTimeout(() -> sessions.remove(sessionId));
        emitter.onError(error -> sessions.remove(sessionId));
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/api/mcp/agents/%s/messages?sessionId=%s".formatted(
                            UriUtils.encodePathSegment(normalizedAgentCode, StandardCharsets.UTF_8),
                            UriUtils.encodeQueryParam(sessionId, StandardCharsets.UTF_8)
                    )));
        } catch (IOException ex) {
            sessions.remove(sessionId);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void handleMessage(String agentCode, String sessionId, JsonNode payload) {
        String normalizedSessionId = trimToNull(sessionId);
        if (normalizedSessionId == null) {
            throw new BizException(400, "sessionId is required");
        }
        McpSseSession session = sessions.get(normalizedSessionId);
        if (session == null) {
            throw new BizException(404, "mcp sse session not found");
        }
        String normalizedAgentCode = trimToNull(agentCode);
        if (normalizedAgentCode == null || !normalizedAgentCode.equals(session.agentCode())) {
            throw new BizException(400, "agentCode does not match sse session");
        }
        JsonNode id = payload == null ? null : payload.get("id");
        if (id == null || id.isNull()) {
            return;
        }
        ObjectNode response;
        try {
            response = successResponse(id, handleRequest(session.agentCode(), payload));
        } catch (BizException ex) {
            response = errorResponse(id, ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            response = errorResponse(id, -32603, "%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()));
        }
        sendMessage(session, response);
    }

    private JsonNode handleRequest(String agentCode, JsonNode payload) {
        String method = payload == null ? "" : payload.path("method").asText("");
        return switch (method) {
            case "initialize" -> initializeResult(payload);
            case "ping" -> objectMapper.createObjectNode();
            case "tools/list" -> toolsListResult(agentCode);
            case "tools/call" -> toolsCallResult(agentCode, payload.path("params"));
            default -> throw new BizException(-32601, "method not found: " + method);
        };
    }

    private ObjectNode initializeResult(JsonNode payload) {
        ObjectNode result = objectMapper.createObjectNode();
        String protocolVersion = payload.path("params").path("protocolVersion").asText("2024-11-05");
        result.put("protocolVersion", protocolVersion);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "ai-api-mcp-file-search");
        serverInfo.put("version", "1.0.0");
        return result;
    }

    private ObjectNode toolsListResult(String agentCode) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        if (mcpFileProxyService.listFileTools(agentCode).isEmpty()) {
            return result;
        }
        tools.add(tool("tree", "返回 Agent 工作目录下的目录树", true, false));
        tools.add(tool("list", "返回指定目录下一层文件和目录", false, false));
        tools.add(tool("read", "读取指定文件内容", false, false));
        tools.add(tool("search", "按文件名或文件内容搜索", false, true));
        return result;
    }

    private ObjectNode tool(String name, String description, boolean includeDepth, boolean requireKeyword) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode path = properties.putObject("path");
        path.put("type", "string");
        path.put("description", "相对 Agent 工作目录的路径，默认 .");
        if (includeDepth || requireKeyword) {
            ObjectNode maxDepth = properties.putObject("maxDepth");
            maxDepth.put("type", "integer");
            maxDepth.put("description", "最大递归深度");
        }
        ObjectNode limit = properties.putObject("limit");
        limit.put("type", "integer");
        limit.put("description", "最大返回数量");
        if (requireKeyword) {
            ObjectNode keyword = properties.putObject("keyword");
            keyword.put("type", "string");
            keyword.put("description", "搜索关键字");
            schema.putArray("required").add("keyword");
        }
        return tool;
    }

    private ObjectNode toolsCallResult(String agentCode, JsonNode params) {
        String name = params.path("name").asText("");
        JsonNode arguments = params.path("arguments");
        McpFileRequest request = new McpFileRequest();
        request.setOperation(toolNameToOperation(name, arguments.path("operation").asText(null)));
        request.setPath(arguments.path("path").asText("."));
        request.setKeyword(arguments.path("keyword").asText(null));
        if (arguments.has("maxDepth") && arguments.path("maxDepth").canConvertToInt()) {
            request.setMaxDepth(arguments.path("maxDepth").asInt());
        }
        if (arguments.has("limit") && arguments.path("limit").canConvertToInt()) {
            request.setLimit(arguments.path("limit").asInt());
        }
        McpFileResponse fileResponse = mcpFileProxyService.requestFileTool(agentCode, request);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("isError", trimToNull(fileResponse.getErrorMessage()) != null);
        ArrayNode content = result.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", toPrettyJson(fileResponse));
        return result;
    }

    private String toolNameToOperation(String name, String fallback) {
        String normalized = trimToNull(name);
        if (normalized == null) {
            return defaultOperation(fallback);
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "tree" -> "TREE";
            case "list" -> "LIST";
            case "read" -> "READ";
            case "search" -> "SEARCH";
            default -> defaultOperation(fallback);
        };
    }

    private String defaultOperation(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "TREE" : normalized.toUpperCase(Locale.ROOT);
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

    private record McpSseSession(String sessionId, String agentCode, SseEmitter emitter) {
    }
}
