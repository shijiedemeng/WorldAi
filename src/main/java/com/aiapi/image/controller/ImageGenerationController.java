package com.aiapi.image.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.image.dto.CreateImageEditRequest;
import com.aiapi.image.dto.CreateImageGenerationRequest;
import com.aiapi.image.dto.ImageGenerationRecordPageResponse;
import com.aiapi.image.dto.ImageGenerationRecordResponse;
import com.aiapi.image.entity.ImageGenerationRecord;
import com.aiapi.image.service.ImageFileStorageService;
import com.aiapi.image.service.ImageGenerationAsyncService;
import com.aiapi.image.service.ImageGenerationService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/image-generations")
@RequiredArgsConstructor
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;
    private final ImageGenerationAsyncService imageGenerationAsyncService;
    private final ImageFileStorageService imageFileStorageService;

    @GetMapping("/page")
    public ApiResponse<ImageGenerationRecordPageResponse> page(@RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String generationType,
                                                               @RequestParam(required = false) String aiSettingKey,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(imageGenerationService.page(status, generationType, aiSettingKey, keyword, page, pageSize));
    }

    @GetMapping("/{recordId}")
    public ApiResponse<ImageGenerationRecordResponse> get(@PathVariable Long recordId) {
        return ApiResponse.success(imageGenerationService.get(recordId));
    }

    @PostMapping
    public ApiResponse<ImageGenerationRecordResponse> createTextToImage(@Valid @RequestBody CreateImageGenerationRequest request) {
        ImageGenerationRecordResponse response = imageGenerationService.createTextToImage(request);
        imageGenerationAsyncService.submit(response.getId());
        return ApiResponse.success(response);
    }

    @PostMapping(value = "/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageGenerationRecordResponse> createImageEdit(@ModelAttribute CreateImageEditRequest request,
                                                                      @RequestPart("image") MultipartFile image,
                                                                      @RequestPart(value = "mask", required = false) MultipartFile mask) {
        ImageGenerationRecordResponse response = imageGenerationService.createImageEdit(request, image, mask);
        imageGenerationAsyncService.submit(response.getId());
        return ApiResponse.success(response);
    }

    @PostMapping("/{recordId}/retry")
    public ApiResponse<ImageGenerationRecordResponse> retry(@PathVariable Long recordId) {
        ImageGenerationRecordResponse response = imageGenerationService.retryFailedRecord(recordId);
        imageGenerationAsyncService.submit(response.getId());
        return ApiResponse.success(response);
    }

    @GetMapping("/{recordId}/file")
    public ResponseEntity<FileSystemResource> getResultFile(@PathVariable Long recordId) {
        ImageGenerationRecord record = imageGenerationService.findRecord(recordId);
        return buildFileResponse(record.getResultFilePath(), record.getResultFileContentType(), record.getResultFileName());
    }

    @GetMapping("/{recordId}/source-file")
    public ResponseEntity<FileSystemResource> getSourceFile(@PathVariable Long recordId) {
        ImageGenerationRecord record = imageGenerationService.findRecord(recordId);
        return buildFileResponse(record.getSourceImagePath(), record.getSourceImageContentType(), record.getSourceImageFileName());
    }

    @GetMapping("/{recordId}/mask-file")
    public ResponseEntity<FileSystemResource> getMaskFile(@PathVariable Long recordId) {
        ImageGenerationRecord record = imageGenerationService.findRecord(recordId);
        return buildFileResponse(record.getMaskImagePath(), record.getMaskImageContentType(), record.getMaskImageFileName());
    }

    private ResponseEntity<FileSystemResource> buildFileResponse(String filePath, String contentType, String fileName) {
        FileSystemResource resource = imageFileStorageService.toResource(filePath);
        String normalizedFileName = fileName == null || fileName.isBlank() ? "image" : fileName;
        MediaType mediaType = contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(imageFileStorageService.size(filePath))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(normalizedFileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }
}
