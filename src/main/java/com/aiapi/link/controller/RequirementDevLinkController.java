package com.aiapi.link.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.link.dto.CreateRequirementLinkRequest;
import com.aiapi.link.dto.RequirementLinkResponse;
import com.aiapi.link.dto.UpdateLinkProgressRequest;
import com.aiapi.link.service.RequirementDevLinkService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RequirementDevLinkController {

    private final RequirementDevLinkService requirementDevLinkService;

    @PostMapping("/api/requirements/{requirementNo}/links")
    public ApiResponse<RequirementLinkResponse> create(@PathVariable String requirementNo,
                                                       @Valid @RequestBody CreateRequirementLinkRequest request) {
        return ApiResponse.success(requirementDevLinkService.create(requirementNo, request));
    }

    @GetMapping("/api/requirements/{requirementNo}/links")
    public ApiResponse<List<RequirementLinkResponse>> listByRequirement(@PathVariable String requirementNo,
                                                                        @RequestParam(defaultValue = "false") boolean includeChildren) {
        return ApiResponse.success(requirementDevLinkService.listByRequirementNo(requirementNo, includeChildren));
    }

    @GetMapping("/api/agents/{agentCode}/tasks")
    public ApiResponse<List<RequirementLinkResponse>> listAgentTasks(@PathVariable String agentCode) {
        return ApiResponse.success(requirementDevLinkService.listAgentTasks(agentCode));
    }

    @PutMapping("/api/links/{linkId}/progress")
    public ApiResponse<RequirementLinkResponse> updateProgress(@PathVariable Long linkId,
                                                               @Valid @RequestBody UpdateLinkProgressRequest request) {
        return ApiResponse.success(requirementDevLinkService.updateProgress(linkId, request));
    }
}
