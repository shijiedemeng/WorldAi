package com.aiapi.defect.integration;

import com.aiapi.common.exception.BizException;
import com.aiapi.defect.dto.DefectRemoteQueryRequest;
import com.aiapi.defect.entity.DefectSyncAccount;
import com.aiapi.defect.entity.DefectSyncProject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class ZentaoDefectPlatformClient implements DefectPlatformClient {

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public DefectPlatformBugPage fetchBugs(DefectSyncAccount account, DefectSyncProject syncProject, DefectRemoteQueryRequest query) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        String token = resolveToken(restTemplate, account);
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        int page = normalizePage(query == null ? null : query.getPage());
        int pageSize = normalizePageSize(query == null ? null : query.getPageSize());
        JsonNode root = fetchBugListRoot(restTemplate, account.getBaseUrl(), syncProject.getExternalProjectKey(), headers, page, pageSize, query);
        List<JsonNode> bugs = extractBugNodes(root);
        List<DefectPlatformBug> items = bugs.stream().map(this::toBug).toList();
        long total = readTotal(root, items.size(), page, pageSize);
        return DefectPlatformBugPage.builder()
                .items(items)
                .page(page)
                .pageSize(pageSize)
                .total(total)
                .totalPages(total == 0 ? 0 : (int) Math.ceil((double) total / pageSize))
                .build();
    }

    @Override
    public DefectPlatformBug fetchBug(DefectSyncAccount account, DefectSyncProject syncProject, String externalDefectId) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        String token = resolveToken(restTemplate, account);
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode detailNode = fetchBugDetail(restTemplate, account.getBaseUrl(), externalDefectId, headers);
        return toBug(detailNode);
    }

    private String resolveToken(RestTemplate restTemplate, DefectSyncAccount account) {
        if (account.getAccessToken() != null && !account.getAccessToken().isBlank()) {
            return account.getAccessToken().trim();
        }
        if (account.getUsername() == null || account.getUsername().isBlank()
                || account.getPasswordValue() == null || account.getPasswordValue().isBlank()) {
            throw new BizException(400, "zentao account requires username/password or accessToken");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String payload = objectMapper.createObjectNode()
                    .put("account", account.getUsername())
                    .put("password", account.getPasswordValue())
                    .toString();
            ResponseEntity<String> response = restTemplate.exchange(
                    normalizeBaseUrl(account.getBaseUrl()) + "/api.php/v1/tokens",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String token = root.path("token").asText();
            if (token == null || token.isBlank()) {
                throw new BizException(400, "zentao token missing in response");
            }
            return token;
        } catch (RestClientException | JsonProcessingException ex) {
            throw new BizException(400, "zentao login failed: " + ex.getMessage());
        }
    }

    private JsonNode fetchBugListRoot(RestTemplate restTemplate,
                                      String baseUrl,
                                      String externalProjectKey,
                                      HttpHeaders headers,
                                      int page,
                                      int limit,
                                      DefectRemoteQueryRequest query) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(normalizeBaseUrl(baseUrl) + "/api.php/v1/products/" + externalProjectKey + "/bugs")
                .queryParam("page", page)
                .queryParam("limit", limit)
                .queryParam("order", resolveZentaoOrder(query));
        addQueryParam(builder, "keyword", query == null ? null : query.getKeyword());
        addQueryParam(builder, "status", query == null ? null : query.getStatus());
        addQueryParam(builder, "severity", query == null ? null : query.getSeverity());
        addQueryParam(builder, "assignedTo", query == null ? null : query.getAssignedTo());
        addQueryParam(builder, "openedBy", query == null ? null : query.getReporterName());
        try {
            ResponseEntity<String> response = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException | JsonProcessingException ex) {
            throw new BizException(400, "zentao bug list fetch failed: " + ex.getMessage());
        }
    }

    private List<JsonNode> extractBugNodes(JsonNode root) {
        List<JsonNode> bugs = new ArrayList<>();
        if (root == null || root.isNull()) {
            return bugs;
        }
        if (root.isArray()) {
            root.forEach(bugs::add);
            return bugs;
        }
        if (root.has("data") && root.get("data").isArray()) {
            root.get("data").forEach(bugs::add);
            return bugs;
        }
        if (root.has("bugs") && root.get("bugs").isArray()) {
            root.get("bugs").forEach(bugs::add);
            return bugs;
        }
        String[] arrayFields = {"data", "items", "list", "result", "bugs"};
        for (String field : arrayFields) {
            JsonNode node = root.get(field);
            if (node != null && node.isObject()) {
                List<JsonNode> nested = extractBugNodes(node);
                if (!nested.isEmpty()) {
                    return nested;
                }
            }
        }
        Iterator<JsonNode> iterator = root.elements();
        while (iterator.hasNext()) {
            JsonNode node = iterator.next();
            if (node.has("id")) {
                bugs.add(node);
            }
        }
        return bugs;
    }

    private JsonNode fetchBugDetail(RestTemplate restTemplate, String baseUrl, String bugId, HttpHeaders headers) {
        String url = normalizeBaseUrl(baseUrl) + "/api.php/v1/bugs/" + bugId;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException | JsonProcessingException ex) {
            throw new BizException(400, "zentao bug detail fetch failed: " + ex.getMessage());
        }
    }

    private DefectPlatformBug toBug(JsonNode bug) {
        String title = bug.path("title").asText();
        String steps = stripHtml(bug.path("steps").asText(null));
        String story = stripHtml(bug.path("story").asText(null));
        String summary = title + (steps == null || steps.isBlank() ? "" : "\n\n" + steps);
        List<DefectPlatformComment> comments = extractComments(bug);
        return DefectPlatformBug.builder()
                .externalDefectId(readText(bug, "id"))
                .externalDefectKey(readText(bug, "id"))
                .title(title)
                .severity(firstNonBlank(readText(bug, "severity"), readText(bug, "pri")))
                .defectStatusId(readText(bug, "status"))
                .defectStatus(readText(bug, "status"))
                .defectType(readText(bug, "type"))
                .assignedTo(readNestedName(bug.get("assignedTo")))
                .reporterName(readNestedName(bug.get("openedBy")))
                .openedAt(parseDateTime(readText(bug, "openedDate")))
                .updatedAtRemote(parseDateTime(firstNonBlank(readText(bug, "lastEditedDate"), readText(bug, "assignedDate"))))
                .hasImageFlag(containsImage(bug))
                .tags(firstNonBlank(readText(bug, "keywords"), readText(bug, "os")))
                .summary(summary)
                .descriptionText(firstNonBlank(steps, story, stripHtml(bug.path("title").asText())))
                .rawPayload(bug.toPrettyString())
                .comments(comments)
                .build();
    }

    private String resolveZentaoOrder(DefectRemoteQueryRequest query) {
        String orderBy = trimToNull(query == null ? null : query.getOrderBy());
        String sort = trimToNull(query == null ? null : query.getSort());
        if (sort == null) {
            sort = "desc";
        }
        String field = "lastEditedDate";
        if ("createdAt".equals(orderBy) || "openedAt".equals(orderBy)) {
            field = "openedDate";
        } else if ("title".equals(orderBy)) {
            field = "title";
        }
        return field + "_" + sort;
    }

    private void addQueryParam(UriComponentsBuilder builder, String name, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            builder.queryParam(name, normalized);
        }
    }

    private long readTotal(JsonNode root, int itemCount, int page, int pageSize) {
        Long total = firstLong(root, "total", "totalCount", "count");
        if (total != null) {
            return total;
        }
        JsonNode pageNode = root == null ? null : root.get("page");
        total = firstLong(pageNode, "total", "totalCount", "count");
        if (total != null) {
            return total;
        }
        return (long) (page - 1) * pageSize + itemCount;
    }

    private Long firstLong(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToLong()) {
                return value.asLong();
            }
        }
        return null;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private List<DefectPlatformComment> extractComments(JsonNode bug) {
        List<DefectPlatformComment> comments = new ArrayList<>();
        JsonNode actions = bug.get("actions");
        if (actions == null) {
            return comments;
        }
        if (actions.isArray()) {
            for (JsonNode item : actions) {
                comments.add(toComment(item));
            }
            return comments;
        }
        Iterator<JsonNode> iterator = actions.elements();
        while (iterator.hasNext()) {
            comments.add(toComment(iterator.next()));
        }
        return comments;
    }

    private DefectPlatformComment toComment(JsonNode action) {
        return DefectPlatformComment.builder()
                .externalCommentId(readText(action, "id"))
                .authorName(readNestedName(action.get("actor")))
                .commentContent(firstNonBlank(stripHtml(readText(action, "comment")), readText(action, "action"), readText(action, "objectType")))
                .commentedAt(parseDateTime(firstNonBlank(readText(action, "date"), readText(action, "createdDate"))))
                .build();
    }

    private String readNestedName(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String realName = firstNonBlank(readText(node, "realName"), readText(node, "name"), readText(node, "account"));
        return trimToNull(realName);
    }

    private String readText(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String stripHtml(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private Boolean containsImage(JsonNode bug) {
        String payload = bug.toString().toLowerCase(Locale.ROOT);
        return payload.contains("<img") || payload.contains(".png") || payload.contains(".jpg") || payload.contains(".jpeg");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
