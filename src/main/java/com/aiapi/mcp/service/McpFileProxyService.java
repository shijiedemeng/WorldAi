package com.aiapi.mcp.service;

import com.aiapi.client.dto.ClientNodeResponse;
import com.aiapi.client.service.ClientNodeService;
import com.aiapi.client.websocket.ClientDispatchWebSocketHandler;
import com.aiapi.common.enums.ClientNodeStatus;
import com.aiapi.common.exception.BizException;
import com.aiapi.mcp.dto.McpFileClientRequest;
import com.aiapi.mcp.dto.McpFileRequest;
import com.aiapi.mcp.dto.McpFileResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpFileProxyService {

    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    private final ClientNodeService clientNodeService;
    private final ClientDispatchWebSocketHandler webSocketHandler;
    private final ConcurrentHashMap<String, CompletableFuture<McpFileResponse>> pendingRequests = new ConcurrentHashMap<>();

    public McpFileResponse requestFileTool(String agentCode, McpFileRequest request) {
        String normalizedAgentCode = trimToNull(agentCode);
        if (normalizedAgentCode == null) {
            throw new BizException(400, "agentCode is required");
        }
        ClientNodeResponse client = findOnlineClient(normalizedAgentCode);
        String requestId = "MCP_REQ-" + UUID.randomUUID();
        McpFileClientRequest clientRequest = McpFileClientRequest.builder()
                .requestId(requestId)
                .agentCode(normalizedAgentCode)
                .operation(defaultText(request == null ? null : request.getOperation(), "TREE").toUpperCase())
                .path(trimToNull(request == null ? null : request.getPath()))
                .keyword(trimToNull(request == null ? null : request.getKeyword()))
                .maxDepth(request == null ? null : request.getMaxDepth())
                .limit(request == null ? null : request.getLimit())
                .build();

        CompletableFuture<McpFileResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);
        boolean sent = webSocketHandler.sendMcpFileRequest(client.getClientCode(), clientRequest);
        if (!sent) {
            pendingRequests.remove(requestId);
            throw new BizException(400, "linked client websocket is not connected");
        }
        try {
            return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "mcp file request interrupted");
        } catch (TimeoutException ex) {
            throw new BizException(504, "mcp file request timed out");
        } catch (ExecutionException ex) {
            throw new BizException(500, "mcp file request failed");
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    public List<Map<String, Object>> listFileTools(String agentCode) {
        ClientNodeResponse client = findOnlineClient(defaultText(agentCode, ""));
        if (!Boolean.TRUE.equals(client.getMcpEnabled())) {
            return List.of();
        }
        return List.of(
                tool("TREE", "返回 Agent 工作目录下的目录树"),
                tool("LIST", "返回指定目录下一层文件和目录"),
                tool("READ", "读取指定文件内容"),
                tool("SEARCH", "按文件名或文件内容搜索")
        );
    }

    public void complete(String requestId, McpFileResponse response) {
        CompletableFuture<McpFileResponse> future = pendingRequests.get(requestId);
        if (future == null) {
            throw new BizException(404, "mcp file request not found or expired");
        }
        if (response == null) {
            response = new McpFileResponse();
        }
        response.setRequestId(requestId);
        future.complete(response);
    }

    private ClientNodeResponse findOnlineClient(String agentCode) {
        String normalizedAgentCode = trimToNull(agentCode);
        if (normalizedAgentCode == null) {
            throw new BizException(400, "agentCode is required");
        }
        List<ClientNodeResponse> candidates = clientNodeService.list().stream()
                .filter(item -> item.getStatus() == ClientNodeStatus.ONLINE)
                .filter(item -> item.getAgents().stream()
                        .anyMatch(agent -> normalizedAgentCode.equals(agent.getAgentCode()) && Boolean.TRUE.equals(agent.getEnabledFlag())))
                .toList();
        if (candidates.isEmpty()) {
            throw new BizException(404, "no online client linked to agent");
        }
        return candidates.get(0);
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private Map<String, Object> tool(String name, String description) {
        return Map.of("name", name, "description", description);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
