package com.aiapi.project.service;

import com.aiapi.common.enums.AgentRole;
import com.aiapi.common.enums.ProjectDocumentUsage;
import com.aiapi.common.enums.ProjectMarkdownBaseKey;
import com.aiapi.common.enums.ProjectMarkdownBaseSyncMode;
import com.aiapi.common.enums.ProjectMarkdownChangeSource;
import com.aiapi.common.enums.ProjectMarkdownFileType;
import com.aiapi.common.enums.ProjectMarkdownSyncMode;
import com.aiapi.common.exception.BizException;
import com.aiapi.markdown.entity.MarkdownDocument;
import com.aiapi.markdown.repository.MarkdownDocumentRepository;
import com.aiapi.project.dto.ProjectMarkdownBaseUploadRequest;
import com.aiapi.project.dto.ProjectMarkdownConfigRequest;
import com.aiapi.project.dto.ProjectMarkdownConfigResponse;
import com.aiapi.project.dto.ProjectMarkdownDocumentLinkRequest;
import com.aiapi.project.dto.ProjectMarkdownDocumentLinkResponse;
import com.aiapi.project.dto.ProjectMarkdownFileHistoryResponse;
import com.aiapi.project.dto.ProjectMarkdownFileRequest;
import com.aiapi.project.dto.ProjectMarkdownFileResponse;
import com.aiapi.project.entity.ProjectInfo;
import com.aiapi.project.entity.ProjectMarkdownDocumentLink;
import com.aiapi.project.entity.ProjectMarkdownFile;
import com.aiapi.project.entity.ProjectMarkdownFileHistory;
import com.aiapi.project.repository.ProjectInfoRepository;
import com.aiapi.project.repository.ProjectMarkdownDocumentLinkRepository;
import com.aiapi.project.repository.ProjectMarkdownFileHistoryRepository;
import com.aiapi.project.repository.ProjectMarkdownFileRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectMarkdownService {

    private static final String ROLE_MARKDOWN_DIR = "roles";

    private final ProjectInfoRepository projectInfoRepository;
    private final ProjectMarkdownFileRepository projectMarkdownFileRepository;
    private final ProjectMarkdownFileHistoryRepository projectMarkdownFileHistoryRepository;
    private final ProjectMarkdownDocumentLinkRepository projectMarkdownDocumentLinkRepository;
    private final MarkdownDocumentRepository markdownDocumentRepository;

    @Transactional(readOnly = true)
    public ProjectMarkdownConfigResponse getConfig(String projectCode) {
        ProjectInfo project = findProject(projectCode);
        List<ProjectMarkdownFileResponse> files = projectMarkdownFileRepository.findByProjectCode(projectCode).stream()
                .sorted(markdownComparator())
                .map(this::toResponse)
                .toList();
        List<ProjectMarkdownDocumentLinkResponse> documentLinks = buildDocumentLinkResponses(
                projectMarkdownDocumentLinkRepository.findByProjectCode(projectCode));
        return ProjectMarkdownConfigResponse.builder()
                .projectCode(projectCode)
                .markdownSyncMode(project.getMarkdownSyncMode())
                .baseMarkdownSyncMode(defaultBaseSyncMode(project.getBaseMarkdownSyncMode()))
                .baseMarkdownAllowClientUpload(Boolean.TRUE.equals(project.getBaseMarkdownAllowClientUpload()))
                .markdownFiles(files)
                .documentLinks(documentLinks)
                .build();
    }

    @Transactional
    public ProjectMarkdownConfigResponse updateConfig(String projectCode, ProjectMarkdownConfigRequest request) {
        ProjectInfo project = findProject(projectCode);
        replaceConfig(
                project,
                request.getMarkdownSyncMode(),
                request.getBaseMarkdownSyncMode(),
                request.getBaseMarkdownAllowClientUpload(),
                request.getMarkdownFiles(),
                request.getDocumentLinks()
        );
        return getConfig(projectCode);
    }

    @Transactional
    public void replaceConfig(ProjectInfo project,
                              ProjectMarkdownSyncMode markdownSyncMode,
                              List<ProjectMarkdownFileRequest> markdownFiles) {
        replaceConfig(
                project,
                markdownSyncMode,
                project.getBaseMarkdownSyncMode(),
                project.getBaseMarkdownAllowClientUpload(),
                markdownFiles
        );
    }

    @Transactional
    public void replaceConfig(ProjectInfo project,
                              ProjectMarkdownSyncMode markdownSyncMode,
                              ProjectMarkdownBaseSyncMode baseMarkdownSyncMode,
                              Boolean baseMarkdownAllowClientUpload,
                              List<ProjectMarkdownFileRequest> markdownFiles) {
        project.setMarkdownSyncMode(markdownSyncMode == null ? ProjectMarkdownSyncMode.CANCEL : markdownSyncMode);
        project.setBaseMarkdownSyncMode(defaultBaseSyncMode(baseMarkdownSyncMode));
        project.setBaseMarkdownAllowClientUpload(Boolean.TRUE.equals(baseMarkdownAllowClientUpload));
        projectInfoRepository.save(project);

        List<ProjectMarkdownFile> existingFiles = projectMarkdownFileRepository.findByProjectCode(project.getProjectCode());
        Map<String, ProjectMarkdownFile> existingMap = existingFiles.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(markdownIdentityKey(item), item), Map::putAll);
        projectMarkdownFileRepository.deleteByProjectCode(project.getProjectCode());
        List<ProjectMarkdownFile> entities = buildEntities(project.getProjectCode(), markdownFiles);
        applyVersionHistory(existingMap, entities);
        recordDeletedBaseHistories(existingMap, entities);
        if (!entities.isEmpty()) {
            projectMarkdownFileRepository.saveAll(entities);
        }
    }

    @Transactional
    public void replaceConfig(ProjectInfo project,
                              ProjectMarkdownSyncMode markdownSyncMode,
                              List<ProjectMarkdownFileRequest> markdownFiles,
                              List<ProjectMarkdownDocumentLinkRequest> documentLinks) {
        replaceConfig(project, markdownSyncMode, project.getBaseMarkdownSyncMode(), project.getBaseMarkdownAllowClientUpload(), markdownFiles);

        replaceDocumentLinks(project.getProjectCode(), documentLinks);
    }

    @Transactional
    public void replaceConfig(ProjectInfo project,
                              ProjectMarkdownSyncMode markdownSyncMode,
                              ProjectMarkdownBaseSyncMode baseMarkdownSyncMode,
                              Boolean baseMarkdownAllowClientUpload,
                              List<ProjectMarkdownFileRequest> markdownFiles,
                              List<ProjectMarkdownDocumentLinkRequest> documentLinks) {
        replaceConfig(project, markdownSyncMode, baseMarkdownSyncMode, baseMarkdownAllowClientUpload, markdownFiles);

        replaceDocumentLinks(project.getProjectCode(), documentLinks);
    }

    private void replaceDocumentLinks(String projectCode, List<ProjectMarkdownDocumentLinkRequest> documentLinks) {
        projectMarkdownDocumentLinkRepository.deleteByProjectCode(projectCode);
        projectMarkdownDocumentLinkRepository.flush();
        List<ProjectMarkdownDocumentLink> linkEntities = buildDocumentLinkEntities(projectCode, documentLinks);
        if (!linkEntities.isEmpty()) {
            projectMarkdownDocumentLinkRepository.saveAll(linkEntities);
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectMarkdownFileHistoryResponse> listBaseFileHistory(String projectCode, Long fileId) {
        ProjectMarkdownFile file = findMarkdownFile(projectCode, fileId);
        ensureBaseFile(file);
        return projectMarkdownFileHistoryRepository
                .findBaseHistory(projectCode, file.getAgentRole(), file.getBaseKey())
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional
    public ProjectMarkdownFileResponse rollbackBaseFile(String projectCode, Long fileId, Long historyId) {
        ProjectMarkdownFile file = findMarkdownFile(projectCode, fileId);
        ensureBaseFile(file);
        ProjectMarkdownFileHistory history = projectMarkdownFileHistoryRepository.findByProjectCodeAndId(projectCode, historyId)
                .orElseThrow(() -> new BizException(404, "markdown history not found"));
        if (!sameBaseScope(file, history)) {
            throw new BizException(400, "markdown history does not belong to this base file");
        }
        saveHistory(file, ProjectMarkdownChangeSource.ROLLBACK, "回滚前保留当前版本");
        int nextVersion = Math.max(versionNo(file), versionNo(history)) + 1;
        file.setContent(history.getContent());
        file.setFilePath(history.getFilePath());
        file.setVersionNo(nextVersion);
        file.setLastSyncSource(ProjectMarkdownChangeSource.ROLLBACK.name());
        return toResponse(projectMarkdownFileRepository.save(file));
    }

    @Transactional
    public ProjectMarkdownFileResponse uploadBaseFile(String projectCode, ProjectMarkdownBaseUploadRequest request) {
        ProjectInfo project = findProject(projectCode);
        if (defaultBaseSyncMode(project.getBaseMarkdownSyncMode()) != ProjectMarkdownBaseSyncMode.AUTO_UPDATE) {
            throw new BizException(400, "project base markdown sync mode is independent");
        }
        if (!Boolean.TRUE.equals(project.getBaseMarkdownAllowClientUpload())) {
            throw new BizException(400, "project does not allow client base markdown upload");
        }
        ProjectMarkdownBaseKey baseKey = request.getBaseKey();
        if (baseKey == null) {
            throw new BizException(400, "base markdown baseKey is required");
        }
        int clientVersion = request.getVersionNo() == null ? 1 : Math.max(1, request.getVersionNo());
        String content = request.getContent();
        if (content == null || content.trim().isBlank()) {
            throw new BizException(400, "base markdown content is required");
        }
        AgentRole agentRole = request.getAgentRole();
        String filePath = resolveBaseFilePath(baseKey, agentRole);
        ProjectMarkdownFile file = projectMarkdownFileRepository
                .findBaseFile(projectCode, agentRole, ProjectMarkdownFileType.BASE, baseKey)
                .orElse(null);
        if (file == null) {
            ProjectMarkdownFile created = buildEntity(projectCode, agentRole, ProjectMarkdownFileType.BASE, baseKey, filePath, content);
            created.setVersionNo(clientVersion);
            created.setLastSyncSource(ProjectMarkdownChangeSource.CLIENT.name());
            return toResponse(projectMarkdownFileRepository.save(created));
        }
        if (clientVersion <= versionNo(file)) {
            return toResponse(file);
        }
        saveHistory(file, ProjectMarkdownChangeSource.CLIENT, "客户端上传高版本前保留服务端版本");
        file.setFilePath(filePath);
        file.setContent(content);
        file.setVersionNo(clientVersion);
        file.setLastSyncSource(ProjectMarkdownChangeSource.CLIENT.name());
        return toResponse(projectMarkdownFileRepository.save(file));
    }

    @Transactional(readOnly = true)
    public String buildAiContext(String projectCode, ProjectDocumentUsage usageType) {
        return buildDocumentContext(projectCode, usageType);
    }

    @Transactional(readOnly = true)
    public String buildDocumentContext(String projectCode, ProjectDocumentUsage usageType) {
        String normalizedProjectCode = trim(projectCode);
        if (normalizedProjectCode.isBlank() || usageType == null) {
            return null;
        }
        List<ProjectMarkdownDocumentLink> links = projectMarkdownDocumentLinkRepository
                .findByProjectCodeAndUsageTypeOrderBySortNoAsc(normalizedProjectCode, normalizeDocumentUsage(usageType));
        if (links.isEmpty()) {
            return null;
        }
        return buildDocumentContextByIds(links.stream()
                .map(ProjectMarkdownDocumentLink::getDocumentId)
                .toList());
    }

    @Transactional(readOnly = true)
    public String buildDocumentContextByCsv(String documentIds) {
        List<String> ids = List.of(trim(documentIds).split(",")).stream()
                .map(this::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        return buildDocumentContextByIds(ids);
    }

    @Transactional(readOnly = true)
    public String buildDocumentContextByIds(Collection<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return null;
        }
        Map<String, MarkdownDocument> documents = markdownDocumentRepository.findAll().stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getDocumentId(), item), Map::putAll);
        Map<String, List<MarkdownDocument>> childrenByParent = buildChildrenByParent(documents.values());
        Map<String, MarkdownDocument> selectedDocuments = new LinkedHashMap<>();
        Set<String> visiting = new HashSet<>();
        for (String documentId : documentIds) {
            MarkdownDocument document = documents.get(trim(documentId));
            if (document == null) {
                continue;
            }
            collectDocumentAndChildren(document, childrenByParent, selectedDocuments, visiting);
        }

        List<String> sections = new ArrayList<>();
        for (MarkdownDocument document : selectedDocuments.values()) {
            if (!isDocumentNode(document)) {
                continue;
            }
            sections.add("""
                    ### %s（%s）
                    类型：%s
                    状态：%s
                    内容：
                    %s
                    """.formatted(
                    document.getTitle(),
                    document.getDocumentId(),
                    document.getDocumentType(),
                    document.getDocumentStatus(),
                    document.getContent()));
        }
        return sections.isEmpty() ? null : String.join("\n\n", sections);
    }

    private ProjectDocumentUsage normalizeDocumentUsage(ProjectDocumentUsage usageType) {
        if (usageType == ProjectDocumentUsage.GENERAL_DOCUMENT) {
            return ProjectDocumentUsage.AGENT_COMMON;
        }
        if (usageType == ProjectDocumentUsage.DEVELOPMENT_DOCUMENT) {
            return ProjectDocumentUsage.AGENT_DEVELOPER;
        }
        return usageType;
    }

    private Map<String, List<MarkdownDocument>> buildChildrenByParent(Collection<MarkdownDocument> documents) {
        Map<String, List<MarkdownDocument>> childrenByParent = new LinkedHashMap<>();
        for (MarkdownDocument document : documents) {
            String parentId = trim(document.getParentId());
            if (parentId.isBlank()) {
                continue;
            }
            childrenByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(document);
        }
        childrenByParent.values().forEach(children -> children.sort(markdownDocumentComparator()));
        return childrenByParent;
    }

    private void collectDocumentAndChildren(MarkdownDocument document,
                                            Map<String, List<MarkdownDocument>> childrenByParent,
                                            Map<String, MarkdownDocument> selectedDocuments,
                                            Set<String> visiting) {
        String documentId = document.getDocumentId();
        if (documentId == null || !visiting.add(documentId)) {
            return;
        }
        selectedDocuments.putIfAbsent(documentId, document);
        for (MarkdownDocument child : childrenByParent.getOrDefault(documentId, List.of())) {
            collectDocumentAndChildren(child, childrenByParent, selectedDocuments, visiting);
        }
        visiting.remove(documentId);
    }

    private boolean isDocumentNode(MarkdownDocument document) {
        return !"FOLDER".equalsIgnoreCase(trim(document.getNodeType()));
    }

    private ProjectInfo findProject(String projectCode) {
        return projectInfoRepository.findByProjectCode(projectCode)
                .orElseThrow(() -> new BizException(404, "project not found"));
    }

    private List<ProjectMarkdownFile> buildEntities(String projectCode, List<ProjectMarkdownFileRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<ProjectMarkdownFile> entities = new ArrayList<>();
        Map<String, Set<ProjectMarkdownBaseKey>> baseKeysByScope = new HashMap<>();
        Map<String, Set<String>> pathsByScope = new HashMap<>();
        for (ProjectMarkdownFileRequest request : requests) {
            if (request == null) {
                continue;
            }
            ProjectMarkdownFileType fileType = request.getFileType();
            if (fileType == null) {
                throw new BizException(400, "markdown fileType is required");
            }

            AgentRole agentRole = request.getAgentRole();
            String scopeKey = scopeKey(agentRole);
            Set<ProjectMarkdownBaseKey> scopeBaseKeys = baseKeysByScope.computeIfAbsent(scopeKey, ignored -> new HashSet<>());
            Set<String> scopePaths = pathsByScope.computeIfAbsent(scopeKey, ignored -> new HashSet<>());
            if (fileType == ProjectMarkdownFileType.BASE) {
                ProjectMarkdownBaseKey baseKey = request.getBaseKey();
                if (baseKey == null) {
                    throw new BizException(400, "base markdown baseKey is required");
                }
                String content = trim(request.getContent());
                if (content.isBlank()) {
                    continue;
                }
                if (!scopeBaseKeys.add(baseKey)) {
                    throw new BizException(400, "duplicate base markdown " + scopeKey + ":" + baseKey.name());
                }
                String resolvedPath = resolveBaseFilePath(baseKey, agentRole);
                if (!scopePaths.add(resolvedPath)) {
                    throw new BizException(400, "duplicate markdown path " + scopeKey + ":" + resolvedPath);
                }
                entities.add(buildEntity(projectCode, agentRole, ProjectMarkdownFileType.BASE, baseKey, resolvedPath, content));
                continue;
            }

            if (request.getBaseKey() != null) {
                throw new BizException(400, "extra markdown cannot set baseKey");
            }
            String rawPath = trim(request.getFilePath());
            String content = trim(request.getContent());
            if (rawPath.isBlank() && content.isBlank()) {
                continue;
            }
            if (rawPath.isBlank()) {
                throw new BizException(400, "extra markdown filePath is required");
            }
            if (content.isBlank()) {
                throw new BizException(400, "extra markdown content is required");
            }
            String normalizedPath = normalizeFilePath(rawPath);
            if (!scopePaths.add(normalizedPath)) {
                throw new BizException(400, "duplicate markdown path " + scopeKey + ":" + normalizedPath);
            }
            entities.add(buildEntity(projectCode, agentRole, ProjectMarkdownFileType.EXTRA, null, normalizedPath, content));
        }
        return entities;
    }

    private List<ProjectMarkdownDocumentLink> buildDocumentLinkEntities(String projectCode,
                                                                        List<ProjectMarkdownDocumentLinkRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        Map<ProjectDocumentUsage, Set<String>> documentIdsByUsage = new HashMap<>();
        List<ProjectMarkdownDocumentLink> entities = new ArrayList<>();
        int index = 1;
        for (ProjectMarkdownDocumentLinkRequest request : requests) {
            if (request == null) {
                continue;
            }
            ProjectDocumentUsage usageType = normalizeDocumentUsage(request.getUsageType());
            String documentId = trim(request.getDocumentId());
            if (usageType == null || documentId.isBlank()) {
                continue;
            }
            if (!markdownDocumentRepository.existsByDocumentId(documentId)) {
                throw new BizException(400, "markdown document does not exist: " + documentId);
            }
            Set<String> scopeDocumentIds = documentIdsByUsage.computeIfAbsent(usageType, ignored -> new HashSet<>());
            if (!scopeDocumentIds.add(documentId)) {
                continue;
            }
            ProjectMarkdownDocumentLink entity = new ProjectMarkdownDocumentLink();
            entity.setProjectCode(projectCode);
            entity.setUsageType(usageType);
            entity.setDocumentId(documentId);
            entity.setSortNo(index++);
            entities.add(entity);
        }
        return entities;
    }

    private ProjectMarkdownFile buildEntity(String projectCode,
                                            AgentRole agentRole,
                                            ProjectMarkdownFileType fileType,
                                            ProjectMarkdownBaseKey baseKey,
                                            String filePath,
                                            String content) {
        ProjectMarkdownFile entity = new ProjectMarkdownFile();
        entity.setProjectCode(projectCode);
        entity.setAgentRole(agentRole);
        entity.setFileType(fileType);
        entity.setBaseKey(baseKey);
        entity.setFilePath(filePath);
        entity.setContent(content);
        entity.setVersionNo(1);
        entity.setLastSyncSource(ProjectMarkdownChangeSource.SERVER.name());
        return entity;
    }

    private void applyVersionHistory(Map<String, ProjectMarkdownFile> existingMap, List<ProjectMarkdownFile> entities) {
        for (ProjectMarkdownFile entity : entities) {
            ProjectMarkdownFile previous = existingMap.remove(markdownIdentityKey(entity));
            if (previous == null) {
                continue;
            }
            if (entity.getFileType() != ProjectMarkdownFileType.BASE) {
                entity.setVersionNo(versionNo(previous));
                entity.setLastSyncSource(previous.getLastSyncSource());
                continue;
            }
            if (!Objects.equals(previous.getContent(), entity.getContent())
                    || !Objects.equals(previous.getFilePath(), entity.getFilePath())) {
                saveHistory(previous, ProjectMarkdownChangeSource.SERVER, "服务端保存基础 MD 前保留旧版本");
                entity.setVersionNo(versionNo(previous) + 1);
                entity.setLastSyncSource(ProjectMarkdownChangeSource.SERVER.name());
            } else {
                entity.setVersionNo(versionNo(previous));
                entity.setLastSyncSource(previous.getLastSyncSource());
            }
        }
    }

    private void recordDeletedBaseHistories(Map<String, ProjectMarkdownFile> remainingExistingMap, List<ProjectMarkdownFile> entities) {
        Set<String> entityKeys = entities.stream()
                .map(this::markdownIdentityKey)
                .collect(HashSet::new, Set::add, Set::addAll);
        remainingExistingMap.values().stream()
                .filter(item -> item.getFileType() == ProjectMarkdownFileType.BASE)
                .filter(item -> !entityKeys.contains(markdownIdentityKey(item)))
                .forEach(item -> saveHistory(item, ProjectMarkdownChangeSource.SERVER, "服务端删除基础 MD 前保留旧版本"));
    }

    private void saveHistory(ProjectMarkdownFile file, ProjectMarkdownChangeSource source, String desc) {
        if (file.getFileType() != ProjectMarkdownFileType.BASE || file.getBaseKey() == null) {
            return;
        }
        ProjectMarkdownFileHistory history = new ProjectMarkdownFileHistory();
        history.setProjectCode(file.getProjectCode());
        history.setAgentRole(file.getAgentRole());
        history.setBaseKey(file.getBaseKey());
        history.setFilePath(file.getFilePath());
        history.setContent(file.getContent());
        history.setVersionNo(versionNo(file));
        history.setChangeSource(source);
        history.setChangeDesc(desc);
        projectMarkdownFileHistoryRepository.save(history);
    }

    private String markdownIdentityKey(ProjectMarkdownFile file) {
        String scope = scopeKey(file.getAgentRole());
        if (file.getFileType() == ProjectMarkdownFileType.BASE) {
            return scope + ":BASE:" + file.getBaseKey();
        }
        return scope + ":EXTRA:" + file.getFilePath();
    }

    private ProjectMarkdownFile findMarkdownFile(String projectCode, Long fileId) {
        return projectMarkdownFileRepository.findByProjectCodeAndId(projectCode, fileId)
                .orElseThrow(() -> new BizException(404, "markdown file not found"));
    }

    private void ensureBaseFile(ProjectMarkdownFile file) {
        if (file.getFileType() != ProjectMarkdownFileType.BASE || file.getBaseKey() == null) {
            throw new BizException(400, "only base markdown supports history");
        }
    }

    private boolean sameBaseScope(ProjectMarkdownFile file, ProjectMarkdownFileHistory history) {
        return Objects.equals(file.getProjectCode(), history.getProjectCode())
                && Objects.equals(file.getAgentRole(), history.getAgentRole())
                && Objects.equals(file.getBaseKey(), history.getBaseKey());
    }

    private int versionNo(ProjectMarkdownFile file) {
        return file.getVersionNo() == null || file.getVersionNo() < 1 ? 1 : file.getVersionNo();
    }

    private int versionNo(ProjectMarkdownFileHistory history) {
        return history.getVersionNo() == null || history.getVersionNo() < 1 ? 1 : history.getVersionNo();
    }

    private ProjectMarkdownBaseSyncMode defaultBaseSyncMode(ProjectMarkdownBaseSyncMode value) {
        return value == null ? ProjectMarkdownBaseSyncMode.INDEPENDENT : value;
    }

    private String resolveBaseFilePath(ProjectMarkdownBaseKey baseKey, AgentRole agentRole) {
        if (agentRole == null) {
            return baseKey.getDefaultPath();
        }
        return ROLE_MARKDOWN_DIR + "/" + agentRole.name().toLowerCase(Locale.ROOT) + "/" + baseKey.getDefaultPath();
    }

    private Comparator<ProjectMarkdownFile> markdownComparator() {
        return Comparator
                .comparingInt((ProjectMarkdownFile item) -> roleSortOrder(item.getAgentRole()))
                .thenComparing(item -> scopeKey(item.getAgentRole()))
                .thenComparing(ProjectMarkdownFile::getFileType)
                .thenComparing(ProjectMarkdownFile::getFilePath);
    }

    private Comparator<MarkdownDocument> markdownDocumentComparator() {
        return Comparator
                .comparing(MarkdownDocument::getTitle, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(MarkdownDocument::getDocumentId, Comparator.nullsLast(String::compareToIgnoreCase));
    }

    private String normalizeFilePath(String filePath) {
        String normalized = trim(filePath).replace("\\", "/");
        if (normalized.isBlank()) {
            throw new BizException(400, "markdown filePath is required");
        }
        if (normalized.startsWith("/") || normalized.startsWith("./")) {
            throw new BizException(400, "markdown filePath must be relative");
        }
        if (normalized.equals("..") || normalized.contains("../")) {
            throw new BizException(400, "markdown filePath cannot escape workspace");
        }
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new BizException(400, "markdown filePath must end with .md");
        }
        return normalized;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String scopeKey(AgentRole agentRole) {
        return agentRole == null ? "COMMON" : agentRole.name();
    }

    private int roleSortOrder(AgentRole agentRole) {
        if (agentRole == null) {
            return 0;
        }
        return switch (agentRole) {
            case MAIN -> 1;
            case DEVELOPER -> 2;
            case TESTER -> 3;
            case REVIEWER -> 4;
            case OPS -> 5;
        };
    }

    private ProjectMarkdownFileResponse toResponse(ProjectMarkdownFile entity) {
        return ProjectMarkdownFileResponse.builder()
                .id(entity.getId())
                .agentRole(entity.getAgentRole())
                .fileType(entity.getFileType())
                .baseKey(entity.getBaseKey())
                .filePath(entity.getFilePath())
                .content(entity.getContent())
                .versionNo(versionNo(entity))
                .lastSyncSource(entity.getLastSyncSource())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ProjectMarkdownFileHistoryResponse toHistoryResponse(ProjectMarkdownFileHistory entity) {
        return ProjectMarkdownFileHistoryResponse.builder()
                .id(entity.getId())
                .projectCode(entity.getProjectCode())
                .agentRole(entity.getAgentRole())
                .baseKey(entity.getBaseKey())
                .filePath(entity.getFilePath())
                .content(entity.getContent())
                .versionNo(versionNo(entity))
                .changeSource(entity.getChangeSource())
                .changeDesc(entity.getChangeDesc())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<ProjectMarkdownDocumentLinkResponse> buildDocumentLinkResponses(List<ProjectMarkdownDocumentLink> links) {
        Map<String, MarkdownDocument> documents = markdownDocumentMap(links.stream()
                .map(ProjectMarkdownDocumentLink::getDocumentId)
                .toList());
        return links.stream()
                .sorted(Comparator
                        .comparing(ProjectMarkdownDocumentLink::getUsageType)
                        .thenComparing(ProjectMarkdownDocumentLink::getSortNo))
                .map(link -> toResponse(link, documents.get(link.getDocumentId())))
                .toList();
    }

    private Map<String, MarkdownDocument> markdownDocumentMap(Collection<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        return markdownDocumentRepository.findByDocumentIdIn(documentIds).stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getDocumentId(), item), Map::putAll);
    }

    private ProjectMarkdownDocumentLinkResponse toResponse(ProjectMarkdownDocumentLink entity, MarkdownDocument document) {
        return ProjectMarkdownDocumentLinkResponse.builder()
                .id(entity.getId())
                .usageType(entity.getUsageType())
                .documentId(entity.getDocumentId())
                .title(document == null ? entity.getDocumentId() : document.getTitle())
                .type(document == null ? null : document.getDocumentType())
                .status(document == null ? null : document.getDocumentStatus())
                .sortNo(entity.getSortNo())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
