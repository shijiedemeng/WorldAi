package com.aiapi.project.service;

import com.aiapi.common.enums.ProjectMarkdownSyncMode;
import com.aiapi.common.enums.ProjectMarkdownBaseSyncMode;
import com.aiapi.common.exception.BizException;
import com.aiapi.project.dto.CreateProjectRequest;
import com.aiapi.project.dto.ProjectResponse;
import com.aiapi.project.dto.UpdateProjectRequest;
import com.aiapi.project.entity.ProjectInfo;
import com.aiapi.project.repository.ProjectInfoRepository;
import com.aiapi.agent.repository.AgentInfoRepository;
import com.aiapi.project.repository.ProjectMarkdownDocumentLinkRepository;
import com.aiapi.project.repository.ProjectMarkdownFileRepository;
import com.aiapi.project.repository.ProjectMarkdownFileHistoryRepository;
import com.aiapi.knowledge.repository.ProjectKnowledgeItemRepository;
import com.aiapi.requirement.repository.RequirementInfoRepository;
import com.aiapi.requirement.repository.RequirementModuleInfoRepository;
import com.aiapi.session.repository.AgentSessionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectInfoRepository projectInfoRepository;
    private final ProjectMarkdownService projectMarkdownService;
    private final RequirementInfoRepository requirementInfoRepository;
    private final RequirementModuleInfoRepository requirementModuleInfoRepository;
    private final AgentInfoRepository agentInfoRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final ProjectMarkdownFileRepository projectMarkdownFileRepository;
    private final ProjectMarkdownFileHistoryRepository projectMarkdownFileHistoryRepository;
    private final ProjectMarkdownDocumentLinkRepository projectMarkdownDocumentLinkRepository;
    private final ProjectKnowledgeItemRepository projectKnowledgeItemRepository;

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        projectInfoRepository.findByProjectCode(request.getProjectCode()).ifPresent(project -> {
            throw new BizException(400, "projectCode already exists");
        });
        ProjectInfo entity = new ProjectInfo();
        entity.setProjectCode(request.getProjectCode());
        entity.setProjectName(request.getProjectName());
        entity.setProjectDesc(request.getProjectDesc());
        entity.setBusinessGoal(request.getBusinessGoal());
        entity.setTechStack(request.getTechStack());
        entity.setRepositoryUrl(request.getRepositoryUrl());
        entity.setOwnerAgentCode(request.getOwnerAgentCode());
        entity.setFileSearchMcpAgentCodes(trimToNull(request.getFileSearchMcpAgentCodes()));
        entity.setMarkdownSyncMode(request.getMarkdownSyncMode() == null ? ProjectMarkdownSyncMode.CANCEL : request.getMarkdownSyncMode());
        entity.setBaseMarkdownSyncMode(defaultBaseSyncMode(request.getBaseMarkdownSyncMode()));
        entity.setBaseMarkdownAllowClientUpload(Boolean.TRUE.equals(request.getBaseMarkdownAllowClientUpload()));
        entity.setStatus(request.getStatus());
        ProjectInfo saved = projectInfoRepository.save(entity);
        if (request.getMarkdownFiles() != null) {
            projectMarkdownService.replaceConfig(saved, saved.getMarkdownSyncMode(), request.getMarkdownFiles());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getByProjectCode(String projectCode) {
        return toResponse(findEntity(projectCode));
    }

    @Transactional
    public ProjectResponse update(String projectCode, UpdateProjectRequest request) {
        ProjectInfo entity = findEntity(projectCode);
        entity.setProjectName(request.getProjectName());
        entity.setProjectDesc(request.getProjectDesc());
        entity.setBusinessGoal(request.getBusinessGoal());
        entity.setTechStack(request.getTechStack());
        entity.setRepositoryUrl(request.getRepositoryUrl());
        entity.setOwnerAgentCode(request.getOwnerAgentCode());
        entity.setFileSearchMcpAgentCodes(trimToNull(request.getFileSearchMcpAgentCodes()));
        entity.setMarkdownSyncMode(request.getMarkdownSyncMode());
        entity.setBaseMarkdownSyncMode(defaultBaseSyncMode(request.getBaseMarkdownSyncMode()));
        entity.setBaseMarkdownAllowClientUpload(Boolean.TRUE.equals(request.getBaseMarkdownAllowClientUpload()));
        entity.setStatus(request.getStatus());
        return toResponse(projectInfoRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        return projectInfoRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(String projectCode) {
        ProjectInfo entity = findEntity(projectCode);
        if (requirementInfoRepository.countByProjectCode(projectCode) > 0
                || requirementModuleInfoRepository.countByProjectCode(projectCode) > 0) {
            throw new BizException(400, "project has requirements, cannot delete");
        }
        if (agentInfoRepository.countByProjectCode(projectCode) > 0) {
            throw new BizException(400, "project has agents, cannot delete");
        }
        if (agentSessionRepository.countByProjectCode(projectCode) > 0) {
            throw new BizException(400, "project has sessions, cannot delete");
        }
        if (projectKnowledgeItemRepository.countByProjectCode(projectCode) > 0) {
            throw new BizException(400, "project has knowledge reserve items, cannot delete");
        }
        projectMarkdownFileRepository.deleteByProjectCode(projectCode);
        projectMarkdownFileHistoryRepository.deleteByProjectCode(projectCode);
        projectMarkdownDocumentLinkRepository.deleteByProjectCode(projectCode);
        projectInfoRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public ProjectInfo findEntity(String projectCode) {
        return projectInfoRepository.findByProjectCode(projectCode)
                .orElseThrow(() -> new BizException(404, "project not found"));
    }

    private ProjectResponse toResponse(ProjectInfo entity) {
        return ProjectResponse.builder()
                .id(entity.getId())
                .projectCode(entity.getProjectCode())
                .projectName(entity.getProjectName())
                .projectDesc(entity.getProjectDesc())
                .businessGoal(entity.getBusinessGoal())
                .techStack(entity.getTechStack())
                .repositoryUrl(entity.getRepositoryUrl())
                .ownerAgentCode(entity.getOwnerAgentCode())
                .fileSearchMcpAgentCodes(entity.getFileSearchMcpAgentCodes())
                .markdownSyncMode(entity.getMarkdownSyncMode())
                .baseMarkdownSyncMode(defaultBaseSyncMode(entity.getBaseMarkdownSyncMode()))
                .baseMarkdownAllowClientUpload(Boolean.TRUE.equals(entity.getBaseMarkdownAllowClientUpload()))
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ProjectMarkdownBaseSyncMode defaultBaseSyncMode(ProjectMarkdownBaseSyncMode value) {
        return value == null ? ProjectMarkdownBaseSyncMode.INDEPENDENT : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
