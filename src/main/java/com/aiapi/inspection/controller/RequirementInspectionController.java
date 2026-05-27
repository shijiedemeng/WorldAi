package com.aiapi.inspection.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.inspection.dto.RequirementInspectionResponse;
import com.aiapi.inspection.service.RequirementInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RequirementInspectionController {

    private final RequirementInspectionService requirementInspectionService;

    @GetMapping("/api/requirements/{requirementNo}/inspection")
    public ApiResponse<RequirementInspectionResponse> inspect(@PathVariable String requirementNo) {
        return ApiResponse.success(requirementInspectionService.inspect(requirementNo));
    }
}
