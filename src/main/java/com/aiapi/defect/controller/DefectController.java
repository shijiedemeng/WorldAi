package com.aiapi.defect.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.defect.dto.AnalyzeDefectRequest;
import com.aiapi.defect.dto.DefectAnalysisResponse;
import com.aiapi.defect.dto.DefectRemoteQueryRequest;
import com.aiapi.defect.dto.DefectSourceConfigResponse;
import com.aiapi.defect.dto.FetchYunxiaoOrganizationsRequest;
import com.aiapi.defect.dto.FetchYunxiaoProjectMembersRequest;
import com.aiapi.defect.dto.FetchYunxiaoProjectsRequest;
import com.aiapi.defect.dto.RemoteDefectPageResponse;
import com.aiapi.defect.dto.PushDefectToRequirementRequest;
import com.aiapi.defect.dto.RemoteDefectResponse;
import com.aiapi.defect.dto.SaveDefectSourceConfigRequest;
import com.aiapi.defect.dto.YunxiaoProjectMemberResponse;
import com.aiapi.defect.dto.YunxiaoProjectResponse;
import com.aiapi.defect.dto.YunxiaoOrganizationResponse;
import com.aiapi.defect.service.DefectRecordService;
import com.aiapi.defect.service.DefectSourceConfigService;
import com.aiapi.defect.service.YunxiaoMetadataService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

@RestController
@RequestMapping("/api/defects")
@RequiredArgsConstructor
public class DefectController {

    private final DefectSourceConfigService defectSourceConfigService;
    private final DefectRecordService defectRecordService;
    private final YunxiaoMetadataService yunxiaoMetadataService;

    @GetMapping("/sources")
    public ApiResponse<List<DefectSourceConfigResponse>> listSources(@RequestParam(required = false) String projectCode) {
        return ApiResponse.success(defectSourceConfigService.list(projectCode));
    }

    @GetMapping("/sources/{sourceCode}")
    public ApiResponse<DefectSourceConfigResponse> getSource(@PathVariable String sourceCode) {
        return ApiResponse.success(defectSourceConfigService.findBySourceCode(sourceCode));
    }

    @PostMapping("/sources")
    public ApiResponse<DefectSourceConfigResponse> saveSource(@Valid @RequestBody SaveDefectSourceConfigRequest request) {
        return ApiResponse.success(defectSourceConfigService.save(request));
    }

    @PostMapping("/yunxiao/organizations")
    public ApiResponse<List<YunxiaoOrganizationResponse>> listYunxiaoOrganizations(
            @Valid @RequestBody FetchYunxiaoOrganizationsRequest request) {
        return ApiResponse.success(yunxiaoMetadataService.listOrganizations(request));
    }

    @PostMapping("/yunxiao/projects")
    public ApiResponse<List<YunxiaoProjectResponse>> listYunxiaoProjects(
            @Valid @RequestBody FetchYunxiaoProjectsRequest request) {
        return ApiResponse.success(yunxiaoMetadataService.listProjects(request));
    }

    @PostMapping("/yunxiao/project-members")
    public ApiResponse<List<YunxiaoProjectMemberResponse>> listYunxiaoProjectMembers(
            @Valid @RequestBody FetchYunxiaoProjectMembersRequest request) {
        return ApiResponse.success(yunxiaoMetadataService.listProjectMembers(request));
    }

    @GetMapping("/sources/{sourceCode}/yunxiao/project")
    public ApiResponse<YunxiaoProjectResponse> getYunxiaoProject(@PathVariable String sourceCode,
                                                                  @RequestParam(required = false) String projectId) {
        return ApiResponse.success(yunxiaoMetadataService.fetchProject(sourceCode, projectId));
    }

    @GetMapping("/sources/{sourceCode}/yunxiao/members")
    public ApiResponse<List<YunxiaoProjectMemberResponse>> listSourceYunxiaoMembers(@PathVariable String sourceCode,
                                                                                    @RequestParam(required = false) String name,
                                                                                    @RequestParam(required = false) String roleId) {
        return ApiResponse.success(yunxiaoMetadataService.listSourceProjectMembers(sourceCode, name, roleId));
    }

    @GetMapping("/sources/{sourceCode}/remote-defects")
    public ApiResponse<RemoteDefectPageResponse> viewRemoteDefects(@PathVariable String sourceCode,
                                                                   @ModelAttribute DefectRemoteQueryRequest request) {
        return ApiResponse.success(defectRecordService.viewRemoteDefects(sourceCode, request));
    }

    @GetMapping("/sources/{sourceCode}/remote-defects/{externalDefectId}")
    public ApiResponse<RemoteDefectResponse> viewRemoteDefectDetail(@PathVariable String sourceCode,
                                                                    @PathVariable String externalDefectId) {
        return ApiResponse.success(defectRecordService.viewRemoteDefectDetail(sourceCode, externalDefectId));
    }

    @GetMapping("/analyses")
    public ApiResponse<List<DefectAnalysisResponse>> listAnalyses(@RequestParam String projectCode) {
        return ApiResponse.success(defectRecordService.listAnalyses(projectCode));
    }

    @PostMapping("/analyze")
    public ApiResponse<DefectAnalysisResponse> analyze(@Valid @RequestBody AnalyzeDefectRequest request) {
        return ApiResponse.success(defectRecordService.analyze(request));
    }

    @PostMapping("/analyses/{analysisNo}/push")
    public ApiResponse<DefectAnalysisResponse> pushToRequirement(@PathVariable String analysisNo,
                                                                 @Valid @RequestBody PushDefectToRequirementRequest request) {
        return ApiResponse.success(defectRecordService.pushToRequirement(analysisNo, request));
    }
}
