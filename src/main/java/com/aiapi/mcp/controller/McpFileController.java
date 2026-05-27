package com.aiapi.mcp.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.mcp.dto.McpFileRequest;
import com.aiapi.mcp.dto.McpFileResponse;
import com.aiapi.mcp.service.McpFileProxyService;
import com.aiapi.mcp.service.McpSseService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpFileController {

    private final McpFileProxyService mcpFileProxyService;
    private final McpSseService mcpSseService;

    @GetMapping(value = "/agents/{agentCode}/tools", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<Map<String, Object>>> tools(@PathVariable String agentCode) {
        return ApiResponse.success(mcpFileProxyService.listFileTools(agentCode));
    }

    @GetMapping(value = {"/agents/{agentCode}/sse", "/agents/{agentCode}/tools"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(@PathVariable String agentCode) {
        return mcpSseService.open(agentCode);
    }

    @PostMapping("/agents/{agentCode}/messages")
    public ResponseEntity<Void> messages(@PathVariable String agentCode,
                                         @RequestParam String sessionId,
                                         @RequestBody JsonNode payload) {
        mcpSseService.handleMessage(agentCode, sessionId, payload);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/agents/{agentCode}/files")
    public ApiResponse<McpFileResponse> files(@PathVariable String agentCode,
                                              @RequestBody(required = false) McpFileRequest request) {
        return ApiResponse.success(mcpFileProxyService.requestFileTool(agentCode, request));
    }

    @PostMapping("/client-requests/{requestId}/complete")
    public ApiResponse<Void> complete(@PathVariable String requestId,
                                      @RequestBody(required = false) McpFileResponse response) {
        mcpFileProxyService.complete(requestId, response);
        return ApiResponse.success();
    }
}
