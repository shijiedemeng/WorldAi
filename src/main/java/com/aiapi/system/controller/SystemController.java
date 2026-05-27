package com.aiapi.system.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.common.enums.AiModelPurpose;
import com.aiapi.system.dto.AiModelSettingResponse;
import com.aiapi.system.dto.SaveAiModelSettingRequest;
import com.aiapi.system.service.AiModelSettingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final AiModelSettingService aiModelSettingService;

    @GetMapping("/ai-settings")
    public ApiResponse<List<AiModelSettingResponse>> listAiSettings(@RequestParam(required = false) Boolean supportImageFlag,
                                                                    @RequestParam(required = false) Boolean enabledFlag,
                                                                    @RequestParam(required = false) AiModelPurpose modelPurpose) {
        return ApiResponse.success(aiModelSettingService.list(supportImageFlag, enabledFlag, modelPurpose));
    }

    @PostMapping("/ai-settings")
    public ApiResponse<AiModelSettingResponse> saveAiSetting(@Valid @RequestBody SaveAiModelSettingRequest request) {
        return ApiResponse.success(aiModelSettingService.save(request));
    }
}
