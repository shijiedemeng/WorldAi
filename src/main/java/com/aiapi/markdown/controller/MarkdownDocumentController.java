package com.aiapi.markdown.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.markdown.dto.MarkdownDocumentPageResponse;
import com.aiapi.markdown.dto.MarkdownDocumentRefsResponse;
import com.aiapi.markdown.dto.MarkdownDocumentResponse;
import com.aiapi.markdown.dto.MarkdownDocumentTreeNodeResponse;
import com.aiapi.markdown.dto.SaveMarkdownDocumentRequest;
import com.aiapi.markdown.entity.MarkdownDocument;
import com.aiapi.markdown.service.MarkdownDocumentService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/markdown-documents")
@RequiredArgsConstructor
public class MarkdownDocumentController {

    private final MarkdownDocumentService service;

    @GetMapping
    public ApiResponse<MarkdownDocumentPageResponse> page(@RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) String nodeType,
                                                          @RequestParam(required = false) String parentId,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(service.page(keyword, type, status, nodeType, parentId, page, pageSize));
    }

    @GetMapping("/tree")
    public ApiResponse<List<MarkdownDocumentTreeNodeResponse>> tree() {
        return ApiResponse.success(service.tree());
    }

    @GetMapping("/{documentId}")
    public ApiResponse<MarkdownDocumentResponse> get(@PathVariable String documentId) {
        return ApiResponse.success(service.get(documentId));
    }

    @PostMapping
    public ApiResponse<MarkdownDocumentResponse> create(@Valid @RequestBody SaveMarkdownDocumentRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MarkdownDocumentResponse> upload(@RequestPart("file") MultipartFile file,
                                                        @RequestParam(required = false) Boolean overwrite) {
        return ApiResponse.success(service.upload(file, overwrite));
    }

    @PutMapping("/{documentId}")
    public ApiResponse<MarkdownDocumentResponse> update(@PathVariable String documentId,
                                                        @Valid @RequestBody SaveMarkdownDocumentRequest request) {
        return ApiResponse.success(service.update(documentId, request));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(@PathVariable String documentId) {
        service.delete(documentId);
        return ApiResponse.success();
    }

    @GetMapping("/{documentId}/subtree")
    public ApiResponse<List<MarkdownDocumentTreeNodeResponse>> subtree(@PathVariable String documentId) {
        return ApiResponse.success(service.subtree(documentId));
    }

    @GetMapping("/{documentId}/path")
    public ApiResponse<List<MarkdownDocumentResponse>> path(@PathVariable String documentId) {
        return ApiResponse.success(service.path(documentId));
    }

    @GetMapping("/{documentId}/refs")
    public ApiResponse<MarkdownDocumentRefsResponse> refs(@PathVariable String documentId) {
        return ApiResponse.success(service.refs(documentId));
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String documentId) {
        MarkdownDocument document = service.findEntity(documentId);
        if ("FOLDER".equalsIgnoreCase(document.getNodeType())) {
            throw new com.aiapi.common.exception.BizException(400, "folder cannot be downloaded");
        }
        byte[] content = document.getContent().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getDocumentId() + ".md", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new ByteArrayResource(content));
    }
}
