package com.aiapi.testrecord.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.testrecord.dto.CreateTestRecordRequest;
import com.aiapi.testrecord.dto.TestRecordResponse;
import com.aiapi.testrecord.service.RequirementTestRecordService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RequirementTestRecordController {

    private final RequirementTestRecordService requirementTestRecordService;

    @PostMapping("/api/requirements/{requirementNo}/tests")
    public ApiResponse<TestRecordResponse> create(@PathVariable String requirementNo,
                                                  @Valid @RequestBody CreateTestRecordRequest request) {
        return ApiResponse.success(requirementTestRecordService.create(requirementNo, request));
    }

    @GetMapping("/api/requirements/{requirementNo}/tests")
    public ApiResponse<List<TestRecordResponse>> list(@PathVariable String requirementNo) {
        return ApiResponse.success(requirementTestRecordService.listByRequirementNo(requirementNo));
    }
}
