package com.aiapi.defect.integration;

import com.aiapi.common.exception.BizException;
import com.aiapi.defect.dto.DefectRemoteQueryRequest;
import com.aiapi.defect.dto.YunxiaoProjectResponse;
import com.aiapi.defect.entity.DefectSyncAccount;
import com.aiapi.defect.entity.DefectSyncProject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class YunxiaoDefectPlatformClient implements DefectPlatformClient {

    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 200;

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public DefectPlatformBugPage fetchBugs(DefectSyncAccount account, DefectSyncProject syncProject, DefectRemoteQueryRequest query) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        YunxiaoConfig config = readConfig(account);
        HttpHeaders headers = buildHeaders(account);
        int page = normalizePage(query == null ? null : query.getPage());
        int pageSize = normalizePageSize(query == null ? null : query.getPageSize());
        ResponseEntity<String> response = searchWorkItemsPage(restTemplate, account, syncProject, config, headers, query, page, pageSize);
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            ensureSuccess(root, "yunxiao work item search failed");
            List<JsonNode> workItems = extractArrayLike(root);
            List<DefectPlatformBug> items = workItems.stream()
                    .map(workItem -> toBug(workItem, List.of(), List.of()))
                    .toList();
            Long total = readHeaderLong(response.getHeaders(), "x-total");
            Integer totalPages = readHeaderInt(response.getHeaders(), "x-total-pages");
            long resolvedTotal = total == null ? (long) (page - 1) * pageSize + items.size() : total;
            int resolvedTotalPages = totalPages == null
                    ? (resolvedTotal == 0 ? 0 : (int) Math.ceil((double) resolvedTotal / pageSize))
                    : totalPages;
            return DefectPlatformBugPage.builder()
                    .items(items)
                    .page(page)
                    .pageSize(pageSize)
                    .total(resolvedTotal)
                    .totalPages(resolvedTotalPages)
                    .build();
        } catch (JsonProcessingException ex) {
            throw new BizException(400, "yunxiao work item search failed: " + ex.getMessage());
        }
    }

    @Override
    public DefectPlatformBug fetchBug(DefectSyncAccount account, DefectSyncProject syncProject, String externalDefectId) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        YunxiaoConfig config = readConfig(account);
        HttpHeaders headers = buildHeaders(account);
        JsonNode detail = fetchWorkItem(restTemplate, account, config, headers, externalDefectId);
        List<DefectPlatformComment> comments = fetchComments(restTemplate, account, config, headers, externalDefectId);
        List<DefectPlatformAttachment> attachments = fetchAttachments(restTemplate, account, config, headers, externalDefectId);
        return toBug(unwrapWorkItem(detail), comments, attachments);
    }

    public List<YunxiaoOrganization> fetchOrganizations(String baseUrl, String accessToken, String userId) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        HttpHeaders headers = buildHeaders(accessToken);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(normalizeBaseUrl(baseUrl) + "/oapi/v1/platform/organizations")
                .queryParam("page", 1)
                .queryParam("perPage", 100);
        String trimmedUserId = trimToNull(userId);
        if (trimmedUserId != null) {
            uriBuilder.queryParam("userId", trimmedUserId);
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uriBuilder.toUriString(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return extractArrayLike(root).stream().map(this::toOrganization).toList();
        } catch (RestClientException | JsonProcessingException ex) {
            throw new BizException(400, "yunxiao organization list fetch failed: " + ex.getMessage());
        }
    }

    public List<YunxiaoProjectResponse> fetchProjects(String baseUrl, String accessToken, String organizationId) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        HttpHeaders headers = buildHeaders(accessToken);
        String normalizedOrganizationId = trimToNull(organizationId);
        if (normalizedOrganizationId == null) {
            throw new BizException(400, "yunxiao organizationId is required");
        }
        List<YunxiaoProjectResponse> result = new ArrayList<>();
        int page = 1;
        int guard = 0;
        while (guard++ < 50) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("orderBy", "gmtCreate");
            payload.put("page", page);
            payload.put("perPage", MAX_PAGE_SIZE);
            payload.put("sort", "desc");
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        normalizeBaseUrl(baseUrl) + "/oapi/v1/projex/organizations/" + normalizedOrganizationId + "/projects:search",
                        HttpMethod.POST,
                        new HttpEntity<>(payload.toString(), headers),
                        String.class);
                JsonNode root = objectMapper.readTree(response.getBody());
                ensureSuccess(root, "yunxiao project list fetch failed");
                List<JsonNode> projects = extractArrayLike(root);
                for (JsonNode project : projects) {
                    result.add(toProject(project));
                }
                Integer totalPages = readHeaderInt(response.getHeaders(), "x-total-pages");
                if (totalPages != null) {
                    if (page >= totalPages) {
                        break;
                    }
                    page++;
                    continue;
                }
                Integer nextPage = readHeaderInt(response.getHeaders(), "x-next-page");
                if (nextPage != null && nextPage > page) {
                    page = nextPage;
                    continue;
                }
                if (projects.size() < MAX_PAGE_SIZE) {
                    break;
                }
                page++;
            } catch (RestClientException | JsonProcessingException ex) {
                throw new BizException(400, "yunxiao project list fetch failed: " + ex.getMessage());
            }
        }
        return result;
    }

    public YunxiaoProjectResponse fetchProject(String baseUrl,
                                               String accessToken,
                                               String organizationId,
                                               String projectId) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        HttpHeaders headers = buildHeaders(accessToken);
        String normalizedProjectId = trimToNull(projectId);
        if (normalizedProjectId == null) {
            throw new BizException(400, "yunxiao project id is required");
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    normalizeBaseUrl(baseUrl) + "/oapi/v1/projex/organizations/" + organizationId + "/projects/" + normalizedProjectId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return toProject(root);
        } catch (RestClientException | JsonProcessingException ex) {
            throw new BizException(400, "yunxiao project fetch failed: " + ex.getMessage());
        }
    }

    public List<YunxiaoProjectMember> fetchProjectMembers(String baseUrl,
                                                          String accessToken,
                                                          String organizationId,
                                                          String projectId,
                                                          String name,
                                                          String roleId) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        HttpHeaders headers = buildHeaders(accessToken);
        String normalizedOrganizationId = trimToNull(organizationId);
        String normalizedProjectId = trimToNull(projectId);
        if (normalizedOrganizationId == null) {
            throw new BizException(400, "yunxiao organizationId is required");
        }
        if (normalizedProjectId == null) {
            throw new BizException(400, "yunxiao projectId is required");
        }
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(normalizeBaseUrl(baseUrl))
                .pathSegment("oapi", "v1", "projex", "organizations", normalizedOrganizationId, "projects", normalizedProjectId, "members");
        addQueryParam(uriBuilder, "name", name);
        addQueryParam(uriBuilder, "roleId", roleId);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    uriBuilder.toUriString(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            ensureSuccess(root, "yunxiao project member list fetch failed");
            return extractArrayLike(root).stream().map(this::toProjectMember).toList();
        } catch (RestClientException | JsonProcessingException ex) {
            throw new BizException(400, "yunxiao project member list fetch failed: " + ex.getMessage());
        }
    }

    private ResponseEntity<String> searchWorkItemsPage(RestTemplate restTemplate,
                                                       DefectSyncAccount account,
                                                       DefectSyncProject syncProject,
                                                       YunxiaoConfig config,
                                                       HttpHeaders headers,
                                                       DefectRemoteQueryRequest query,
                                                       int page,
                                                       int perPage) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("category", config.category());
        payload.put("orderBy", resolveYunxiaoOrderBy(config, query));
        payload.put("page", page);
        payload.put("perPage", perPage);
        payload.put("sort", resolveSort(config, query));
        String spaceId = trimToNull(syncProject.getExternalProjectKey());
        if (spaceId == null) {
            throw new BizException(400, "yunxiao project is required, please select one from the project list first");
        }
        payload.put("spaceId", spaceId);
        payload.put("spaceType", config.spaceType());
        String conditions = buildYunxiaoConditions(config.conditions(), query);
        if (conditions != null) {
            payload.put("conditions", conditions);
        }
        try {
            return restTemplate.exchange(
                    buildWorkItemBaseUrl(account, config) + ":search",
                    HttpMethod.POST,
                    new HttpEntity<>(payload.toString(), headers),
                    String.class);
        } catch (RestClientException ex) {
            throw new BizException(400, "yunxiao work item search failed: " + ex.getMessage());
        }
    }

    private JsonNode fetchWorkItem(RestTemplate restTemplate,
                                   DefectSyncAccount account,
                                   YunxiaoConfig config,
                                   HttpHeaders headers,
                                   String externalDefectId) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    buildWorkItemBaseUrl(account, config) + "/" + externalDefectId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException | JsonProcessingException ex) {
            throw new BizException(400, "yunxiao work item detail fetch failed: " + ex.getMessage());
        }
    }

    private List<DefectPlatformComment> fetchComments(RestTemplate restTemplate,
                                                      DefectSyncAccount account,
                                                      YunxiaoConfig config,
                                                      HttpHeaders headers,
                                                      String externalDefectId) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    buildWorkItemBaseUrl(account, config) + "/" + externalDefectId + "/comments",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return extractArrayLike(root).stream().map(this::toComment).toList();
        } catch (RestClientException | JsonProcessingException ex) {
            throw new BizException(400, "yunxiao work item comments fetch failed: " + ex.getMessage());
        }
    }

    private List<DefectPlatformAttachment> fetchAttachments(RestTemplate restTemplate,
                                                           DefectSyncAccount account,
                                                           YunxiaoConfig config,
                                                           HttpHeaders headers,
                                                           String externalDefectId) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    buildWorkItemBaseUrl(account, config) + "/" + externalDefectId + "/attachments",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return extractArrayLike(root).stream().map(this::toAttachment).toList();
        } catch (RestClientException | JsonProcessingException ex) {
            throw new BizException(400, "yunxiao work item attachments fetch failed: " + ex.getMessage());
        }
    }

    private HttpHeaders buildHeaders(DefectSyncAccount account) {
        return buildHeaders(account.getAccessToken());
    }

    private HttpHeaders buildHeaders(String accessToken) {
        String token = trimToNull(accessToken);
        if (token == null) {
            throw new BizException(400, "yunxiao account requires accessToken");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-yunxiao-token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private YunxiaoOrganization toOrganization(JsonNode item) {
        return YunxiaoOrganization.builder()
                .id(readText(item, "id"))
                .name(readText(item, "name"))
                .description(readText(item, "description"))
                .creatorId(readText(item, "creatorId"))
                .defaultRole(readText(item, "defaultRole"))
                .createdAt(parseDateTime(readText(item, "createdAt")))
                .updatedAt(parseDateTime(firstNonBlank(readText(item, "updateAt"), readText(item, "updatedAt"))))
                .build();
    }

    private YunxiaoProjectResponse toProject(JsonNode root) {
        JsonNode project = firstObject(root, "project", "data", "result");
        if (project == null) {
            project = root;
        }
        String projectId = firstNonBlank(readText(project, "id"), readText(project, "identifier"), readText(root, "id"), readText(root, "identifier"));
        if (projectId == null) {
            throw new BizException(400, "yunxiao project response missing id");
        }
        String name = trimToNull(readText(project, "name"));
        if (name == null) {
            throw new BizException(400, "yunxiao project response missing name");
        }
        return YunxiaoProjectResponse.builder()
                .id(projectId)
                .name(name)
                .description(trimToNull(readText(project, "description")))
                .customCode(trimToNull(readText(project, "customCode")))
                .scope(trimToNull(readText(project, "scope")))
                .creatorId(firstNonBlank(readNestedText(project, "creator", "id"), readText(project, "creator"), readText(project, "creatorId")))
                .creatorName(readNestedName(project.get("creator")))
                .modifierId(firstNonBlank(readNestedText(project, "modifier", "id"), readText(project, "modifier"), readText(project, "modifierId")))
                .modifierName(readNestedName(project.get("modifier")))
                .statusId(firstNonBlank(readNestedText(project, "status", "id"), readText(project, "statusStageIdentifier")))
                .statusName(firstNonBlank(readNestedText(project, "status", "name"), readText(project, "logicalStatus")))
                .createdAt(parseDateTime(firstNonBlank(readText(project, "gmtCreate"), readText(project, "createdAt"))))
                .updatedAt(parseDateTime(firstNonBlank(readText(project, "gmtModified"), readText(project, "updatedAt"))))
                .build();
    }

    private String buildWorkItemBaseUrl(DefectSyncAccount account, YunxiaoConfig config) {
        String organizationId = config.organizationId();
        if (organizationId == null) {
            throw new BizException(400, "yunxiao extraConfig.organizationId is required");
        }
        String basePath = config.regionMode()
                ? "/oapi/v1/projex/" + organizationId + "/workitems"
                : "/oapi/v1/projex/organizations/" + organizationId + "/workitems";
        return normalizeBaseUrl(account.getBaseUrl()) + basePath;
    }

    private DefectPlatformBug toBug(JsonNode workItem,
                                    List<DefectPlatformComment> comments,
                                    List<DefectPlatformAttachment> attachments) {
        String title = firstNonBlank(readText(workItem, "subject"), readText(workItem, "title"), readText(workItem, "name"));
        String description = readRichText(workItem, "description");
        String statusId = firstNonBlank(readNestedText(workItem, "status", "id"), readText(workItem, "statusStageId"));
        String status = firstNonBlank(
                readNestedText(workItem, "status", "displayName"),
                readNestedText(workItem, "status", "name"),
                readNestedText(workItem, "status", "nameEn"),
                YunxiaoDefectStatusCatalog.labelOf(statusId));
        String type = firstNonBlank(readNestedText(workItem, "workitemType", "name"), readText(workItem, "categoryId"));
        String serialNumber = readText(workItem, "serialNumber");
        String rawPayload = appendDetailPayload(workItem, comments, attachments);
        return DefectPlatformBug.builder()
                .externalDefectId(readText(workItem, "id"))
                .externalDefectKey(firstNonBlank(serialNumber, readText(workItem, "id")))
                .title(defaultText(title, "未命名工作项"))
                .severity(readCustomField(workItem, "severity", "严重", "优先级", "priority"))
                .defectStatusId(statusId)
                .defectStatus(status)
                .defectType(type)
                .assignedTo(readNestedName(workItem.get("assignedTo")))
                .reporterName(readNestedName(workItem.get("creator")))
                .openedAt(parseDateTime(firstNonBlank(readText(workItem, "gmtCreate"), readText(workItem, "createdAt"))))
                .updatedAtRemote(parseDateTime(firstNonBlank(readText(workItem, "gmtModified"), readText(workItem, "updateStatusAt"), readText(workItem, "updatedAt"))))
                .hasImageFlag(containsImage(workItem) || attachments.stream().anyMatch(this::isImageAttachment))
                .tags(readLabels(workItem))
                .summary(buildSummary(workItem, description, comments, attachments))
                .descriptionText(description)
                .rawPayload(rawPayload)
                .comments(comments)
                .attachments(attachments)
                .build();
    }

    private DefectPlatformComment toComment(JsonNode item) {
        return DefectPlatformComment.builder()
                .externalCommentId(readText(item, "id"))
                .authorName(readNestedName(item.get("user")))
                .commentContent(readRichText(item, "content"))
                .commentedAt(parseDateTime(firstNonBlank(readText(item, "gmtCreate"), readText(item, "gmtModified"))))
                .build();
    }

    private DefectPlatformAttachment toAttachment(JsonNode item) {
        return DefectPlatformAttachment.builder()
                .externalAttachmentId(readText(item, "id"))
                .fileId(readText(item, "fileId"))
                .fileName(readText(item, "fileName"))
                .suffix(readText(item, "suffix"))
                .size(readLong(item, "size"))
                .url(readText(item, "url"))
                .creatorName(readNestedName(item.get("creator")))
                .createdAt(parseDateTime(firstNonBlank(readText(item, "gmtCreate"), readText(item, "gmtModified"))))
                .build();
    }

    private YunxiaoProjectMember toProjectMember(JsonNode item) {
        return YunxiaoProjectMember.builder()
                .roleId(readText(item, "roleId"))
                .roleName(readText(item, "roleName"))
                .userAvatar(readText(item, "userAvatar"))
                .userId(readText(item, "userId"))
                .userName(readText(item, "userName"))
                .build();
    }

    private List<JsonNode> extractArrayLike(JsonNode root) {
        List<JsonNode> result = new ArrayList<>();
        if (root == null || root.isNull()) {
            return result;
        }
        if (root.isArray()) {
            root.forEach(result::add);
            return result;
        }
        JsonNode single = firstObject(root, "workitem", "workItem", "data", "result");
        if (single != null && single.has("id")) {
            result.add(single);
            return result;
        }
        String[] arrayFields = {"data", "items", "list", "result", "projects", "workitems", "workItems", "comments", "attachments"};
        for (String field : arrayFields) {
            JsonNode node = root.get(field);
            if (node != null && node.isArray()) {
                node.forEach(result::add);
                return result;
            }
            if (node != null && node.isObject()) {
                List<JsonNode> nested = extractArrayLike(node);
                if (!nested.isEmpty()) {
                    return nested;
                }
            }
        }
        Iterator<JsonNode> iterator = root.elements();
        while (iterator.hasNext()) {
            JsonNode node = iterator.next();
            if (node != null && node.isObject()) {
                result.add(node);
            }
        }
        return result;
    }

    private JsonNode unwrapWorkItem(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new BizException(404, "yunxiao work item not found");
        }
        JsonNode single = firstObject(root, "workitem", "workItem", "data", "result");
        return single == null ? root : single;
    }

    private JsonNode firstObject(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode node = root.get(field);
            if (node != null && node.isObject()) {
                return node;
            }
        }
        return null;
    }

    private YunxiaoConfig readConfig(DefectSyncAccount account) {
        String extraConfig = trimToNull(account.getExtraConfig());
        if (extraConfig == null) {
            return new YunxiaoConfig(null, "Bug", null, "Project", "gmtModified", 1, DEFAULT_PAGE_SIZE, "desc", false);
        }
        try {
            JsonNode root = objectMapper.readTree(extraConfig);
            return new YunxiaoConfig(
                    trimToNull(firstNonBlank(readText(root, "organizationId"), readText(root, "organization_id"))),
                    defaultText(readText(root, "category"), "Bug"),
                    trimToNull(readConfigText(root, "conditions")),
                    defaultText(readText(root, "spaceType"), "Project"),
                    defaultText(readText(root, "orderBy"), "gmtModified"),
                    Math.max(1, readInt(root, "page", 1)),
                    Math.min(MAX_PAGE_SIZE, Math.max(1, readInt(root, "perPage", DEFAULT_PAGE_SIZE))),
                    defaultText(readText(root, "sort"), "desc"),
                    root.path("regionMode").asBoolean(false));
        } catch (JsonProcessingException ex) {
            throw new BizException(400, "yunxiao extraConfig must be valid JSON: " + ex.getMessage());
        }
    }

    private String buildSummary(JsonNode workItem,
                                String description,
                                List<DefectPlatformComment> comments,
                                List<DefectPlatformAttachment> attachments) {
        StringBuilder builder = new StringBuilder();
        builder.append("工作项编号：").append(defaultText(readText(workItem, "serialNumber"), readText(workItem, "id"))).append("\n");
        builder.append("标题：").append(defaultText(readText(workItem, "subject"), readText(workItem, "title"), "未命名工作项")).append("\n");
        builder.append("状态：").append(defaultText(readNestedText(workItem, "status", "displayName"), readNestedText(workItem, "status", "name"))).append("\n");
        builder.append("负责人：").append(defaultText(readNestedName(workItem.get("assignedTo")))).append("\n");
        builder.append("创建人：").append(defaultText(readNestedName(workItem.get("creator")))).append("\n");
        if (description != null) {
            builder.append("\n描述：\n").append(description).append("\n");
        }
        if (!attachments.isEmpty()) {
            builder.append("\n附件：\n");
            for (DefectPlatformAttachment attachment : attachments) {
                builder.append("- ").append(defaultText(attachment.getFileName(), attachment.getFileId()));
                if (attachment.getUrl() != null) {
                    builder.append(" ").append(attachment.getUrl());
                }
                builder.append("\n");
            }
        }
        if (!comments.isEmpty()) {
            builder.append("\n最新评论：\n");
            comments.stream().limit(3).forEach(comment -> builder.append("- ")
                    .append(defaultText(comment.getAuthorName(), "未知用户"))
                    .append("：")
                    .append(defaultText(comment.getCommentContent()))
                    .append("\n"));
        }
        return builder.toString().trim();
    }

    private String appendDetailPayload(JsonNode workItem,
                                       List<DefectPlatformComment> comments,
                                       List<DefectPlatformAttachment> attachments) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("workItem", workItem);
        root.set("comments", objectMapper.valueToTree(comments));
        root.set("attachments", objectMapper.valueToTree(attachments));
        return root.toPrettyString();
    }

    private String readCustomField(JsonNode workItem, String... names) {
        JsonNode fields = workItem.get("customFieldValues");
        if (fields == null || !fields.isArray()) {
            return null;
        }
        for (JsonNode field : fields) {
            String fieldName = firstNonBlank(readText(field, "fieldIdentifier"), readText(field, "fieldName"), readText(field, "fieldId"));
            if (!matchesAny(fieldName, names)) {
                continue;
            }
            JsonNode values = field.get("values");
            if (values != null && values.isArray() && !values.isEmpty()) {
                JsonNode first = values.get(0);
                return firstNonBlank(readText(first, "displayValue"), readText(first, "identifier"), first.asText(null));
            }
        }
        return null;
    }

    private boolean matchesAny(String fieldName, String... names) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        for (String name : names) {
            if (lower.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String readLabels(JsonNode workItem) {
        JsonNode labels = workItem.get("labels");
        if (labels == null || !labels.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode label : labels) {
            String name = trimToNull(readText(label, "name"));
            if (name != null) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join(",", names);
    }

    private String readRichText(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        return normalizeRichText(node.get(field));
    }

    private String normalizeRichText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            return normalizeRichObject(node);
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            appendRichArray(parts, node);
            return joinRichText(parts);
        }
        String value = trimToNull(node.asText());
        if (value == null) {
            return null;
        }
        JsonNode parsed = parseJsonOrNull(value);
        if (parsed != null) {
            String normalized = normalizeRichText(parsed);
            if (normalized != null) {
                return normalized;
            }
        }
        return stripHtml(value);
    }

    private String normalizeRichObject(JsonNode node) {
        List<String> parts = new ArrayList<>();
        String htmlValue = trimToNull(readText(node, "htmlValue"));
        if (htmlValue != null) {
            String plainText = stripHtml(htmlValue);
            if (plainText != null) {
                parts.add(plainText);
            }
        }
        JsonNode jsonMl = node.get("jsonMLValue");
        if (jsonMl != null) {
            if (htmlValue == null) {
                appendRichArray(parts, jsonMl);
            } else {
                appendImageUrlsFromRich(parts, jsonMl);
            }
        }
        if (parts.isEmpty()) {
            String content = firstNonBlank(readText(node, "content"), readText(node, "value"), readText(node, "text"));
            if (content != null) {
                parts.add(stripHtml(content));
            }
        }
        return joinRichText(parts);
    }

    private JsonNode parseJsonOrNull(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || !(normalized.startsWith("{") || normalized.startsWith("["))) {
            return null;
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private void appendRichArray(List<String> parts, JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }
        int startIndex = 0;
        String tagName = null;
        if (!arrayNode.isEmpty() && arrayNode.get(0).isTextual()) {
            tagName = arrayNode.get(0).asText();
            startIndex = 1;
        }
        if ("img".equalsIgnoreCase(defaultText(tagName))) {
            appendImageUrl(parts, arrayNode.size() > 1 ? arrayNode.get(1) : null);
        }
        if (arrayNode.size() > startIndex && arrayNode.get(startIndex).isObject()) {
            appendImageUrl(parts, arrayNode.get(startIndex));
            startIndex++;
        }
        for (int index = startIndex; index < arrayNode.size(); index++) {
            appendRichNode(parts, arrayNode.get(index));
        }
    }

    private void appendRichNode(List<String> parts, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            appendRichArray(parts, node);
            return;
        }
        if (node.isObject()) {
            String htmlValue = trimToNull(readText(node, "htmlValue"));
            if (htmlValue != null) {
                String plainText = stripHtml(htmlValue);
                if (plainText != null) {
                    parts.add(plainText);
                }
            }
            appendImageUrl(parts, node);
            JsonNode jsonMl = node.get("jsonMLValue");
            if (jsonMl != null) {
                appendRichArray(parts, jsonMl);
            }
            return;
        }
        if (node.isTextual()) {
            String value = stripHtml(node.asText());
            if (value != null) {
                parts.add(value);
            }
        }
    }

    private void appendImageUrlsFromRich(List<String> parts, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            appendImageUrl(parts, node);
            return;
        }
        if (!node.isArray()) {
            return;
        }
        if (!node.isEmpty() && node.get(0).isTextual() && "img".equalsIgnoreCase(node.get(0).asText())) {
            appendImageUrl(parts, node.size() > 1 ? node.get(1) : null);
        }
        for (JsonNode child : node) {
            appendImageUrlsFromRich(parts, child);
        }
    }

    private void appendImageUrl(List<String> parts, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        String src = firstNonBlank(readText(node, "src"), readText(node, "url"));
        if (src != null) {
            parts.add("图片：" + src);
        }
    }

    private String joinRichText(List<String> parts) {
        List<String> normalizedParts = new ArrayList<>();
        for (String part : parts) {
            String normalized = trimToNull(part);
            if (normalized != null && !normalizedParts.contains(normalized)) {
                normalizedParts.add(normalized);
            }
        }
        return normalizedParts.isEmpty() ? null : String.join("\n", normalizedParts);
    }

    private boolean containsImage(JsonNode node) {
        String payload = node.toString().toLowerCase(Locale.ROOT);
        return payload.contains("<img") || payload.contains(".png") || payload.contains(".jpg") || payload.contains(".jpeg") || payload.contains(".gif");
    }

    private boolean isImageAttachment(DefectPlatformAttachment attachment) {
        String value = firstNonBlank(attachment.getSuffix(), attachment.getFileName(), attachment.getUrl());
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    private String buildYunxiaoConditions(String baseConditions, DefectRemoteQueryRequest query) {
        ArrayNode group = objectMapper.createArrayNode();
        appendBaseConditions(group, baseConditions);
        appendCondition(group, "string", "subject", "input", "CONTAINS", trimToNull(query == null ? null : query.getKeyword()));
        appendCondition(group, "status", "status", "list", "CONTAINS", trimToNull(query == null ? null : query.getStatus()));
        appendCondition(group, "user", "assignedTo", "list", "CONTAINS", trimToNull(query == null ? null : query.getAssignedTo()));
        appendCondition(group, "user", "creator", "list", "CONTAINS", trimToNull(query == null ? null : query.getReporterName()));
        appendCondition(group, "tag", "tag", "multiList", "CONTAINS", trimToNull(query == null ? null : query.getTag()));
        if (group.isEmpty()) {
            return null;
        }
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode groups = objectMapper.createArrayNode();
        groups.add(group);
        root.set("conditionGroups", groups);
        return root.toString();
    }

    private void appendBaseConditions(ArrayNode targetGroup, String baseConditions) {
        String normalized = trimToNull(baseConditions);
        if (normalized == null) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(normalized);
            JsonNode groups = root.get("conditionGroups");
            if (groups == null || !groups.isArray() || groups.isEmpty()) {
                return;
            }
            JsonNode firstGroup = groups.get(0);
            if (firstGroup == null || !firstGroup.isArray()) {
                return;
            }
            firstGroup.forEach(targetGroup::add);
        } catch (JsonProcessingException ignored) {
        }
    }

    private void addQueryParam(UriComponentsBuilder builder, String name, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            builder.queryParam(name, normalized);
        }
    }

    private void appendCondition(ArrayNode group,
                                 String className,
                                 String fieldIdentifier,
                                 String format,
                                 String operator,
                                 String csvValue) {
        List<String> values = splitCsv(csvValue);
        if (values.isEmpty()) {
            return;
        }
        ObjectNode condition = objectMapper.createObjectNode();
        condition.put("className", className);
        condition.put("fieldIdentifier", fieldIdentifier);
        condition.put("format", format);
        condition.put("operator", operator);
        condition.putNull("toValue");
        ArrayNode valueNode = objectMapper.createArrayNode();
        values.forEach(valueNode::add);
        condition.set("value", valueNode);
        group.add(condition);
    }

    private List<String> splitCsv(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : normalized.split(",")) {
            String trimmed = trimToNull(item);
            if (trimmed != null) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String resolveYunxiaoOrderBy(YunxiaoConfig config, DefectRemoteQueryRequest query) {
        String orderBy = trimToNull(query == null ? null : query.getOrderBy());
        if ("createdAt".equals(orderBy) || "gmtCreate".equals(orderBy)) {
            return "gmtCreate";
        }
        if ("updatedAt".equals(orderBy) || "gmtModified".equals(orderBy)) {
            return "gmtModified";
        }
        if ("title".equals(orderBy) || "name".equals(orderBy)) {
            return "name";
        }
        String fallback = trimToNull(config.orderBy());
        if ("updatedAt".equals(fallback)) {
            fallback = "gmtModified";
        }
        if ("createdAt".equals(fallback)) {
            fallback = "gmtCreate";
        }
        return defaultText(fallback, "gmtModified");
    }

    private String resolveSort(YunxiaoConfig config, DefectRemoteQueryRequest query) {
        String sort = trimToNull(query == null ? null : query.getSort());
        return defaultText(sort, trimToNull(config.sort()), "desc");
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String readNestedName(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return trimToNull(firstNonBlank(readText(node, "displayName"), readText(node, "name"), readText(node, "realName"), readText(node, "id")));
    }

    private String readNestedText(JsonNode node, String parent, String child) {
        if (node == null || node.isNull()) {
            return null;
        }
        return readText(node.get(parent), child);
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

    private String readConfigText(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private Long readLong(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private int readInt(JsonNode node, String field, int defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.matches("\\d{10,13}")) {
            long epoch = Long.parseLong(trimmed);
            if (trimmed.length() == 10) {
                return java.time.Instant.ofEpochSecond(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            }
            return java.time.Instant.ofEpochMilli(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }
        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed.replace(" ", "T"));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private Integer readHeaderInt(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long readHeaderLong(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void ensureSuccess(JsonNode root, String fallbackMessage) {
        if (root == null || root.isNull()) {
            throw new BizException(400, fallbackMessage);
        }
        JsonNode success = root.get("success");
        if (success != null && success.isBoolean() && !success.asBoolean()) {
            String message = firstNonBlank(readText(root, "message"), readText(root, "errorMsg"), readText(root, "errorMessage"));
            throw new BizException(400, firstNonBlank(message, fallbackMessage));
        }
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String defaultText(String... values) {
        String value = firstNonBlank(values);
        return value == null ? "-" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record YunxiaoConfig(String organizationId,
                                 String category,
                                 String conditions,
                                 String spaceType,
                                 String orderBy,
                                 int page,
                                 int perPage,
                                 String sort,
                                 boolean regionMode) {
    }
}
