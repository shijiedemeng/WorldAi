package com.aiapi.session.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.session.dto.BindRequirementSessionRequest;
import com.aiapi.session.dto.RequirementSessionBindingResponse;
import com.aiapi.session.service.RequirementSessionBindingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requirements/{requirementNo}/sessions")
@RequiredArgsConstructor
public class RequirementSessionController {

    private final RequirementSessionBindingService requirementSessionBindingService;

    @GetMapping
    public ApiResponse<List<RequirementSessionBindingResponse>> list(@PathVariable String requirementNo) {
        return ApiResponse.success(requirementSessionBindingService.listByRequirement(requirementNo));
    }

    @PostMapping("/bind")
    public ApiResponse<RequirementSessionBindingResponse> bind(@PathVariable String requirementNo,
                                                               @Valid @RequestBody BindRequirementSessionRequest request) {
        return ApiResponse.success(requirementSessionBindingService.bind(requirementNo, request));
    }
}
