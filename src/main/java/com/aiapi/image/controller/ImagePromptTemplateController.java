package com.aiapi.image.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.image.dto.ImagePromptTemplatePageResponse;
import com.aiapi.image.dto.ImagePromptTemplateResponse;
import com.aiapi.image.dto.SaveImagePromptTemplateRequest;
import com.aiapi.image.service.ImagePromptTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/image-prompt-templates")
@RequiredArgsConstructor
public class ImagePromptTemplateController {

    private final ImagePromptTemplateService imagePromptTemplateService;

    @GetMapping
    public ApiResponse<List<ImagePromptTemplateResponse>> list(@RequestParam(required = false) String templateType,
                                                               @RequestParam(required = false) Boolean enabledFlag,
                                                               @RequestParam(required = false) String keyword) {
        return ApiResponse.success(imagePromptTemplateService.list(templateType, enabledFlag, keyword));
    }

    @GetMapping("/page")
    public ApiResponse<ImagePromptTemplatePageResponse> page(@RequestParam(required = false) String templateType,
                                                             @RequestParam(required = false) Boolean enabledFlag,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(imagePromptTemplateService.page(templateType, enabledFlag, keyword, page, pageSize));
    }

    @GetMapping("/{templateCode}")
    public ApiResponse<ImagePromptTemplateResponse> get(@PathVariable String templateCode) {
        return ApiResponse.success(imagePromptTemplateService.get(templateCode));
    }

    @PostMapping
    public ApiResponse<ImagePromptTemplateResponse> save(@Valid @RequestBody SaveImagePromptTemplateRequest request) {
        return ApiResponse.success(imagePromptTemplateService.save(request));
    }

    @DeleteMapping("/{templateCode}")
    public ApiResponse<Void> delete(@PathVariable String templateCode) {
        imagePromptTemplateService.delete(templateCode);
        return ApiResponse.success();
    }
}
