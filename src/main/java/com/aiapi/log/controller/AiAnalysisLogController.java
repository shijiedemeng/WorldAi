package com.aiapi.log.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.log.dto.AiAnalysisLogPageResponse;
import com.aiapi.log.dto.AiAnalysisLogResponse;
import com.aiapi.log.service.AiAnalysisLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class AiAnalysisLogController {

    private final AiAnalysisLogService aiAnalysisLogService;

    @GetMapping("/ai-analysis")
    public ApiResponse<AiAnalysisLogPageResponse> listAiAnalysisLogs(
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(aiAnalysisLogService.list(sourceType, projectCode, businessNo, status, keyword, page, pageSize));
    }

    @GetMapping("/ai-analysis/{id}")
    public ApiResponse<AiAnalysisLogResponse> getAiAnalysisLog(@PathVariable Long id) {
        return ApiResponse.success(aiAnalysisLogService.detail(id));
    }
}
