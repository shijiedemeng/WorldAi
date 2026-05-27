package com.aiapi.skill.controller;

import com.aiapi.common.api.ApiResponse;
import com.aiapi.skill.dto.SaveSkillRequest;
import com.aiapi.skill.dto.SkillPageResponse;
import com.aiapi.skill.dto.SkillResponse;
import com.aiapi.skill.entity.SkillInfo;
import com.aiapi.skill.service.SkillService;
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
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public ApiResponse<List<SkillResponse>> list(@RequestParam(required = false) Boolean enabledFlag) {
        return ApiResponse.success(skillService.list(enabledFlag));
    }

    @GetMapping("/page")
    public ApiResponse<SkillPageResponse> page(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Boolean enabledFlag,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer pageSize) {
        return ApiResponse.success(skillService.page(keyword, enabledFlag, page, pageSize));
    }

    @GetMapping("/{skillCode}")
    public ApiResponse<SkillResponse> get(@PathVariable String skillCode) {
        return ApiResponse.success(skillService.get(skillCode));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SkillResponse> create(@RequestBody SaveSkillRequest request) {
        return ApiResponse.success(skillService.create(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillResponse> createWithArchive(@RequestParam String skillCode,
                                                        @RequestParam String skillName,
                                                        @RequestParam(required = false) String skillDesc,
                                                        @RequestParam(required = false) Boolean enabledFlag,
                                                        @RequestPart("file") MultipartFile file) {
        SaveSkillRequest request = new SaveSkillRequest();
        request.setSkillCode(skillCode);
        request.setSkillName(skillName);
        request.setSkillDesc(skillDesc);
        request.setEnabledFlag(enabledFlag);
        return ApiResponse.success(skillService.createWithArchive(request, file));
    }

    @PutMapping("/{skillCode}")
    public ApiResponse<SkillResponse> update(@PathVariable String skillCode,
                                             @RequestBody SaveSkillRequest request) {
        return ApiResponse.success(skillService.update(skillCode, request));
    }

    @PostMapping(value = "/{skillCode}/archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillResponse> uploadArchive(@PathVariable String skillCode,
                                                    @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(skillService.uploadArchive(skillCode, file));
    }

    @GetMapping("/{skillCode}/archive")
    public ResponseEntity<ByteArrayResource> downloadArchive(@PathVariable String skillCode) {
        SkillInfo skill = skillService.findEntity(skillCode);
        byte[] content = skill.getArchiveContent();
        if (content == null || content.length == 0) {
            return ResponseEntity.notFound().build();
        }
        String fileName = skill.getArchiveFileName() == null ? skill.getSkillCode() + ".archive" : skill.getArchiveFileName();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (skill.getArchiveContentType() != null && !skill.getArchiveContentType().isBlank()) {
            mediaType = MediaType.parseMediaType(skill.getArchiveContentType());
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new ByteArrayResource(content));
    }

    @DeleteMapping("/{skillCode}")
    public ApiResponse<Void> delete(@PathVariable String skillCode) {
        skillService.delete(skillCode);
        return ApiResponse.success();
    }
}
