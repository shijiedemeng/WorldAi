package com.aiapi.image.service;

import com.aiapi.common.enums.ImageGenerationStatus;
import com.aiapi.common.enums.ImageGenerationType;
import com.aiapi.common.enums.ImagePromptTemplateType;
import com.aiapi.common.exception.BizException;
import com.aiapi.image.dto.CreateImageEditRequest;
import com.aiapi.image.dto.CreateImageGenerationRequest;
import com.aiapi.image.dto.ImageGenerationRecordPageResponse;
import com.aiapi.image.dto.ImageGenerationRecordResponse;
import com.aiapi.image.entity.ImageGenerationRecord;
import com.aiapi.image.entity.ImagePromptTemplate;
import com.aiapi.image.repository.ImageGenerationRecordRepository;
import com.aiapi.image.repository.ImagePromptTemplateRepository;
import com.aiapi.system.entity.AiModelSetting;
import com.aiapi.system.repository.AiModelSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private static final int RESPONSE_PAYLOAD_DB_LIMIT = 200_000;
    private static final List<String> SIZE_OPTIONS = List.of("auto", "1024x1024", "1536x1024", "1024x1536", "1536x864", "3840x2160");
    private static final List<String> QUALITY_OPTIONS = List.of("low", "medium", "high", "auto");
    private static final List<String> OUTPUT_FORMAT_OPTIONS = List.of("png", "jpeg");
    private static final List<String> BACKGROUND_OPTIONS = List.of("opaque", "auto");
    private static final List<String> MODERATION_OPTIONS = List.of("auto", "low");
    private static final List<String> RESPONSE_FORMAT_OPTIONS = List.of("b64_json");
    private static final List<String> INPUT_FIDELITY_OPTIONS = List.of("high", "auto");

    private final ImageGenerationRecordRepository recordRepository;
    private final ImagePromptTemplateRepository promptTemplateRepository;
    private final AiModelSettingRepository aiModelSettingRepository;
    private final ImageFileStorageService imageFileStorageService;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Value("${ai.image.connect-timeout-ms:30000}")
    private long connectTimeoutMs;

    @Value("${ai.image.read-timeout-ms:1800000}")
    private long readTimeoutMs;

    @Transactional(readOnly = true)
    public ImageGenerationRecordPageResponse page(String status,
                                                  String generationType,
                                                  String aiSettingKey,
                                                  String keyword,
                                                  Integer page,
                                                  Integer pageSize) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<ImageGenerationRecord> result = recordRepository.findAll(
                buildSpecification(status, generationType, aiSettingKey, keyword),
                PageRequest.of(normalizedPage - 1, normalizedPageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ImageGenerationRecordPageResponse.builder()
                .items(result.getContent().stream().map(this::toResponse).toList())
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public ImageGenerationRecordResponse get(Long recordId) {
        return toResponse(findRecord(recordId));
    }

    public ImageGenerationRecordResponse createTextToImage(CreateImageGenerationRequest request) {
        AiModelSetting setting = resolveImageSetting(request.getAiSettingKey());
        PromptBuildResult prompt = buildPrompt(setting, request.getPromptTemplateCodes(), request.getNegativeTemplateCodes(),
                request.getPositivePromptLines(), request.getNegativePromptLines());
        ImageGenerationRecord record = newRecord(ImageGenerationType.TEXT_TO_IMAGE, setting, prompt);
        applyOptions(record, request.getSize(), request.getQuality(), request.getOutputFormat(), request.getBackground(),
                request.getModeration(), request.getResponseFormat(), null, request.getN(), request.getUser());
        record.setRequestPayload(buildRequestPayload(resolveEndpoint(setting, "/images/generations"), buildApiBody(record)));
        ImageGenerationRecord saved = recordRepository.saveAndFlush(record);
        return toResponse(saved);
    }

    public ImageGenerationRecordResponse createImageEdit(CreateImageEditRequest request, MultipartFile imageFile, MultipartFile maskFile) {
        AiModelSetting setting = resolveImageSetting(request.getAiSettingKey());
        PromptBuildResult prompt = buildPrompt(setting, request.getPromptTemplateCodes(), request.getNegativeTemplateCodes(),
                request.getPositivePromptLines(), request.getNegativePromptLines());
        ImageGenerationRecord record = newRecord(ImageGenerationType.IMAGE_EDIT, setting, prompt);
        applyOptions(record, request.getSize(), request.getQuality(), request.getOutputFormat(), request.getBackground(),
                request.getModeration(), request.getResponseFormat(), request.getInputFidelity(), request.getN(), request.getUser());
        ImageGenerationRecord saved = recordRepository.saveAndFlush(record);
        ImageFileStorageService.StoredFile source = imageFileStorageService.saveSourceImage(saved.getId(), imageFile);
        saved.setSourceImageFileName(source.fileName());
        saved.setSourceImagePath(source.path());
        saved.setSourceImageContentType(source.contentType());
        saved.setSourceImageSize(source.size());
        if (maskFile != null && !maskFile.isEmpty()) {
            ImageFileStorageService.StoredFile mask = imageFileStorageService.saveMaskImage(saved.getId(), maskFile);
            saved.setMaskImageFileName(mask.fileName());
            saved.setMaskImagePath(mask.path());
            saved.setMaskImageContentType(mask.contentType());
            saved.setMaskImageSize(mask.size());
        }
        saved.setRequestPayload(buildRequestPayload(resolveEndpoint(setting, "/images/edits"), buildApiBody(saved)));
        saved = recordRepository.saveAndFlush(saved);
        return toResponse(saved);
    }

    @Transactional
    public ImageGenerationRecordResponse retryFailedRecord(Long recordId) {
        ImageGenerationRecord record = findRecord(recordId);
        if (record.getStatus() != ImageGenerationStatus.FAILED) {
            throw new BizException(400, "only failed image generation record can be retried");
        }
        record.setStatus(ImageGenerationStatus.PENDING);
        record.setResponsePayload(null);
        record.setErrorMessage(null);
        record.setStartedAt(null);
        record.setFinishedAt(null);
        record.setResultFileName(null);
        record.setResultFilePath(null);
        record.setResultFileContentType(null);
        record.setResultFileSize(null);
        return toResponse(recordRepository.saveAndFlush(record));
    }

    public void processRecord(Long recordId) {
        ImageGenerationRecord running = markRunning(recordId);
        String responsePayload = null;
        try {
            AiModelSetting setting = resolveImageSetting(running.getAiSettingKey());
            ImageApiResult result;
            if (running.getGenerationType() == ImageGenerationType.IMAGE_EDIT) {
                result = callImageEdit(setting, running);
            } else {
                result = callTextToImage(setting, running);
            }
            responsePayload = result.responsePayload();
            ImageFileStorageService.StoredFile responseFile = saveRawResponseIfNeeded(running.getId(), responsePayload);
            ImageFileStorageService.StoredFile storedFile = imageFileStorageService.saveResultImage(
                    running.getId(), result.content(), running.getOutputFormat(), result.contentType());
            markSuccess(running.getId(), storedFile, buildDatabaseResponsePayload(responsePayload, responseFile));
        } catch (RestClientResponseException ex) {
            markFailed(recordId, buildHttpErrorPayload(ex), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            markFailed(recordId, responsePayload == null ? null : sanitizeImageResponse(responsePayload), ex.getMessage());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ImageGenerationRecord findRecord(Long recordId) {
        return recordRepository.findById(recordId)
                .orElseThrow(() -> new BizException(404, "image generation record not found"));
    }

    private ImageGenerationRecord newRecord(ImageGenerationType type, AiModelSetting setting, PromptBuildResult prompt) {
        ImageGenerationRecord record = new ImageGenerationRecord();
        record.setGenerationType(type);
        record.setAiSettingKey(setting.getSettingKey());
        record.setProviderName(setting.getProviderName());
        record.setModelName(setting.getModelName());
        record.setPromptTemplateCodes(joinCodes(prompt.positiveTemplateCodes()));
        record.setNegativeTemplateCodes(joinCodes(prompt.negativeTemplateCodes()));
        record.setPositivePromptText(prompt.positivePromptText());
        record.setNegativePromptText(prompt.negativePromptText());
        record.setFinalPrompt(prompt.finalPrompt());
        record.setStatus(ImageGenerationStatus.PENDING);
        return record;
    }

    private void applyOptions(ImageGenerationRecord record,
                              String size,
                              String quality,
                              String outputFormat,
                              String background,
                              String moderation,
                              String responseFormat,
                              String inputFidelity,
                              Integer n,
                              String user) {
        record.setImageSize(normalizeOption(size, SIZE_OPTIONS, "1024x1024", "size"));
        record.setQuality(normalizeOption(quality, QUALITY_OPTIONS, "high", "quality"));
        record.setOutputFormat(normalizeOption(outputFormat, OUTPUT_FORMAT_OPTIONS, "png", "outputFormat"));
        record.setBackground(normalizeOption(background, BACKGROUND_OPTIONS, "opaque", "background"));
        record.setModeration(normalizeOption(moderation, MODERATION_OPTIONS, "auto", "moderation"));
        record.setResponseFormat(normalizeOption(responseFormat, RESPONSE_FORMAT_OPTIONS, "b64_json", "responseFormat"));
        record.setInputFidelity(record.getGenerationType() == ImageGenerationType.IMAGE_EDIT
                ? normalizeOption(inputFidelity, INPUT_FIDELITY_OPTIONS, "high", "inputFidelity")
                : null);
        record.setImageCount(1);
        record.setUserText(trimToNull(user));
        if (n != null && n != 1) {
            throw new BizException(400, "image generation only supports n=1");
        }
    }

    private PromptBuildResult buildPrompt(AiModelSetting setting,
                                          List<String> positiveTemplateCodes,
                                          List<String> negativeTemplateCodes,
                                          List<String> positivePromptLines,
                                          List<String> negativePromptLines) {
        List<String> positiveCodes = normalizeCodeList(positiveTemplateCodes);
        List<String> negativeCodes = normalizeCodeList(negativeTemplateCodes);
        String positiveTemplates = joinTemplateContents(loadTemplates(positiveCodes, ImagePromptTemplateType.POSITIVE));
        String negativeTemplates = joinTemplateContents(loadTemplates(negativeCodes, ImagePromptTemplateType.NEGATIVE));
        String customPositive = joinPromptLines(positivePromptLines);
        String customNegative = joinPromptLines(negativePromptLines);
        String positiveText = joinNonBlankValues(positiveTemplates, customPositive);
        String negativeText = joinNonBlankValues(negativeTemplates, customNegative);
        List<String> sections = new ArrayList<>();
        String settingPrompt = trimToNull(setting.getPromptTemplate());
        if (settingPrompt != null) {
            sections.add("基础要求：\n" + settingPrompt);
        }
        if (positiveText != null) {
            sections.add("正向提示词：\n" + positiveText);
        }
        if (negativeText != null) {
            sections.add("负向约束：\n请避免：" + "\n" + negativeText);
        }
        String finalPrompt = joinNonBlank(sections);
        if (finalPrompt == null) {
            throw new BizException(400, "image prompt is required");
        }
        return new PromptBuildResult(positiveCodes, negativeCodes, positiveText, negativeText, finalPrompt);
    }

    private List<ImagePromptTemplate> loadTemplates(List<String> codes, ImagePromptTemplateType expectedType) {
        if (codes.isEmpty()) {
            return List.of();
        }
        Map<String, ImagePromptTemplate> templateMap = promptTemplateRepository.findByTemplateCodeIn(codes).stream()
                .collect(Collectors.toMap(ImagePromptTemplate::getTemplateCode, Function.identity()));
        List<ImagePromptTemplate> result = new ArrayList<>();
        for (String code : codes) {
            ImagePromptTemplate template = templateMap.get(code);
            if (template == null) {
                throw new BizException(400, "image prompt template not found: " + code);
            }
            if (template.getTemplateType() != expectedType) {
                throw new BizException(400, "image prompt template type mismatch: " + code);
            }
            if (!Boolean.TRUE.equals(template.getEnabledFlag())) {
                throw new BizException(400, "image prompt template is disabled: " + code);
            }
            result.add(template);
        }
        return result;
    }

    private ImageApiResult callTextToImage(AiModelSetting setting, ImageGenerationRecord record) {
        String endpoint = resolveEndpoint(setting, "/images/generations");
        Map<String, Object> body = buildApiBody(record);
        ResponseEntity<String> response = restTemplate().exchange(endpoint, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders(setting)), String.class);
        String responsePayload = response.getBody() == null ? "" : response.getBody();
        ExtractedImage extractedImage = extractImage(responsePayload, record.getOutputFormat());
        return new ImageApiResult(extractedImage.content(), extractedImage.contentType(), responsePayload);
    }

    private ImageApiResult callImageEdit(AiModelSetting setting, ImageGenerationRecord record) {
        String endpoint = resolveEndpoint(setting, "/images/edits");
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        buildApiBody(record).forEach((key, value) -> {
            if (value != null) {
                form.add(key, String.valueOf(value));
            }
        });
        form.add("image", new FileSystemResource(record.getSourceImagePath()));
        if (trimToNull(record.getMaskImagePath()) != null) {
            form.add("mask", new FileSystemResource(record.getMaskImagePath()));
        }
        ResponseEntity<String> response = restTemplate().exchange(endpoint, HttpMethod.POST, new HttpEntity<>(form, multipartHeaders(setting)), String.class);
        String responsePayload = response.getBody() == null ? "" : response.getBody();
        ExtractedImage extractedImage = extractImage(responsePayload, record.getOutputFormat());
        return new ImageApiResult(extractedImage.content(), extractedImage.contentType(), responsePayload);
    }

    private Map<String, Object> buildApiBody(ImageGenerationRecord record) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", record.getModelName());
        body.put("prompt", record.getFinalPrompt());
        body.put("size", record.getImageSize());
        body.put("quality", record.getQuality());
        body.put("output_format", record.getOutputFormat());
        body.put("background", record.getBackground());
        body.put("moderation", record.getModeration());
        body.put("response_format", record.getResponseFormat());
        body.put("n", record.getImageCount());
        if (record.getGenerationType() == ImageGenerationType.IMAGE_EDIT && trimToNull(record.getInputFidelity()) != null) {
            body.put("input_fidelity", record.getInputFidelity());
        }
        if (trimToNull(record.getUserText()) != null) {
            body.put("user", record.getUserText());
        }
        return body;
    }

    private ImageGenerationRecord markRunning(Long recordId) {
        ImageGenerationRecord record = findRecord(recordId);
        record.setStatus(ImageGenerationStatus.RUNNING);
        record.setStartedAt(LocalDateTime.now());
        record.setErrorMessage(null);
        return recordRepository.saveAndFlush(record);
    }

    private void markSuccess(Long recordId, ImageFileStorageService.StoredFile storedFile, String responsePayload) {
        ImageGenerationRecord record = findRecord(recordId);
        record.setStatus(ImageGenerationStatus.SUCCESS);
        record.setResultFileName(storedFile.fileName());
        record.setResultFilePath(storedFile.path());
        record.setResultFileContentType(storedFile.contentType());
        record.setResultFileSize(storedFile.size());
        record.setResponsePayload(responsePayload);
        record.setErrorMessage(null);
        record.setFinishedAt(LocalDateTime.now());
        recordRepository.save(record);
    }

    private void markFailed(Long recordId, String responsePayload, String errorMessage) {
        ImageGenerationRecord record = findRecord(recordId);
        record.setStatus(ImageGenerationStatus.FAILED);
        record.setResponsePayload(trimToNull(responsePayload));
        record.setErrorMessage(truncate(errorMessage, 4000));
        record.setFinishedAt(LocalDateTime.now());
        recordRepository.save(record);
    }

    private AiModelSetting resolveImageSetting(String settingKey) {
        String normalized = required(settingKey, "aiSettingKey");
        AiModelSetting setting = aiModelSettingRepository.findBySettingKey(normalized)
                .orElseThrow(() -> new BizException(404, "ai image setting not found"));
        if (!Boolean.TRUE.equals(setting.getEnabledFlag())) {
            throw new BizException(400, "ai image setting is disabled");
        }
        if (!Boolean.TRUE.equals(setting.getSupportImageFlag())) {
            throw new BizException(400, "ai setting does not support image generation");
        }
        if (trimToNull(setting.getBaseUrl()) == null) {
            throw new BizException(400, "ai image setting baseUrl is required");
        }
        if (trimToNull(setting.getApiKey()) == null) {
            throw new BizException(400, "ai image setting apiKey is required");
        }
        if (trimToNull(setting.getModelName()) == null) {
            throw new BizException(400, "ai image setting modelName is required");
        }
        return setting;
    }

    private ExtractedImage extractImage(String responsePayload, String outputFormat) {
        try {
            JsonNode root = objectMapper.readTree(responsePayload);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode first = data.get(0);
                String b64 = text(first, "b64_json");
                if (b64 != null) {
                    return new ExtractedImage(decodeBase64Image(b64), contentTypeFor(outputFormat));
                }
                String url = text(first, "url");
                if (url != null) {
                    ResponseEntity<byte[]> response = restTemplate().getForEntity(url, byte[].class);
                    byte[] body = response.getBody();
                    if (body != null && body.length > 0) {
                        String contentType = response.getHeaders().getContentType() == null
                                ? contentTypeFor(outputFormat)
                                : response.getHeaders().getContentType().toString();
                        return new ExtractedImage(body, contentType);
                    }
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new BizException(502, "failed to parse image api response: " + ex.getMessage());
        }
        throw new BizException(502, "image api response missing b64_json");
    }

    private Specification<ImageGenerationRecord> buildSpecification(String status,
                                                                     String generationType,
                                                                     String aiSettingKey,
                                                                     String keyword) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            ImageGenerationStatus normalizedStatus = parseStatus(status);
            if (normalizedStatus != null) {
                predicates.add(builder.equal(root.get("status"), normalizedStatus));
            }
            ImageGenerationType normalizedType = parseGenerationType(generationType);
            if (normalizedType != null) {
                predicates.add(builder.equal(root.get("generationType"), normalizedType));
            }
            String normalizedSettingKey = trimToNull(aiSettingKey);
            if (normalizedSettingKey != null) {
                predicates.add(builder.equal(root.get("aiSettingKey"), normalizedSettingKey));
            }
            String normalizedKeyword = trimToNull(keyword);
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword.toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("aiSettingKey")), pattern),
                        builder.like(builder.lower(root.get("modelName")), pattern),
                        builder.like(builder.lower(root.get("finalPrompt")), pattern),
                        builder.like(builder.lower(root.get("errorMessage")), pattern)
                ));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private ImageGenerationRecordResponse toResponse(ImageGenerationRecord entity) {
        return ImageGenerationRecordResponse.builder()
                .id(entity.getId())
                .generationType(entity.getGenerationType())
                .aiSettingKey(entity.getAiSettingKey())
                .providerName(entity.getProviderName())
                .modelName(entity.getModelName())
                .promptTemplateCodes(entity.getPromptTemplateCodes())
                .negativeTemplateCodes(entity.getNegativeTemplateCodes())
                .positivePromptText(entity.getPositivePromptText())
                .negativePromptText(entity.getNegativePromptText())
                .finalPrompt(entity.getFinalPrompt())
                .imageSize(entity.getImageSize())
                .quality(entity.getQuality())
                .outputFormat(entity.getOutputFormat())
                .background(entity.getBackground())
                .moderation(entity.getModeration())
                .responseFormat(entity.getResponseFormat())
                .inputFidelity(entity.getInputFidelity())
                .userText(entity.getUserText())
                .imageCount(entity.getImageCount())
                .sourceImageFileName(entity.getSourceImageFileName())
                .sourceImagePath(entity.getSourceImagePath())
                .sourceImageContentType(entity.getSourceImageContentType())
                .sourceImageSize(entity.getSourceImageSize())
                .sourceImageUrl(entity.getSourceImagePath() == null ? null : "/api/image-generations/" + entity.getId() + "/source-file")
                .maskImageFileName(entity.getMaskImageFileName())
                .maskImagePath(entity.getMaskImagePath())
                .maskImageContentType(entity.getMaskImageContentType())
                .maskImageSize(entity.getMaskImageSize())
                .maskImageUrl(entity.getMaskImagePath() == null ? null : "/api/image-generations/" + entity.getId() + "/mask-file")
                .resultFileName(entity.getResultFileName())
                .resultFilePath(entity.getResultFilePath())
                .resultFileContentType(entity.getResultFileContentType())
                .resultFileSize(entity.getResultFileSize())
                .resultFileUrl(entity.getResultFilePath() == null ? null : "/api/image-generations/" + entity.getId() + "/file")
                .status(entity.getStatus())
                .requestPayload(entity.getRequestPayload())
                .responsePayload(entity.getResponsePayload())
                .errorMessage(entity.getErrorMessage())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String buildRequestPayload(String url, Map<String, Object> body) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("url", url);
            payload.put("body", body);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private ImageFileStorageService.StoredFile saveRawResponseIfNeeded(Long recordId, String responsePayload) {
        if (responsePayload == null || responsePayload.isBlank()) {
            return null;
        }
        if (!containsBase64Image(responsePayload) && responsePayload.length() <= RESPONSE_PAYLOAD_DB_LIMIT) {
            return null;
        }
        return imageFileStorageService.saveResponsePayload(recordId, responsePayload);
    }

    private boolean containsBase64Image(String responsePayload) {
        return responsePayload != null && responsePayload.contains("b64_json");
    }

    private String buildDatabaseResponsePayload(String responsePayload, ImageFileStorageService.StoredFile responseFile) {
        String sanitized = sanitizeImageResponse(responsePayload);
        if (responseFile == null) {
            return sanitized;
        }
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("message", "原始响应已保存到本地文件，数据库只保存摘要，避免 b64_json 大内容入库。");
        summary.put("rawResponseFileName", responseFile.fileName());
        summary.put("rawResponseFilePath", responseFile.path());
        summary.put("rawResponseContentType", responseFile.contentType());
        summary.put("rawResponseSize", responseFile.size());
        try {
            summary.set("responseSummary", objectMapper.readTree(sanitized));
        } catch (Exception ex) {
            summary.put("responseSummary", truncate(sanitized, 8000));
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
        } catch (Exception ex) {
            return truncate(sanitized, 8000);
        }
    }

    private String sanitizeImageResponse(String responsePayload) {
        if (responsePayload == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responsePayload);
            scrubBase64(root);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception ex) {
            return truncate(responsePayload, 8000);
        }
    }

    private void scrubBase64(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            if (objectNode.has("b64_json")) {
                objectNode.remove("b64_json");
                objectNode.put("b64JsonStored", true);
            }
            objectNode.fields().forEachRemaining(entry -> scrubBase64(entry.getValue()));
            return;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            arrayNode.forEach(this::scrubBase64);
        }
    }

    private String buildHttpErrorPayload(RestClientResponseException ex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("statusCode", ex.getStatusCode().value());
        payload.put("statusText", ex.getStatusText());
        payload.put("body", truncate(ex.getResponseBodyAsString(), 8000));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception ignored) {
            return truncate(ex.getResponseBodyAsString(), 8000);
        }
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(normalizeTimeoutMs(connectTimeoutMs, 30_000L)))
                .setReadTimeout(Duration.ofMillis(normalizeTimeoutMs(readTimeoutMs, 1_800_000L)))
                .build();
    }

    private long normalizeTimeoutMs(long value, long defaultValue) {
        return value <= 0 ? defaultValue : value;
    }

    private HttpHeaders jsonHeaders(AiModelSetting setting) {
        HttpHeaders headers = baseHeaders(setting);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders multipartHeaders(AiModelSetting setting) {
        HttpHeaders headers = baseHeaders(setting);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private HttpHeaders baseHeaders(AiModelSetting setting) {
        HttpHeaders headers = new HttpHeaders();
        String apiKey = setting.getApiKey().trim();
        if (apiKey.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            headers.set(HttpHeaders.AUTHORIZATION, apiKey);
        } else {
            headers.setBearerAuth(apiKey);
        }
        headers.setAccept(List.of(MediaType.ALL));
        return headers;
    }

    private String resolveEndpoint(AiModelSetting setting, String path) {
        String baseUrl = required(setting.getBaseUrl(), "baseUrl");
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/v1")) {
            return normalized + path;
        }
        return normalized + "/v1" + path;
    }

    private ImageGenerationStatus parseStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return ImageGenerationStatus.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(400, "invalid image generation status");
        }
    }

    private ImageGenerationType parseGenerationType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return ImageGenerationType.valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(400, "invalid image generation type");
        }
    }

    private String normalizeOption(String value, Collection<String> options, String defaultValue, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return defaultValue;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!options.contains(normalized)) {
            throw new BizException(400, "invalid " + fieldName);
        }
        return normalized;
    }

    private List<String> normalizeCodeList(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            String trimmed = trimToNull(code);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    private String joinTemplateContents(List<ImagePromptTemplate> templates) {
        if (templates.isEmpty()) {
            return null;
        }
        return joinNonBlank(templates.stream().map(ImagePromptTemplate::getContentText).toList());
    }

    private String joinPromptLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        return joinNonBlank(lines);
    }

    private String joinCodes(List<String> codes) {
        return codes == null || codes.isEmpty() ? null : String.join(",", codes);
    }

    private String joinNonBlank(List<String> values) {
        List<String> normalized = values.stream()
                .map(this::trimToNull)
                .filter(item -> item != null)
                .toList();
        return normalized.isEmpty() ? null : String.join("\n", normalized);
    }

    private String joinNonBlankValues(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        return normalized.isEmpty() ? null : String.join("\n", normalized);
    }

    private String contentTypeFor(String outputFormat) {
        return "jpeg".equals(outputFormat) ? "image/jpeg" : "image/png";
    }

    private byte[] decodeBase64Image(String value) {
        String normalized = value;
        int commaIndex = normalized.indexOf(',');
        if (normalized.startsWith("data:") && commaIndex >= 0) {
            normalized = normalized.substring(commaIndex + 1);
        }
        return Base64.getDecoder().decode(normalized);
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String required(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(400, fieldName + " is required");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...(truncated)";
    }

    private record PromptBuildResult(List<String> positiveTemplateCodes,
                                     List<String> negativeTemplateCodes,
                                     String positivePromptText,
                                     String negativePromptText,
                                     String finalPrompt) {
    }

    private record ExtractedImage(byte[] content, String contentType) {
    }

    private record ImageApiResult(byte[] content, String contentType, String responsePayload) {
    }
}
