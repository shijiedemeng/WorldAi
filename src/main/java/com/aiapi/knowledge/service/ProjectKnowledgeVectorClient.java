package com.aiapi.knowledge.service;

import com.aiapi.common.exception.BizException;
import com.aiapi.knowledge.dto.ProjectKnowledgeSearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ProjectKnowledgeVectorClient {

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Value("${ai.knowledge.vector-base-url:http://127.0.0.1:8091}")
    private String vectorBaseUrl;

    @Value("${ai.knowledge.connect-timeout-ms:10000}")
    private long connectTimeoutMs;

    @Value("${ai.knowledge.read-timeout-ms:60000}")
    private long readTimeoutMs;

    public VectorUpsertResult upsert(String projectCode,
                                     String vectorId,
                                     List<Double> embedding,
                                     Map<String, Object> metadata,
                                     String content) {
        String normalizedProjectCode = requireProjectCode(projectCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectCode", normalizedProjectCode);
        body.put("project_code", normalizedProjectCode);
        body.put("id", vectorId);
        body.put("vector", embedding);
        body.put("metadata", metadata);
        body.put("content", content);
        JsonNode response = post("/vectors/upsert", body);
        return new VectorUpsertResult(
                response.path("collection").asText(null),
                response.path("id").asText(vectorId),
                response.path("dbPath").asText(null));
    }

    public List<ProjectKnowledgeSearchResponse.Item> search(String projectCode,
                                                            List<Double> embedding,
                                                            int limit) {
        String normalizedProjectCode = requireProjectCode(projectCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectCode", normalizedProjectCode);
        body.put("project_code", normalizedProjectCode);
        body.put("vector", embedding);
        body.put("limit", limit);
        JsonNode response = post("/vectors/search", body);
        List<ProjectKnowledgeSearchResponse.Item> result = new ArrayList<>();
        JsonNode items = response.path("items");
        if (!items.isArray()) {
            return result;
        }
        for (JsonNode item : items) {
            JsonNode metadata = item.path("metadata");
            result.add(ProjectKnowledgeSearchResponse.Item.builder()
                    .id(metadata.path("knowledgeId").isNumber() ? metadata.path("knowledgeId").asLong() : null)
                    .title(metadata.path("title").asText("-"))
                    .simpleDesc(metadata.path("simpleDesc").asText(null))
                    .content(item.path("content").asText(null))
                    .knowledgeType(metadata.path("knowledgeType").asText(null))
                    .score(item.path("score").isNumber() ? item.path("score").asDouble() : null)
                    .build());
        }
        return result;
    }

    public void delete(String projectCode, String vectorId) {
        String normalizedProjectCode = requireProjectCode(projectCode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectCode", normalizedProjectCode);
        body.put("project_code", normalizedProjectCode);
        body.put("id", vectorId);
        post("/vectors/delete", body);
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            byte[] requestBody = objectMapper.writeValueAsBytes(body);
            ResponseEntity<byte[]> response = restTemplate().exchange(
                    normalizeBaseUrl() + path,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, jsonHeaders()),
                    byte[].class);
            byte[] responseBody = response.getBody();
            if (responseBody == null || responseBody.length == 0) {
                throw new BizException(502, "vector service returned empty response");
            }
            return objectMapper.readTree(responseBody);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(502, "vector service request failed: " + ex.getMessage());
        }
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String normalizeBaseUrl() {
        String baseUrl = vectorBaseUrl == null || vectorBaseUrl.isBlank()
                ? "http://127.0.0.1:8091"
                : vectorBaseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String requireProjectCode(String projectCode) {
        if (projectCode == null || projectCode.isBlank()) {
            throw new BizException(400, "projectCode is required before vector request");
        }
        return projectCode.trim();
    }

    public record VectorUpsertResult(String collection, String vectorId, String dbPath) {
    }
}
