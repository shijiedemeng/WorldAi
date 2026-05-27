package com.aiapi.markdown.service;

import com.aiapi.common.exception.BizException;
import com.aiapi.markdown.dto.MarkdownDocumentPageResponse;
import com.aiapi.markdown.dto.MarkdownDocumentRefsResponse;
import com.aiapi.markdown.dto.MarkdownDocumentResponse;
import com.aiapi.markdown.dto.MarkdownDocumentTreeNodeResponse;
import com.aiapi.markdown.dto.SaveMarkdownDocumentRequest;
import com.aiapi.markdown.entity.MarkdownDocument;
import com.aiapi.markdown.repository.MarkdownDocumentRepository;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MarkdownDocumentService {

    private static final Pattern DOCUMENT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$");
    private static final String ROOT_KEY = "__ROOT__";

    private final MarkdownDocumentRepository repository;

    @Transactional(readOnly = true)
    public MarkdownDocumentPageResponse page(String keyword,
                                             String type,
                                             String status,
                                             String nodeType,
                                             String parentId,
                                             Integer page,
                                             Integer pageSize) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        Page<MarkdownDocument> result = repository.findAll(
                buildSpecification(keyword, type, status, nodeType, parentId),
                PageRequest.of(normalizedPage - 1, normalizedPageSize, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return MarkdownDocumentPageResponse.builder()
                .items(result.getContent().stream().map(this::toResponse).toList())
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public List<MarkdownDocumentTreeNodeResponse> tree() {
        List<MarkdownDocument> documents = repository.findAll().stream()
                .sorted(documentComparator())
                .toList();
        return buildTree(documents, null, new HashSet<>());
    }

    @Transactional(readOnly = true)
    public MarkdownDocumentResponse get(String documentId) {
        return toResponse(findEntity(documentId));
    }

    @Transactional(readOnly = true)
    public List<MarkdownDocumentTreeNodeResponse> subtree(String documentId) {
        MarkdownDocument root = findEntity(documentId);
        List<MarkdownDocument> documents = repository.findAll().stream()
                .sorted(documentComparator())
                .toList();
        return List.of(toTreeNode(root, documents, new HashSet<>()));
    }

    @Transactional(readOnly = true)
    public List<MarkdownDocumentResponse> path(String documentId) {
        MarkdownDocument current = findEntity(documentId);
        List<MarkdownDocumentResponse> reversed = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (current != null) {
            if (!visited.add(current.getDocumentId())) {
                throw new BizException(400, "markdown document tree has cycle");
            }
            reversed.add(toResponse(current));
            String parentId = trimToNull(current.getParentId());
            current = parentId == null ? null : findEntity(parentId);
        }
        Collections.reverse(reversed);
        return reversed;
    }

    @Transactional(readOnly = true)
    public MarkdownDocumentRefsResponse refs(String documentId) {
        MarkdownDocument document = findEntity(documentId);
        List<String> refs = parseRefs(document.getRefs());
        Map<String, MarkdownDocument> outgoingMap = repository.findByDocumentIdIn(refs).stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getDocumentId(), item), Map::putAll);
        List<MarkdownDocumentResponse> outgoing = refs.stream()
                .map(outgoingMap::get)
                .filter(item -> item != null)
                .map(this::toResponse)
                .toList();
        List<String> missing = refs.stream()
                .filter(ref -> !outgoingMap.containsKey(ref))
                .toList();
        List<MarkdownDocumentResponse> incoming = repository.findAll().stream()
                .filter(item -> !item.getDocumentId().equals(documentId))
                .filter(item -> parseRefs(item.getRefs()).contains(documentId))
                .sorted(documentComparator())
                .map(this::toResponse)
                .toList();
        return MarkdownDocumentRefsResponse.builder()
                .documentId(document.getDocumentId())
                .refs(refs)
                .missingRefs(missing)
                .outgoingDocuments(outgoing)
                .incomingDocuments(incoming)
                .build();
    }

    @Transactional
    public MarkdownDocumentResponse create(SaveMarkdownDocumentRequest request) {
        FrontMatter frontMatter = resolveRequest(request);
        if (repository.existsByDocumentId(frontMatter.documentId())) {
            throw new BizException(400, "markdown document id already exists");
        }
        MarkdownDocument entity = new MarkdownDocument();
        applyFrontMatter(entity, frontMatter, null);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public MarkdownDocumentResponse update(String documentId, SaveMarkdownDocumentRequest request) {
        MarkdownDocument entity = findEntity(documentId);
        FrontMatter frontMatter = resolveRequest(request);
        if (!entity.getDocumentId().equals(frontMatter.documentId())) {
            throw new BizException(400, "markdown document id cannot be changed");
        }
        applyFrontMatter(entity, frontMatter, entity.getDocumentId());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public MarkdownDocumentResponse upload(MultipartFile file, Boolean overwrite) {
        String content = readUploadContent(file);
        FrontMatter frontMatter = parseFrontMatter(content);
        MarkdownDocument entity = repository.findByDocumentId(frontMatter.documentId()).orElse(null);
        if (entity != null && !Boolean.TRUE.equals(overwrite)) {
            throw new BizException(400, "markdown document id already exists");
        }
        if (entity == null) {
            entity = new MarkdownDocument();
        }
        applyFrontMatter(entity, frontMatter, entity.getDocumentId());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(String documentId) {
        MarkdownDocument entity = findEntity(documentId);
        if (repository.existsByParentId(entity.getDocumentId())) {
            throw new BizException(400, "markdown document has children, cannot delete");
        }
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public MarkdownDocument findEntity(String documentId) {
        String normalized = requiredId(documentId, "documentId");
        return repository.findByDocumentId(normalized)
                .orElseThrow(() -> new BizException(404, "markdown document not found"));
    }

    private void applyFrontMatter(MarkdownDocument entity, FrontMatter frontMatter, String currentDocumentId) {
        validateParent(frontMatter.documentId(), frontMatter.parentId(), currentDocumentId);
        validateRefs(frontMatter.documentId(), frontMatter.refs());
        entity.setDocumentId(frontMatter.documentId());
        entity.setTitle(frontMatter.title());
        entity.setNodeType(frontMatter.nodeType());
        entity.setDocumentType(frontMatter.type());
        entity.setDocumentStatus(frontMatter.status());
        entity.setParentId(frontMatter.parentId());
        entity.setRefs(String.join(",", frontMatter.refs()));
        entity.setContent(frontMatter.content());
    }

    private FrontMatter resolveRequest(SaveMarkdownDocumentRequest request) {
        if (request == null) {
            throw new BizException(400, "request is required");
        }
        String content = trimToNull(request.getContent());
        if (content != null) {
            return parseFrontMatter(content);
        }
        String nodeType = normalizeNodeType(request.getNodeType());
        String documentId = requiredId(request.getDocumentId(), "documentId");
        String title = requiredText(request.getTitle(), "title");
        String type = requiredText(request.getType(), "type");
        String status = requiredText(request.getStatus(), "status");
        String parentId = optionalId(request.getParentId());
        List<String> refs = request.getRefs() == null
                ? List.of()
                : request.getRefs().stream()
                .map(item -> requiredId(item, "refs"))
                .distinct()
                .toList();
        if ("FOLDER".equals(nodeType) && !refs.isEmpty()) {
            throw new BizException(400, "folder cannot have refs");
        }
        String body = "FOLDER".equals(nodeType) ? "" : normalizeLineBreaks(defaultText(request.getBody(), ""));
        return new FrontMatter(documentId, title, nodeType, type, status, parentId, refs, buildContent(documentId, title, nodeType, type, status, parentId, refs, body));
    }

    private void validateParent(String documentId, String parentId, String currentDocumentId) {
        if (parentId == null) {
            return;
        }
        if (documentId.equals(parentId)) {
            throw new BizException(400, "parent_id cannot equal id");
        }
        MarkdownDocument parent = repository.findByDocumentId(parentId)
                .orElseThrow(() -> new BizException(400, "parent_id does not exist"));
        Set<String> visited = new HashSet<>();
        while (parent != null) {
            if (!visited.add(parent.getDocumentId())) {
                throw new BizException(400, "markdown document tree has cycle");
            }
            if (parent.getDocumentId().equals(documentId) || parent.getDocumentId().equals(currentDocumentId)) {
                throw new BizException(400, "parent_id cannot point to descendant");
            }
            String nextParentId = trimToNull(parent.getParentId());
            parent = nextParentId == null ? null : repository.findByDocumentId(nextParentId).orElse(null);
        }
    }

    private void validateRefs(String documentId, List<String> refs) {
        if (refs.contains(documentId)) {
            throw new BizException(400, "refs cannot contain self id");
        }
    }

    private FrontMatter parseFrontMatter(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        if (lines.length < 3 || !"---".equals(lines[0].trim())) {
            throw new BizException(400, "markdown frontMatter must start with ---");
        }
        int endLine = -1;
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                endLine = i;
                break;
            }
        }
        if (endLine < 0) {
            throw new BizException(400, "markdown frontMatter must end with ---");
        }

        Map<String, String> values = new LinkedHashMap<>();
        List<String> refs = new ArrayList<>();
        String activeListKey = null;
        for (int i = 1; i < endLine; i++) {
            String raw = lines[i];
            String line = raw.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (activeListKey != null && line.startsWith("- ")) {
                if ("refs".equals(activeListKey)) {
                    addRef(refs, line.substring(2));
                }
                continue;
            }
            activeListKey = null;
            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                throw new BizException(400, "invalid frontMatter line: " + line);
            }
            String key = line.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colonIndex + 1).trim();
            if ("refs".equals(key)) {
                if (value.isBlank()) {
                    activeListKey = "refs";
                } else {
                    parseInlineRefs(value).forEach(item -> addRef(refs, item));
                }
                continue;
            }
            values.put(key, stripQuotes(value));
        }

        String documentId = requiredId(values.get("id"), "id");
        String nodeType = normalizeNodeType(values.get("node_type"));
        return new FrontMatter(
                documentId,
                requiredFrontMatterValue(values, "title"),
                nodeType,
                requiredFrontMatterValue(values, "type"),
                requiredFrontMatterValue(values, "status"),
                optionalId(values.get("parent_id")),
                refs.stream().distinct().toList(),
                "FOLDER".equals(nodeType) ? "" : content);
    }

    private List<String> parseInlineRefs(String value) {
        String normalized = stripQuotes(value);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split(",")).stream()
                .map(this::stripQuotes)
                .map(this::trimToNull)
                .filter(item -> item != null)
                .toList();
    }

    private void addRef(List<String> refs, String rawValue) {
        String ref = requiredId(stripQuotes(rawValue), "refs");
        if (!refs.contains(ref)) {
            refs.add(ref);
        }
    }

    private String requiredFrontMatterValue(Map<String, String> values, String key) {
        String value = trimToNull(values.get(key));
        if (value == null) {
            throw new BizException(400, "frontMatter " + key + " is required");
        }
        return value;
    }

    private String requiredText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BizException(400, fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeNodeType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "DOCUMENT";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!"DOCUMENT".equals(upper) && !"FOLDER".equals(upper)) {
            throw new BizException(400, "nodeType only supports DOCUMENT or FOLDER");
        }
        return upper;
    }

    private String optionalId(String value) {
        String normalized = trimToNull(stripQuotes(value));
        if (normalized == null || "null".equalsIgnoreCase(normalized) || "~".equals(normalized)) {
            return null;
        }
        return requiredId(normalized, "parent_id");
    }

    private String requiredId(String value, String fieldName) {
        String normalized = trimToNull(stripQuotes(value));
        if (normalized == null) {
            throw new BizException(400, fieldName + " is required");
        }
        if (!DOCUMENT_ID_PATTERN.matcher(normalized).matches()) {
            throw new BizException(400, fieldName + " only supports letters, numbers, dot, underscore, colon and dash");
        }
        return normalized;
    }

    private String requiredContent(SaveMarkdownDocumentRequest request) {
        if (request == null || trimToNull(request.getContent()) == null) {
            throw new BizException(400, "content is required");
        }
        return request.getContent();
    }

    private String buildContent(String documentId,
                                String title,
                                String nodeType,
                                String type,
                                String status,
                                String parentId,
                                List<String> refs,
                                String body) {
        List<String> lines = new ArrayList<>();
        lines.add("---");
        lines.add("id: " + documentId);
        lines.add("title: " + title.replaceAll("\\R", " ").trim());
        lines.add("node_type: " + nodeType);
        lines.add("type: " + type.replaceAll("\\R", " ").trim());
        lines.add("status: " + status.replaceAll("\\R", " ").trim());
        lines.add("parent_id: " + defaultText(parentId, ""));
        if (refs.isEmpty()) {
            lines.add("refs: []");
        } else {
            lines.add("refs:");
            refs.forEach(ref -> lines.add("  - " + ref));
        }
        lines.add("---");
        return lines.stream().collect(java.util.stream.Collectors.joining("\n")) + "\n\n" + normalizeLineBreaks(defaultText(body, ""));
    }

    private String normalizeLineBreaks(String value) {
        return defaultText(value, "").replace("\r\n", "\n").replace('\r', '\n');
    }

    private String readUploadContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "markdown file is required");
        }
        String filename = trimToNull(file.getOriginalFilename());
        if (filename != null && !filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new BizException(400, "only .md markdown files are supported");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BizException(500, "read markdown file failed");
        }
    }

    private List<MarkdownDocumentTreeNodeResponse> buildTree(List<MarkdownDocument> documents,
                                                             String parentId,
                                                             Set<String> visited) {
        String parentKey = parentId == null ? ROOT_KEY : parentId;
        return documents.stream()
                .filter(item -> parentKey.equals(item.getParentId() == null ? ROOT_KEY : item.getParentId()))
                .map(item -> toTreeNode(item, documents, visited))
                .toList();
    }

    private MarkdownDocumentTreeNodeResponse toTreeNode(MarkdownDocument entity,
                                                        List<MarkdownDocument> documents,
                                                        Set<String> visited) {
        if (!visited.add(entity.getDocumentId())) {
            return treeNode(entity, List.of());
        }
        List<MarkdownDocumentTreeNodeResponse> children = buildTree(documents, entity.getDocumentId(), visited);
        visited.remove(entity.getDocumentId());
        return treeNode(entity, children);
    }

    private MarkdownDocumentTreeNodeResponse treeNode(MarkdownDocument entity, List<MarkdownDocumentTreeNodeResponse> children) {
        return MarkdownDocumentTreeNodeResponse.builder()
                .documentId(entity.getDocumentId())
                .title(entity.getTitle())
                .nodeType(defaultText(entity.getNodeType(), "DOCUMENT"))
                .type(entity.getDocumentType())
                .status(entity.getDocumentStatus())
                .parentId(entity.getParentId())
                .refs(parseRefs(entity.getRefs()))
                .children(children)
                .build();
    }

    private MarkdownDocumentResponse toResponse(MarkdownDocument entity) {
        return MarkdownDocumentResponse.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .title(entity.getTitle())
                .nodeType(defaultText(entity.getNodeType(), "DOCUMENT"))
                .type(entity.getDocumentType())
                .status(entity.getDocumentStatus())
                .parentId(entity.getParentId())
                .refs(parseRefs(entity.getRefs()))
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<String> parseRefs(String refs) {
        String normalized = trimToNull(refs);
        if (normalized == null) {
            return List.of();
        }
        return List.of(normalized.split(",")).stream()
                .map(this::trimToNull)
                .filter(item -> item != null)
                .distinct()
                .toList();
    }

    private Specification<MarkdownDocument> buildSpecification(String keyword,
                                                               String type,
                                                               String status,
                                                               String nodeType,
                                                               String parentId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            String normalizedNodeType = trimToNull(nodeType);
            if (normalizedNodeType != null) {
                predicates.add(builder.equal(root.get("nodeType"), normalizeNodeType(normalizedNodeType)));
            }
            String normalizedParentId = trimToNull(parentId);
            if (normalizedParentId != null) {
                if (ROOT_KEY.equals(normalizedParentId)) {
                    predicates.add(builder.or(
                            builder.isNull(root.get("parentId")),
                            builder.equal(root.get("parentId"), "")
                    ));
                } else {
                    predicates.add(builder.equal(root.get("parentId"), normalizedParentId));
                }
            }
            String normalizedType = trimToNull(type);
            if (normalizedType != null) {
                predicates.add(builder.equal(root.get("documentType"), normalizedType));
            }
            String normalizedStatus = trimToNull(status);
            if (normalizedStatus != null) {
                predicates.add(builder.equal(root.get("documentStatus"), normalizedStatus));
            }
            String normalizedKeyword = trimToNull(keyword);
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("documentId")), pattern),
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("content")), pattern),
                        builder.like(builder.lower(root.get("refs")), pattern)
                ));
            }
            return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Comparator<MarkdownDocument> documentComparator() {
        return Comparator
                .comparing(MarkdownDocument::getTitle, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(MarkdownDocument::getDocumentId);
    }

    private String stripQuotes(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            return normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record FrontMatter(
            String documentId,
            String title,
            String nodeType,
            String type,
            String status,
            String parentId,
            List<String> refs,
            String content
    ) {
    }
}
