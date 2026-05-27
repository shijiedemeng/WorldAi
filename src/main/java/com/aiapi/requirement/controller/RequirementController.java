package com.aiapi.requirement.controller;

import com.aiapi.client.dto.ClientCommandResponse;
import com.aiapi.client.service.ClientExecutionService;
import com.aiapi.common.api.ApiResponse;
import com.aiapi.requirement.dto.AnalyzeRequirementRequest;
import com.aiapi.requirement.dto.CreateRequirementRequest;
import com.aiapi.requirement.dto.RequirementResponse;
import com.aiapi.requirement.dto.RequirementTreeNodeResponse;
import com.aiapi.requirement.dto.RequirementWorkflowResultResponse;
import com.aiapi.requirement.dto.RequirementWorkflowResponse;
import com.aiapi.requirement.dto.SaveRequirementWorkflowRequest;
import com.aiapi.requirement.dto.UpdateRequirementRequest;
import com.aiapi.requirement.dto.UpdateRequirementDescriptionRequest;
import com.aiapi.requirement.dto.UpdateRequirementStatusRequest;
import com.aiapi.requirement.dto.UpdateRequirementSessionPreferenceRequest;
import com.aiapi.requirement.service.RequirementAiAnalysisService;
import com.aiapi.requirement.service.RequirementService;
import com.aiapi.requirement.service.RequirementWorkflowService;
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
@RequestMapping("/api/requirements")
@RequiredArgsConstructor
public class RequirementController {

    private final RequirementService requirementService;
    private final RequirementAiAnalysisService requirementAiAnalysisService;
    private final ClientExecutionService clientExecutionService;
    private final RequirementWorkflowService requirementWorkflowService;

    @PostMapping
    public ApiResponse<RequirementResponse> create(@Valid @RequestBody CreateRequirementRequest request) {
        return ApiResponse.success(requirementService.create(request));
    }

    @PostMapping("/masters")
    public ApiResponse<RequirementResponse> createMaster(@Valid @RequestBody CreateRequirementRequest request) {
        return ApiResponse.success(requirementService.createMaster(request));
    }

    @PostMapping("/{masterRequirementNo}/children")
    public ApiResponse<RequirementResponse> createChild(@PathVariable String masterRequirementNo,
                                                        @Valid @RequestBody CreateRequirementRequest request) {
        return ApiResponse.success(requirementService.createChild(masterRequirementNo, request));
    }

    @GetMapping("/{requirementNo}")
    public ApiResponse<RequirementResponse> get(@PathVariable String requirementNo) {
        return ApiResponse.success(requirementService.get(requirementNo));
    }

    @GetMapping
    public ApiResponse<List<RequirementResponse>> list(@RequestParam(required = false) String projectCode,
                                                       @RequestParam(required = false, defaultValue = "false") boolean openOnly,
                                                       @RequestParam(required = false) String agentCode) {
        return ApiResponse.success(openOnly
                ? requirementService.listOpenTasks(projectCode, agentCode)
                : requirementService.list(projectCode));
    }

    @GetMapping("/tree")
    public ApiResponse<List<RequirementTreeNodeResponse>> tree(@RequestParam(required = false) String projectCode,
                                                               @RequestParam(required = false) String rootRequirementNo) {
        return ApiResponse.success(requirementService.tree(projectCode, rootRequirementNo));
    }

    @GetMapping("/{masterRequirementNo}/children")
    public ApiResponse<List<RequirementResponse>> listChildren(@PathVariable String masterRequirementNo) {
        return ApiResponse.success(requirementService.listChildren(masterRequirementNo));
    }

    @GetMapping("/{requirementNo}/workflow-results")
    public ApiResponse<RequirementWorkflowResultResponse> workflowResults(@PathVariable String requirementNo) {
        return ApiResponse.success(requirementService.workflowResults(requirementNo));
    }

    @GetMapping("/{requirementNo}/workflow")
    public ApiResponse<RequirementWorkflowResponse> workflow(@PathVariable String requirementNo) {
        return ApiResponse.success(requirementWorkflowService.getWorkflow(requirementNo));
    }

    @PutMapping("/{requirementNo}/workflow")
    public ApiResponse<RequirementWorkflowResponse> saveWorkflow(@PathVariable String requirementNo,
                                                                 @Valid @RequestBody SaveRequirementWorkflowRequest request) {
        return ApiResponse.success(requirementWorkflowService.saveWorkflow(requirementNo, request));
    }

    @PostMapping("/{requirementNo}/workflow/dispatch-ready")
    public ApiResponse<List<ClientCommandResponse>> dispatchReadyWorkflow(@PathVariable String requirementNo) {
        return ApiResponse.success(clientExecutionService.dispatchReadyWorkflowTasks(requirementNo));
    }

    @PostMapping("/{requirementNo}/ai-analysis")
    public ApiResponse<RequirementResponse> aiAnalysis(@PathVariable String requirementNo,
                                                      @RequestBody(required = false) AnalyzeRequirementRequest request) {
        return ApiResponse.success(requirementAiAnalysisService.submitAnalysis(requirementNo, request));
    }

    @PutMapping("/{requirementNo}")
    public ApiResponse<RequirementResponse> update(@PathVariable String requirementNo,
                                                   @Valid @RequestBody UpdateRequirementRequest request) {
        return ApiResponse.success(requirementService.update(requirementNo, request));
    }

    @PutMapping("/{requirementNo}/description")
    public ApiResponse<RequirementResponse> updateDescription(@PathVariable String requirementNo,
                                                              @RequestBody UpdateRequirementDescriptionRequest request) {
        return ApiResponse.success(requirementService.updateDescription(requirementNo, request));
    }

    @PutMapping("/{requirementNo}/status")
    public ApiResponse<RequirementResponse> updateStatus(@PathVariable String requirementNo,
                                                         @Valid @RequestBody UpdateRequirementStatusRequest request) {
        return ApiResponse.success(requirementService.updateStatus(requirementNo, request));
    }

    @PostMapping("/{requirementNo}/reset-execution")
    public ApiResponse<RequirementResponse> resetExecution(@PathVariable String requirementNo) {
        return ApiResponse.success(requirementService.resetExecution(requirementNo));
    }

    @PostMapping("/{requirementNo}/review-approve")
    public ApiResponse<RequirementResponse> approveReview(@PathVariable String requirementNo) {
        return ApiResponse.success(requirementService.approveReview(requirementNo));
    }

    @PutMapping("/{requirementNo}/session-preference")
    public ApiResponse<RequirementResponse> updateSessionPreference(@PathVariable String requirementNo,
                                                                    @RequestBody UpdateRequirementSessionPreferenceRequest request) {
        return ApiResponse.success(requirementService.updateSessionPreference(requirementNo, request));
    }

    @DeleteMapping("/{requirementNo}")
    public ApiResponse<Void> delete(@PathVariable String requirementNo) {
        requirementService.delete(requirementNo);
        return ApiResponse.success();
    }
}
