package com.aiapi.mcp.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.knowledge.dto.ProjectKnowledgeSearchResponse;
import com.aiapi.knowledge.dto.SearchProjectKnowledgeRequest;
import com.aiapi.knowledge.service.ProjectKnowledgeReserveService;
import com.aiapi.mcp.service.McpProjectKnowledgeSseService;
import com.aiapi.project.dto.ProjectResponse;
import com.aiapi.project.service.ProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/mcp/project-knowledge")
@RequiredArgsConstructor
public class McpProjectKnowledgeController {

    private final ProjectKnowledgeReserveService service;
    private final ProjectService projectService;
    private final McpProjectKnowledgeSseService mcpProjectKnowledgeSseService;

    @GetMapping(value = "/tools", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<Map<String, Object>>> tools() {
        return ApiResponse.success(List.of(
                Map.of(
                        "name", "project_knowledge_projects",
                        "description", "返回项目编码和项目名称列表。",
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "string", "description", "可选，按项目编码或名称过滤"),
                                        "limit", Map.of("type", "integer", "description", "返回数量，默认 50")
                                )
                        )
                ),
                Map.of(
                        "name", "project_knowledge_language_search",
                        "description", "按自然语言查询项目储备库向量内容，返回综合匹配度。",
                        "inputSchema", Map.of(
                                "type", "object",
                                "required", List.of("projectCode", "query"),
                                "properties", Map.of(
                                        "projectCode", Map.of("type", "string", "description", "项目编码"),
                                        "query", Map.of("type", "string", "description", "查询文本"),
                                        "knowledgeType", Map.of("type", "string", "enum", List.of("COMMON_ISSUE", "PROCESS_GUIDE")),
                                        "embeddingSettingKey", Map.of("type", "string", "description", "可选，向量模型配置键；不传时使用第一个启用的向量模型"),
                                        "limit", Map.of("type", "integer", "description", "返回列表数量，默认 5，最大 20"),
                                        "minMatchScore", Map.of("type", "number", "description", "最低综合匹配值，支持 0-100 或 0-1")
                                )
                        )
                )
        ));
    }

    @PostMapping("/search")
    public ApiResponse<ProjectKnowledgeSearchResponse> search(@Valid @RequestBody SearchProjectKnowledgeRequest request) {
        return ApiResponse.success(service.search(request));
    }

    @GetMapping("/projects")
    public ApiResponse<List<ProjectResponse>> projects(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer limit) {
        String normalizedKeyword = trimToNull(keyword);
        int normalizedLimit = limit == null || limit < 1 ? 50 : Math.min(limit, 100);
        return ApiResponse.success(projectService.list().stream()
                .filter(project -> matchesProject(project, normalizedKeyword))
                .limit(normalizedLimit)
                .toList());
    }

    @GetMapping(value = {"/sse", "/tools"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse() {
        return mcpProjectKnowledgeSseService.open();
    }

    @PostMapping("/messages")
    public ResponseEntity<Void> messages(@RequestParam String sessionId,
                                         @RequestBody JsonNode payload) {
        mcpProjectKnowledgeSseService.handleMessage(sessionId, payload);
        return ResponseEntity.accepted().build();
    }

    private boolean matchesProject(ProjectResponse project, String keyword) {
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
