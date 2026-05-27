package com.aiapi.knowledge.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.common.enums.ProjectKnowledgeStatus;
import com.aiapi.common.enums.ProjectKnowledgeType;
import com.aiapi.common.enums.ProjectKnowledgeVectorStatus;
import com.aiapi.knowledge.dto.ConfirmProjectKnowledgeRequest;
import com.aiapi.knowledge.dto.CreateKnowledgeFromRequirementRequest;
import com.aiapi.knowledge.dto.CreateProjectKnowledgeRequest;
import com.aiapi.knowledge.dto.OrganizeProjectKnowledgeRequest;
import com.aiapi.knowledge.dto.ProjectKnowledgePageResponse;
import com.aiapi.knowledge.dto.ProjectKnowledgeResponse;
import com.aiapi.knowledge.dto.ProjectKnowledgeSearchResponse;
import com.aiapi.knowledge.dto.SearchProjectKnowledgeRequest;
import com.aiapi.knowledge.dto.UpdateProjectKnowledgeRequest;
import com.aiapi.knowledge.service.ProjectKnowledgeReserveService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project-knowledge")
@RequiredArgsConstructor
public class ProjectKnowledgeReserveController {

    private final ProjectKnowledgeReserveService service;

    @GetMapping
    public ApiResponse<ProjectKnowledgePageResponse> page(@RequestParam(required = false) String projectCode,
                                                          @RequestParam(required = false) ProjectKnowledgeType knowledgeType,
                                                          @RequestParam(required = false) ProjectKnowledgeStatus status,
                                                          @RequestParam(required = false) ProjectKnowledgeVectorStatus vectorStatus,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(service.page(projectCode, knowledgeType, status, vectorStatus, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProjectKnowledgeResponse> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @GetMapping("/by-requirement/{requirementNo}")
    public ApiResponse<List<ProjectKnowledgeResponse>> byRequirement(@PathVariable String requirementNo) {
        return ApiResponse.success(service.listByRequirement(requirementNo));
    }

    @PostMapping
    public ApiResponse<ProjectKnowledgeResponse> create(@Valid @RequestBody CreateProjectKnowledgeRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PostMapping("/from-requirements/{requirementNo}")
    public ApiResponse<ProjectKnowledgeResponse> createFromRequirement(@PathVariable String requirementNo,
                                                                       @Valid @RequestBody CreateKnowledgeFromRequirementRequest request) {
        return ApiResponse.success(service.createFromRequirement(requirementNo, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectKnowledgeResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateProjectKnowledgeRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @PostMapping("/{id}/organize")
    public ApiResponse<ProjectKnowledgeResponse> organize(@PathVariable Long id,
                                                          @RequestBody(required = false) OrganizeProjectKnowledgeRequest request) {
        return ApiResponse.success(service.organize(id, request));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ProjectKnowledgeResponse> confirm(@PathVariable Long id,
                                                         @RequestBody(required = false) ConfirmProjectKnowledgeRequest request) {
        return ApiResponse.success(service.confirmAndVectorize(id, request));
    }

    @PostMapping("/{id}/similar")
    public ApiResponse<ProjectKnowledgeSearchResponse> similar(@PathVariable Long id,
                                                               @RequestParam(required = false) String embeddingSettingKey,
                                                               @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(service.similar(id, embeddingSettingKey, limit));
    }

    @PostMapping("/search")
    public ApiResponse<ProjectKnowledgeSearchResponse> search(@Valid @RequestBody SearchProjectKnowledgeRequest request) {
        return ApiResponse.success(service.search(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }
}
