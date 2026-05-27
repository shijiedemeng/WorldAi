package com.aiapi.project.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.common.enums.ProjectDocumentUsage;
import com.aiapi.project.dto.CreateProjectRequest;
import com.aiapi.project.dto.ProjectMarkdownBaseUploadRequest;
import com.aiapi.project.dto.ProjectMarkdownConfigRequest;
import com.aiapi.project.dto.ProjectMarkdownConfigResponse;
import com.aiapi.project.dto.ProjectMarkdownFileHistoryResponse;
import com.aiapi.project.dto.ProjectMarkdownFileResponse;
import com.aiapi.project.dto.ProjectResponse;
import com.aiapi.project.dto.UpdateProjectRequest;
import com.aiapi.project.service.ProjectMarkdownService;
import com.aiapi.project.service.ProjectService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMarkdownService projectMarkdownService;

    @PostMapping
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.success(projectService.create(request));
    }

    @GetMapping("/{projectCode}")
    public ApiResponse<ProjectResponse> get(@PathVariable String projectCode) {
        return ApiResponse.success(projectService.getByProjectCode(projectCode));
    }

    @PutMapping("/{projectCode}")
    public ApiResponse<ProjectResponse> update(@PathVariable String projectCode,
                                               @Valid @RequestBody UpdateProjectRequest request) {
        return ApiResponse.success(projectService.update(projectCode, request));
    }

    @DeleteMapping("/{projectCode}")
    public ApiResponse<Void> delete(@PathVariable String projectCode) {
        projectService.delete(projectCode);
        return ApiResponse.success();
    }

    @GetMapping("/{projectCode}/markdown-config")
    public ApiResponse<ProjectMarkdownConfigResponse> getMarkdownConfig(@PathVariable String projectCode) {
        return ApiResponse.success(projectMarkdownService.getConfig(projectCode));
    }

    @PutMapping("/{projectCode}/markdown-config")
    public ApiResponse<ProjectMarkdownConfigResponse> updateMarkdownConfig(@PathVariable String projectCode,
                                                                          @Valid @RequestBody ProjectMarkdownConfigRequest request) {
        return ApiResponse.success(projectMarkdownService.updateConfig(projectCode, request));
    }

    @GetMapping("/{projectCode}/document-context/{usageType}")
    public ApiResponse<String> getDocumentContext(@PathVariable String projectCode,
                                                  @PathVariable ProjectDocumentUsage usageType) {
        return ApiResponse.success(projectMarkdownService.buildDocumentContext(projectCode, usageType));
    }

    @GetMapping("/{projectCode}/markdown-files/{fileId}/history")
    public ApiResponse<List<ProjectMarkdownFileHistoryResponse>> listMarkdownHistory(@PathVariable String projectCode,
                                                                                    @PathVariable Long fileId) {
        return ApiResponse.success(projectMarkdownService.listBaseFileHistory(projectCode, fileId));
    }

    @PostMapping("/{projectCode}/markdown-files/{fileId}/rollback/{historyId}")
    public ApiResponse<ProjectMarkdownFileResponse> rollbackMarkdownFile(@PathVariable String projectCode,
                                                                        @PathVariable Long fileId,
                                                                        @PathVariable Long historyId) {
        return ApiResponse.success(projectMarkdownService.rollbackBaseFile(projectCode, fileId, historyId));
    }

    @PostMapping("/{projectCode}/markdown-files/base-upload")
    public ApiResponse<ProjectMarkdownFileResponse> uploadBaseMarkdownFile(@PathVariable String projectCode,
                                                                          @Valid @RequestBody ProjectMarkdownBaseUploadRequest request) {
        return ApiResponse.success(projectMarkdownService.uploadBaseFile(projectCode, request));
    }

    @GetMapping
    public ApiResponse<List<ProjectResponse>> list() {
        return ApiResponse.success(projectService.list());
    }
}
