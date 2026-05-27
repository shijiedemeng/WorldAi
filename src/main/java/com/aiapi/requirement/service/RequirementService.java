package com.aiapi.requirement.service;

import com.aiapi.common.enums.RequirementType;
import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.common.enums.RequirementExecutionMode;
import com.aiapi.common.enums.SessionStrategy;
import com.aiapi.common.exception.BizException;
import com.aiapi.common.enums.DefectPushStatus;
import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.websocket.ConsolePushWebSocketHandler;
import com.aiapi.defect.entity.DefectAnalysisRecord;
import com.aiapi.defect.repository.DefectAnalysisRecordRepository;
import com.aiapi.link.entity.RequirementDevLink;
import com.aiapi.link.repository.RequirementDevLinkRepository;
import com.aiapi.project.service.ProjectService;
import com.aiapi.requirement.dto.CreateRequirementRequest;
import com.aiapi.requirement.dto.RequirementResponse;
import com.aiapi.requirement.dto.RequirementTreeNodeResponse;
import com.aiapi.requirement.dto.RequirementWorkflowResultResponse;
import com.aiapi.requirement.dto.UpdateRequirementRequest;
import com.aiapi.requirement.dto.UpdateRequirementDescriptionRequest;
import com.aiapi.requirement.dto.UpdateRequirementStatusRequest;
import com.aiapi.requirement.dto.UpdateRequirementSessionPreferenceRequest;
import com.aiapi.requirement.entity.RequirementInfo;
import com.aiapi.requirement.entity.RequirementModuleInfo;
import com.aiapi.requirement.repository.RequirementInfoRepository;
import com.aiapi.requirement.repository.RequirementModuleInfoRepository;
import com.aiapi.requirement.repository.RequirementWorkflowEdgeRepository;
import com.aiapi.session.repository.RequirementSessionBindingRepository;
import com.aiapi.testrecord.repository.RequirementTestRecordRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequirementService {

    private final RequirementInfoRepository requirementInfoRepository;
    private final RequirementModuleInfoRepository requirementModuleInfoRepository;
    private final ProjectService projectService;
    private final RequirementDevLinkRepository requirementDevLinkRepository;
    private final RequirementTestRecordRepository requirementTestRecordRepository;
    private final RequirementSessionBindingRepository requirementSessionBindingRepository;
    private final DefectAnalysisRecordRepository defectAnalysisRecordRepository;
    private final RequirementWorkflowEdgeRepository workflowEdgeRepository;
    private final RequirementTaskExecutionMarkerService taskExecutionMarkerService;
    private final ConsolePushWebSocketHandler consolePushWebSocketHandler;

    @Transactional
    public RequirementResponse create(CreateRequirementRequest request) {
        RequirementType requirementType = request.getRequirementType() == null ? RequirementType.MASTER : request.getRequirementType();
        if (requirementType == RequirementType.SUB) {
            throw new BizException(400, "use /children endpoint to create sub requirement");
        }
        return createMaster(request);
    }

    @Transactional
    public RequirementResponse createMaster(CreateRequirementRequest request) {
        ensureManualStatusAllowed(request.getStatus());
        ensureRequirementNoUnused(request.getRequirementNo());
        projectService.findEntity(request.getProjectCode());
        RequirementInfo entity = new RequirementInfo();
        entity.setRequirementNo(request.getRequirementNo());
        entity.setProjectCode(request.getProjectCode());
        entity.setTitle(request.getTitle());
        entity.setRequirementDesc(request.getRequirementDesc());
        entity.setPriority(request.getPriority());
        entity.setStatus(request.getStatus());
        entity.setSource(request.getSource());
        entity.setRequirementType(RequirementType.MASTER);
        entity.setParentRequirementNo(null);
        entity.setRootRequirementNo(request.getRequirementNo());
        entity.setSortNo(normalizeSortNo(request.getSortNo()));
        entity.setMainAgentCode(request.getMainAgentCode());
        entity.setSessionStrategy(request.getSessionStrategy());
        entity.setPreferredSessionCode(trimToNull(request.getPreferredSessionCode()));
        entity.setCurrentStage(request.getCurrentStage());
        entity.setExpectedDeadline(request.getExpectedDeadline());
        entity.setCreatedBy(request.getCreatedBy());
        entity.setExecutionSteps(trimToNull(request.getExecutionSteps()));
        entity.setAutoExecuteFlag(Boolean.TRUE.equals(request.getAutoExecuteFlag()));
        entity.setFileSearchMcpAgentCodes(trimCsvToNull(request.getFileSearchMcpAgentCodes()));
        entity.setExecutionMode(defaultExecutionMode(request.getExecutionMode(), RequirementExecutionMode.NORMAL));
        entity.setResultExtractableFlag(defaultResultExtractable(request.getResultExtractableFlag()));
        entity.setReviewRequiredFlag(defaultReviewRequired(request.getReviewRequiredFlag()));
        entity.setReviewApprovedFlag(false);
        return toResponse(requirementInfoRepository.save(entity));
    }

    @Transactional
    public RequirementResponse createChild(String masterRequirementNo, CreateRequirementRequest request) {
        return createChild(masterRequirementNo, request, true, false);
    }

    @Transactional
    public RequirementResponse createChildWithoutRefresh(String masterRequirementNo, CreateRequirementRequest request) {
        return createChild(masterRequirementNo, request, false, true);
    }

    private RequirementResponse createChild(String masterRequirementNo,
                                            CreateRequirementRequest request,
                                            boolean refreshMaster,
                                            boolean allowAnalyzingMaster) {
        ensureManualStatusAllowed(request.getStatus());
        ensureRequirementNoUnused(request.getRequirementNo());
        RequirementInfo master = findEntity(masterRequirementNo);
        if (master.getRequirementType() != RequirementType.MASTER) {
            throw new BizException(400, "parent requirement must be MASTER");
        }
        if (!allowAnalyzingMaster) {
            ensureRequirementModuleMutable(master, true);
        }
        RequirementModuleInfo entity = new RequirementModuleInfo();
        entity.setRequirementNo(request.getRequirementNo());
        entity.setProjectCode(master.getProjectCode());
        entity.setTitle(request.getTitle());
        entity.setRequirementDesc(request.getRequirementDesc());
        entity.setPriority(request.getPriority());
        entity.setStatus(request.getStatus());
        entity.setSource(request.getSource());
        entity.setParentRequirementNo(master.getRequirementNo());
        entity.setRootRequirementNo(master.getRequirementNo());
        entity.setSortNo(normalizeSortNo(request.getSortNo()));
        entity.setMainAgentCode(request.getMainAgentCode());
        entity.setSessionStrategy(defaultSessionStrategy(request.getSessionStrategy(), master.getSessionStrategy()));
        entity.setPreferredSessionCode(trimToNull(request.getPreferredSessionCode()));
        entity.setCurrentStage(request.getCurrentStage());
        entity.setExpectedDeadline(request.getExpectedDeadline());
        entity.setCreatedBy(request.getCreatedBy());
        entity.setExecutionSteps(trimToNull(request.getExecutionSteps()));
        entity.setMcpFileSearchEnabledFlag(Boolean.TRUE.equals(request.getMcpFileSearchEnabledFlag()));
        entity.setDocumentIds(trimCsvToNull(request.getDocumentIds()));
        boolean projectKnowledgeSearchEnabled = Boolean.TRUE.equals(request.getProjectKnowledgeSearchEnabledFlag());
        entity.setProjectKnowledgeSearchEnabledFlag(projectKnowledgeSearchEnabled);
        entity.setProjectKnowledgeSearchLimit(projectKnowledgeSearchEnabled
                ? normalizeProjectKnowledgeSearchLimit(request.getProjectKnowledgeSearchLimit())
                : null);
        entity.setProjectKnowledgeSearchMinScore(projectKnowledgeSearchEnabled
                ? normalizeProjectKnowledgeSearchMinScore(request.getProjectKnowledgeSearchMinScore())
                : null);
        entity.setExecutionMode(defaultExecutionMode(request.getExecutionMode(), defaultExecutionMode(master.getExecutionMode(), RequirementExecutionMode.NORMAL)));
        entity.setResultExtractableFlag(defaultResultExtractable(request.getResultExtractableFlag()));
        entity.setReviewRequiredFlag(defaultReviewRequired(request.getReviewRequiredFlag()));
        entity.setReviewApprovedFlag(false);
        RequirementModuleInfo saved = requirementModuleInfoRepository.save(entity);
        RequirementResponse response = toResponse(saved);
        if (refreshMaster) {
            refreshMasterStatus(master.getRequirementNo());
        }
        broadcastRequirementChanged(saved.getRequirementNo(), saved.getRootRequirementNo(), "CREATED");
        return response;
    }

    @Transactional
    public RequirementResponse get(String requirementNo) {
        RequirementInfo master = requirementInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (master != null) {
            return toResponse(master);
        }
        RequirementModuleInfo module = findModuleEntity(requirementNo);
        syncSubRequirementStatusFromLinks(module);
        return toResponse(module);
    }

    @Transactional(readOnly = true)
    public List<RequirementResponse> list(String projectCode) {
        List<RequirementInfo> entities = projectCode == null || projectCode.isBlank()
                ? requirementInfoRepository.findAll()
                : requirementInfoRepository.findByProjectCode(projectCode);
        return entities.stream()
                .filter(item -> item.getRequirementType() == RequirementType.MASTER)
                .sorted(requirementComparator())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequirementResponse> listOpenTasks(String projectCode, String agentCode) {
        String normalizedProjectCode = trimToNull(projectCode);
        String normalizedAgentCode = trimToNull(agentCode);
        List<RequirementInfo> masters = normalizedProjectCode == null
                ? requirementInfoRepository.findAll()
                : requirementInfoRepository.findByProjectCode(normalizedProjectCode);
        List<RequirementResponse> result = new ArrayList<>();
        masters.stream()
                .filter(item -> item.getRequirementType() == RequirementType.MASTER)
                .filter(item -> item.getStatus() != RequirementStatus.DONE && item.getStatus() != RequirementStatus.CLOSED)
                .filter(item -> normalizedAgentCode == null || normalizedAgentCode.equals(item.getMainAgentCode()))
                .sorted(requirementComparator())
                .map(this::toResponse)
                .forEach(result::add);
        List<RequirementModuleInfo> modules = normalizedProjectCode == null
                ? requirementModuleInfoRepository.findAll()
                : requirementModuleInfoRepository.findByProjectCode(normalizedProjectCode);
        modules.stream()
                .filter(item -> item.getStatus() != RequirementStatus.DONE && item.getStatus() != RequirementStatus.CLOSED)
                .filter(item -> normalizedAgentCode == null || normalizedAgentCode.equals(item.getMainAgentCode()))
                .sorted(moduleComparator())
                .map(this::toResponse)
                .forEach(result::add);
        return result;
    }

    @Transactional(readOnly = true)
    public List<RequirementTreeNodeResponse> tree(String projectCode, String rootRequirementNo) {
        List<RequirementInfo> masters = rootRequirementNo != null && !rootRequirementNo.isBlank()
                ? requirementInfoRepository.findByRequirementNo(rootRequirementNo).stream().toList()
                : (projectCode == null || projectCode.isBlank()
                ? requirementInfoRepository.findAll()
                : requirementInfoRepository.findByProjectCode(projectCode));
        List<RequirementModuleInfo> modules = rootRequirementNo != null && !rootRequirementNo.isBlank()
                ? requirementModuleInfoRepository.findByRootRequirementNo(rootRequirementNo)
                : (projectCode == null || projectCode.isBlank()
                ? requirementModuleInfoRepository.findAll()
                : requirementModuleInfoRepository.findByProjectCode(projectCode));

        Map<String, List<RequirementModuleInfo>> childrenByParent = modules.stream()
                .collect(Collectors.groupingBy(RequirementModuleInfo::getParentRequirementNo));

        return masters.stream()
                .filter(item -> item.getRequirementType() == RequirementType.MASTER)
                .sorted(requirementComparator())
                .map(item -> buildTreeNode(item, childrenByParent))
                .toList();
    }

    @Transactional
    public List<RequirementResponse> listChildren(String masterRequirementNo) {
        RequirementInfo master = findEntity(masterRequirementNo);
        if (master.getRequirementType() != RequirementType.MASTER) {
            throw new BizException(400, "requirement is not MASTER");
        }
        List<RequirementModuleInfo> children = requirementModuleInfoRepository.findByParentRequirementNoOrderBySortNoAscCreatedAtAsc(masterRequirementNo);
        children.forEach(this::syncSubRequirementStatusFromLinks);
        return children.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<String> collectRequirementNosUnderRoot(String masterRequirementNo) {
        RequirementInfo master = findEntity(masterRequirementNo);
        if (master.getRequirementType() != RequirementType.MASTER) {
            return Set.of(masterRequirementNo);
        }
        LinkedHashSet<String> requirementNos = new LinkedHashSet<>();
        requirementNos.add(masterRequirementNo);
        requirementModuleInfoRepository.findByRootRequirementNo(masterRequirementNo).stream()
                .map(RequirementModuleInfo::getRequirementNo)
                .forEach(requirementNos::add);
        return requirementNos;
    }

    @Transactional
    public RequirementResponse updateStatus(String requirementNo, UpdateRequirementStatusRequest request) {
        if (request.getStatus() == RequirementStatus.ANALYZING) {
            throw new BizException(400, "ANALYZING status can only be set by AI analysis");
        }
        RequirementInfo master = requirementInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (master != null) {
            ensureRequirementModuleMutable(master, false);
            master.setStatus(request.getStatus());
            RequirementInfo saved = requirementInfoRepository.save(master);
            refreshMasterStatus(saved.getRootRequirementNo());
            broadcastRequirementChanged(saved.getRequirementNo(), saved.getRootRequirementNo(), "STATUS_CHANGED");
            return toResponse(saved);
        }
        RequirementModuleInfo module = findModuleEntity(requirementNo);
        ensureRequirementModuleMutable(module, false);
        module.setStatus(request.getStatus());
        RequirementModuleInfo saved = requirementModuleInfoRepository.save(module);
        refreshMasterStatus(saved.getRootRequirementNo());
        broadcastRequirementChanged(saved.getRequirementNo(), saved.getRootRequirementNo(), "STATUS_CHANGED");
        return toResponse(saved);
    }

    @Transactional
    public RequirementResponse markExecutionStarted(String requirementNo) {
        RequirementModuleInfo module = requirementModuleInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (module == null) {
            RequirementInfo master = findMasterEntity(requirementNo);
            ensureRequirementModuleMutable(master, false);
            if (master.getStatus() == RequirementStatus.DONE || master.getStatus() == RequirementStatus.CLOSED) {
                throw new BizException(400, "completed requirement cannot be started");
            }
            if (master.getStatus() != RequirementStatus.IN_PROGRESS) {
                master.setStatus(RequirementStatus.IN_PROGRESS);
                requirementInfoRepository.save(master);
            }
            refreshMasterStatus(master.getRootRequirementNo());
            broadcastRequirementChanged(master.getRequirementNo(), master.getRootRequirementNo(), "STARTED");
            return toResponse(master);
        }
        ensureRequirementModuleMutable(module, false);
        if (module.getStatus() == RequirementStatus.DONE || module.getStatus() == RequirementStatus.CLOSED) {
            throw new BizException(400, "completed requirement cannot be started");
        }
        if (module.getStatus() != RequirementStatus.IN_PROGRESS) {
            module.setStatus(RequirementStatus.IN_PROGRESS);
            requirementModuleInfoRepository.save(module);
        }
        refreshMasterStatus(module.getRootRequirementNo());
        broadcastRequirementChanged(module.getRequirementNo(), module.getRootRequirementNo(), "STARTED");
        return toResponse(module);
    }

    @Transactional
    public RequirementResponse finishExecutionWithoutLink(String requirementNo, boolean success) {
        RequirementModuleInfo module = requirementModuleInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        RequirementStatus nextStatus = success ? RequirementStatus.DONE : RequirementStatus.BLOCKED;
        if (module == null) {
            RequirementInfo master = findMasterEntity(requirementNo);
            ensureRequirementModuleMutable(master, false);
            if (master.getStatus() != nextStatus) {
                master.setStatus(nextStatus);
                requirementInfoRepository.save(master);
            }
            refreshMasterStatus(master.getRootRequirementNo());
            broadcastRequirementChanged(master.getRequirementNo(), master.getRootRequirementNo(), "FINISHED");
            return toResponse(master);
        }
        ensureRequirementModuleMutable(module, false);
        if (module.getStatus() != nextStatus) {
            module.setStatus(nextStatus);
            requirementModuleInfoRepository.save(module);
        }
        refreshMasterStatus(module.getRootRequirementNo());
        broadcastRequirementChanged(module.getRequirementNo(), module.getRootRequirementNo(), "FINISHED");
        return toResponse(module);
    }

    @Transactional
    public RequirementResponse resetExecution(String requirementNo) {
        RequirementModuleInfo entity = findModuleEntity(requirementNo);
        ensureRequirementModuleMutable(entity, false);
        if (entity.getStatus() != RequirementStatus.IN_PROGRESS) {
            throw new BizException(400, "only IN_PROGRESS sub requirement can reset execution");
        }
        List<RequirementDevLink> links = requirementDevLinkRepository.findByRequirementNo(requirementNo);
        for (RequirementDevLink link : links) {
            if (link.getStatus() == LinkStatus.DOING) {
                link.setStatus(LinkStatus.TODO);
                link.setStartedAt(null);
                link.setFinishedAt(null);
                link.setResultSummary(null);
                link.setExecutionDetails(null);
                link.setDeliverablePath(null);
            }
        }
        if (!links.isEmpty()) {
            requirementDevLinkRepository.saveAll(links);
        }
        entity.setStatus(RequirementStatus.PENDING);
        if (Boolean.TRUE.equals(entity.getReviewRequiredFlag())) {
            entity.setReviewApprovedFlag(false);
        }
        RequirementModuleInfo saved = requirementModuleInfoRepository.save(entity);
        refreshMasterStatus(saved.getRootRequirementNo());
        broadcastRequirementChanged(saved.getRequirementNo(), saved.getRootRequirementNo(), "RESET");
        return toResponse(saved);
    }

    @Transactional
    public RequirementResponse releaseExecutionLock(String requirementNo) {
        RequirementModuleInfo module = requirementModuleInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (module == null) {
            RequirementInfo master = requirementInfoRepository.findByRequirementNo(requirementNo).orElse(null);
            if (master == null || master.getStatus() != RequirementStatus.IN_PROGRESS) {
                return master == null ? null : toResponse(master);
            }
            master.setStatus(RequirementStatus.PENDING);
            RequirementInfo saved = requirementInfoRepository.save(master);
            broadcastRequirementChanged(saved.getRequirementNo(), saved.getRootRequirementNo(), "RELEASED");
            return toResponse(saved);
        }
        if (module.getStatus() == RequirementStatus.IN_PROGRESS) {
            module.setStatus(RequirementStatus.PENDING);
            requirementModuleInfoRepository.save(module);
        }
        refreshMasterStatus(module.getRootRequirementNo());
        broadcastRequirementChanged(module.getRequirementNo(), module.getRootRequirementNo(), "RELEASED");
        return toResponse(module);
    }

    @Transactional
    public RequirementResponse approveReview(String requirementNo) {
        RequirementModuleInfo entity = findModuleEntity(requirementNo);
        ensureRequirementModuleMutable(entity, false);
        if (!Boolean.TRUE.equals(entity.getReviewRequiredFlag())) {
            throw new BizException(400, "current requirement does not require manual review");
        }
        if (entity.getStatus() != RequirementStatus.PENDING && entity.getStatus() != RequirementStatus.BLOCKED) {
            throw new BizException(400, "only pending or blocked requirement can pass review");
        }
        entity.setReviewApprovedFlag(true);
        RequirementModuleInfo saved = requirementModuleInfoRepository.save(entity);
        refreshMasterStatus(saved.getRootRequirementNo());
        broadcastRequirementChanged(saved.getRequirementNo(), saved.getRootRequirementNo(), "REVIEW_APPROVED");
        return toResponse(saved);
    }

    @Transactional
    public RequirementResponse update(String requirementNo, UpdateRequirementRequest request) {
        RequirementInfo master = requirementInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (master != null) {
            ensureManualStatusAllowed(request.getStatus());
            ensureRequirementModuleMutable(master, false);
            master.setTitle(request.getTitle().trim());
            master.setRequirementDesc(request.getRequirementDesc());
            master.setPriority(request.getPriority());
            master.setStatus(request.getStatus());
            master.setSource(request.getSource());
            master.setSortNo(normalizeSortNo(request.getSortNo()));
            master.setMainAgentCode(trimToNull(request.getMainAgentCode()));
            master.setSessionStrategy(request.getSessionStrategy());
            master.setPreferredSessionCode(trimToNull(request.getPreferredSessionCode()));
            master.setCurrentStage(request.getCurrentStage());
            master.setExpectedDeadline(request.getExpectedDeadline());
            master.setCreatedBy(request.getCreatedBy());
            master.setExecutionSteps(trimToNull(request.getExecutionSteps()));
            master.setAutoExecuteFlag(Boolean.TRUE.equals(request.getAutoExecuteFlag()));
            master.setFileSearchMcpAgentCodes(trimCsvToNull(request.getFileSearchMcpAgentCodes()));
            master.setExecutionMode(defaultExecutionMode(request.getExecutionMode(), defaultExecutionMode(master.getExecutionMode(), RequirementExecutionMode.NORMAL)));
            master.setResultExtractableFlag(defaultResultExtractable(request.getResultExtractableFlag()));
            boolean reviewRequired = defaultReviewRequired(request.getReviewRequiredFlag());
            boolean reviewApproved = reviewRequired
                    && Boolean.TRUE.equals(master.getReviewRequiredFlag())
                    && Boolean.TRUE.equals(master.getReviewApprovedFlag());
            master.setReviewRequiredFlag(reviewRequired);
            master.setReviewApprovedFlag(reviewApproved);
            RequirementInfo saved = requirementInfoRepository.save(master);
            broadcastRequirementChanged(saved.getRequirementNo(), saved.getRootRequirementNo(), "UPDATED");
            return toResponse(saved);
        }
        RequirementModuleInfo entity = findModuleEntity(requirementNo);
        ensureManualStatusAllowed(request.getStatus());
        ensureRequirementModuleMutable(entity, false);
        ensureSubRequirementMutable(entity, false);
        entity.setTitle(request.getTitle().trim());
        entity.setRequirementDesc(request.getRequirementDesc());
        entity.setPriority(request.getPriority());
        entity.setStatus(request.getStatus());
        entity.setSource(request.getSource());
        entity.setSortNo(normalizeSortNo(request.getSortNo()));
        entity.setMainAgentCode(trimToNull(request.getMainAgentCode()));
        entity.setSessionStrategy(request.getSessionStrategy());
        entity.setPreferredSessionCode(trimToNull(request.getPreferredSessionCode()));
        entity.setCurrentStage(request.getCurrentStage());
        entity.setExpectedDeadline(request.getExpectedDeadline());
        entity.setCreatedBy(request.getCreatedBy());
        entity.setExecutionSteps(trimToNull(request.getExecutionSteps()));
        entity.setMcpFileSearchEnabledFlag(Boolean.TRUE.equals(request.getMcpFileSearchEnabledFlag()));
        entity.setDocumentIds(trimCsvToNull(request.getDocumentIds()));
        boolean projectKnowledgeSearchEnabled = Boolean.TRUE.equals(request.getProjectKnowledgeSearchEnabledFlag());
        entity.setProjectKnowledgeSearchEnabledFlag(projectKnowledgeSearchEnabled);
        entity.setProjectKnowledgeSearchLimit(projectKnowledgeSearchEnabled
                ? normalizeProjectKnowledgeSearchLimit(request.getProjectKnowledgeSearchLimit())
                : null);
        entity.setProjectKnowledgeSearchMinScore(projectKnowledgeSearchEnabled
                ? normalizeProjectKnowledgeSearchMinScore(request.getProjectKnowledgeSearchMinScore())
                : null);
        entity.setExecutionMode(defaultExecutionMode(request.getExecutionMode(), defaultExecutionMode(entity.getExecutionMode(), RequirementExecutionMode.NORMAL)));
        entity.setResultExtractableFlag(defaultResultExtractable(request.getResultExtractableFlag()));
        boolean reviewRequired = defaultReviewRequired(request.getReviewRequiredFlag());
        boolean reviewApproved = reviewRequired
                && Boolean.TRUE.equals(entity.getReviewRequiredFlag())
                && Boolean.TRUE.equals(entity.getReviewApprovedFlag());
        entity.setReviewRequiredFlag(reviewRequired);
        entity.setReviewApprovedFlag(reviewApproved);
        RequirementModuleInfo saved = requirementModuleInfoRepository.save(entity);
        refreshMasterStatus(saved.getRootRequirementNo());
        broadcastRequirementChanged(saved.getRequirementNo(), saved.getRootRequirementNo(), "UPDATED");
        return toResponse(saved);
    }

    @Transactional
    public RequirementResponse updateDescription(String requirementNo, UpdateRequirementDescriptionRequest request) {
        RequirementInfo master = requirementInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (master != null) {
            ensureRequirementModuleMutable(master, false);
            ensureMasterRequirementDescriptionMutable(master);
            master.setRequirementDesc(trimToNull(request == null ? null : request.getRequirementDesc()));
            return toResponse(requirementInfoRepository.save(master));
        }
        RequirementModuleInfo module = findModuleEntity(requirementNo);
        ensureRequirementModuleMutable(module, false);
        ensureSubRequirementMutable(module, false);
        module.setRequirementDesc(trimToNull(request == null ? null : request.getRequirementDesc()));
        return toResponse(requirementModuleInfoRepository.save(module));
    }

    @Transactional
    public RequirementResponse updateSessionPreference(String requirementNo, UpdateRequirementSessionPreferenceRequest request) {
        RequirementInfo master = requirementInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (master != null) {
            ensureRequirementModuleMutable(master, false);
            master.setSessionStrategy(request.getSessionStrategy());
            master.setPreferredSessionCode(trimToNull(request.getPreferredSessionCode()));
            return toResponse(requirementInfoRepository.save(master));
        }
        RequirementModuleInfo module = findModuleEntity(requirementNo);
        ensureRequirementModuleMutable(module, false);
        module.setSessionStrategy(request.getSessionStrategy());
        module.setPreferredSessionCode(trimToNull(request.getPreferredSessionCode()));
        return toResponse(requirementModuleInfoRepository.save(module));
    }

    @Transactional
    public void delete(String requirementNo) {
        RequirementInfo master = requirementInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (master != null) {
            ensureRequirementModuleMutable(master, true);
            deleteRequirementModule(master);
            return;
        }
        RequirementModuleInfo module = findModuleEntity(requirementNo);
        ensureRequirementModuleMutable(module, true);
        deleteSingleRequirement(module);
        refreshMasterStatus(module.getRootRequirementNo());
    }

    @Transactional(readOnly = true)
    public RequirementInfo findEntity(String requirementNo) {
        RequirementInfo master = requirementInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (master != null) {
            return master;
        }
        return toCompatibleInfo(findModuleEntity(requirementNo));
    }

    @Transactional(readOnly = true)
    public RequirementInfo findNullable(String requirementNo) {
        String normalized = trimToNull(requirementNo);
        if (normalized == null) {
            return null;
        }
        RequirementInfo master = requirementInfoRepository.findByRequirementNo(normalized).orElse(null);
        if (master != null) {
            return master;
        }
        return requirementModuleInfoRepository.findByRequirementNo(normalized)
                .map(this::toCompatibleInfo)
                .orElse(null);
    }

    @Transactional
    public void refreshMasterStatus(String rootRequirementNo) {
        refreshMasterStatus(rootRequirementNo, false);
    }

    @Transactional
    public void refreshMasterStatusAfterAnalysis(String rootRequirementNo) {
        refreshMasterStatus(rootRequirementNo, true);
    }

    @Transactional(readOnly = true)
    public RequirementWorkflowResultResponse workflowResults(String requirementNo) {
        RequirementInfo current = findEntity(requirementNo);
        String rootRequirementNo = current.getRequirementType() == RequirementType.MASTER
                ? current.getRequirementNo()
                : current.getRootRequirementNo();
        List<RequirementModuleInfo> children = requirementModuleInfoRepository.findByRootRequirementNo(rootRequirementNo)
                .stream()
                .filter(item -> Boolean.TRUE.equals(defaultResultExtractable(item.getResultExtractableFlag())))
                .sorted(moduleComparator())
                .toList();
        Set<String> childRequirementNos = children.stream()
                .map(RequirementModuleInfo::getRequirementNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, List<RequirementDevLink>> linksByRequirement = childRequirementNos.isEmpty()
                ? Map.of()
                : requirementDevLinkRepository.findByRequirementNoIn(childRequirementNos).stream()
                .collect(Collectors.groupingBy(RequirementDevLink::getRequirementNo));
        List<RequirementWorkflowResultResponse.Item> items = children.stream()
                .filter(item -> item.getStatus() == RequirementStatus.DONE || item.getStatus() == RequirementStatus.CLOSED)
                .map(item -> workflowResultItem(item, linksByRequirement.getOrDefault(item.getRequirementNo(), List.of())))
                .toList();
        return RequirementWorkflowResultResponse.builder()
                .rootRequirementNo(rootRequirementNo)
                .requirementNo(current.getRequirementNo())
                .items(items)
                .build();
    }

    private void refreshMasterStatus(String rootRequirementNo, boolean force) {
        if (rootRequirementNo == null || rootRequirementNo.isBlank()) {
            return;
        }
        RequirementInfo master = requirementInfoRepository.findByRequirementNo(rootRequirementNo).orElse(null);
        if (master == null || master.getRequirementType() != RequirementType.MASTER) {
            return;
        }
        if (!force && master.getStatus() == RequirementStatus.ANALYZING) {
            return;
        }

        List<RequirementModuleInfo> children = requirementModuleInfoRepository.findByRootRequirementNo(rootRequirementNo);
        if (children.isEmpty()) {
            return;
        }

        master.setStatus(deriveMasterStatusFromChildren(children));
        requirementInfoRepository.save(master);
        broadcastRequirementChanged(master.getRequirementNo(), master.getRootRequirementNo(), "STATUS_CHANGED");
    }

    @Transactional
    public void syncRequirementStatusFromLinks(String requirementNo) {
        RequirementModuleInfo requirement = requirementModuleInfoRepository.findByRequirementNo(requirementNo).orElse(null);
        if (requirement == null) {
            return;
        }
        List<RequirementDevLink> links = requirementDevLinkRepository.findByRequirementNo(requirementNo);
        if (links.isEmpty()) {
            return;
        }

        RequirementStatus nextStatus = deriveRequirementStatusFromLinks(links);
        if (requirement.getStatus() != nextStatus) {
            requirement.setStatus(nextStatus);
            requirementModuleInfoRepository.save(requirement);
            broadcastRequirementChanged(requirement.getRequirementNo(), requirement.getRootRequirementNo(), "STATUS_CHANGED");
        }
        refreshMasterStatus(requirement.getRootRequirementNo());
    }

    private RequirementTreeNodeResponse buildTreeNode(RequirementInfo entity, Map<String, List<RequirementModuleInfo>> childrenByParent) {
        List<RequirementModuleInfo> children = childrenByParent.getOrDefault(entity.getRequirementNo(), List.of());
        List<RequirementTreeNodeResponse> childNodes = new ArrayList<>(children.size());
        for (RequirementModuleInfo child : children.stream().sorted(moduleComparator()).toList()) {
            childNodes.add(buildTreeNode(child, childrenByParent));
        }
        return RequirementTreeNodeResponse.builder()
                .requirement(toResponse(entity))
                .children(childNodes)
                .build();
    }

    private RequirementTreeNodeResponse buildTreeNode(RequirementModuleInfo entity, Map<String, List<RequirementModuleInfo>> childrenByParent) {
        List<RequirementModuleInfo> children = childrenByParent.getOrDefault(entity.getRequirementNo(), List.of());
        List<RequirementTreeNodeResponse> childNodes = new ArrayList<>(children.size());
        for (RequirementModuleInfo child : children.stream().sorted(moduleComparator()).toList()) {
            childNodes.add(buildTreeNode(child, childrenByParent));
        }
        return RequirementTreeNodeResponse.builder()
                .requirement(toResponse(entity))
                .children(childNodes)
                .build();
    }

    private Comparator<RequirementInfo> requirementComparator() {
        return Comparator
                .comparing((RequirementInfo item) -> item.getSortNo() == null ? 0 : item.getSortNo())
                .thenComparing(RequirementInfo::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<RequirementModuleInfo> moduleComparator() {
        return Comparator
                .comparing((RequirementModuleInfo item) -> item.getSortNo() == null ? 0 : item.getSortNo())
                .thenComparing(RequirementModuleInfo::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private RequirementStatus deriveMasterStatusFromChildren(List<RequirementModuleInfo> children) {
        if (children.stream().anyMatch(item -> item.getStatus() == RequirementStatus.BLOCKED)) {
            return RequirementStatus.BLOCKED;
        }
        if (children.stream().anyMatch(item -> item.getStatus() == RequirementStatus.IN_PROGRESS)) {
            return RequirementStatus.IN_PROGRESS;
        }
        if (children.stream().anyMatch(item -> item.getStatus() == RequirementStatus.TESTING)) {
            return RequirementStatus.TESTING;
        }
        if (children.stream().anyMatch(item -> item.getStatus() == RequirementStatus.PENDING)) {
            return RequirementStatus.PENDING;
        }
        if (children.stream().allMatch(item -> item.getStatus() == RequirementStatus.DONE
                || item.getStatus() == RequirementStatus.CLOSED)) {
            return RequirementStatus.DONE;
        }
        return RequirementStatus.PENDING;
    }

    private RequirementStatus deriveRequirementStatusFromLinks(List<RequirementDevLink> links) {
        if (links.stream().allMatch(link -> link.getStatus() == LinkStatus.TODO)) {
            return RequirementStatus.PENDING;
        }
        if (links.stream().anyMatch(link -> link.getStatus() == LinkStatus.BLOCKED)) {
            return RequirementStatus.BLOCKED;
        }
        if (links.stream().allMatch(link -> link.getStatus() == LinkStatus.DONE || link.getStatus() == LinkStatus.SKIPPED)) {
            return RequirementStatus.DONE;
        }
        if (links.stream().anyMatch(link -> link.getStatus() == LinkStatus.DOING)) {
            return RequirementStatus.IN_PROGRESS;
        }
        if (links.stream().anyMatch(link -> link.getStatus() == LinkStatus.DONE || link.getStatus() == LinkStatus.SKIPPED)) {
            return RequirementStatus.IN_PROGRESS;
        }
        return RequirementStatus.PENDING;
    }

    private void syncSubRequirementStatusFromLinks(RequirementModuleInfo requirement) {
        List<RequirementDevLink> links = requirementDevLinkRepository.findByRequirementNo(requirement.getRequirementNo());
        if (links.isEmpty()) {
            return;
        }
        RequirementStatus nextStatus = deriveRequirementStatusFromLinks(links);
        if (requirement.getStatus() == nextStatus) {
            return;
        }
        requirement.setStatus(nextStatus);
        requirementModuleInfoRepository.save(requirement);
        broadcastRequirementChanged(requirement.getRequirementNo(), requirement.getRootRequirementNo(), "STATUS_CHANGED");
        refreshMasterStatus(requirement.getRootRequirementNo());
    }

    private void ensureSubRequirementMutable(RequirementModuleInfo entity, boolean deleting) {
        if (entity.getStatus() != com.aiapi.common.enums.RequirementStatus.PENDING) {
            throw new BizException(400, "only PENDING sub requirement can be edited or deleted");
        }
        if (requirementModuleInfoRepository.countByParentRequirementNo(entity.getRequirementNo()) > 0) {
            throw new BizException(400, "sub requirement has child requirements, cannot edit or delete");
        }
        List<RequirementDevLink> links = requirementDevLinkRepository.findByRequirementNo(entity.getRequirementNo());
        if (hasStartedLinks(links)) {
            throw new BizException(400, "sub requirement has started links, cannot edit or delete");
        }
        if (requirementTestRecordRepository.countByRequirementNo(entity.getRequirementNo()) > 0) {
            throw new BizException(400, "sub requirement has test records, cannot edit or delete");
        }
        if (!deleting && requirementSessionBindingRepository.countByRequirementNo(entity.getRequirementNo()) > 0) {
            return;
        }
    }

    private void ensureRequirementModuleMutable(RequirementInfo entity, boolean deleting) {
        RequirementInfo root = entity.getRequirementType() == RequirementType.MASTER ? entity : findMasterEntity(entity.getRootRequirementNo());
        ensureRootNotAnalyzing(root, deleting);
    }

    private void ensureRequirementModuleMutable(RequirementModuleInfo entity, boolean deleting) {
        ensureRootNotAnalyzing(findMasterEntity(entity.getRootRequirementNo()), deleting);
    }

    private void ensureRootNotAnalyzing(RequirementInfo root, boolean deleting) {
        if (root.getStatus() == RequirementStatus.ANALYZING) {
            throw new BizException(400, deleting
                    ? "requirement module is analyzing, cannot delete"
                    : "requirement module is analyzing, cannot edit");
        }
    }

    private void ensureMasterRequirementDescriptionMutable(RequirementInfo entity) {
        if (entity.getStatus() != RequirementStatus.PENDING) {
            throw new BizException(400, "only PENDING master requirement can edit description");
        }
        if (requirementModuleInfoRepository.countByParentRequirementNo(entity.getRequirementNo()) > 0) {
            throw new BizException(400, "master requirement already has child requirements, cannot edit description");
        }
        List<RequirementDevLink> links = requirementDevLinkRepository.findByRequirementNo(entity.getRequirementNo());
        if (hasStartedLinks(links)) {
            throw new BizException(400, "master requirement has started links, cannot edit description");
        }
        if (requirementTestRecordRepository.countByRequirementNo(entity.getRequirementNo()) > 0) {
            throw new BizException(400, "master requirement has test records, cannot edit description");
        }
    }

    private void ensureManualStatusAllowed(RequirementStatus status) {
        if (status == RequirementStatus.ANALYZING) {
            throw new BizException(400, "ANALYZING status can only be set by AI analysis");
        }
    }

    private boolean hasStartedLinks(List<RequirementDevLink> links) {
        for (RequirementDevLink link : links) {
            if (link.getStatus() != com.aiapi.common.enums.LinkStatus.TODO) {
                return true;
            }
            if (trimToNull(link.getResultSummary()) != null || trimToNull(link.getExecutionDetails()) != null) {
                return true;
            }
            if (link.getStartedAt() != null || link.getFinishedAt() != null) {
                return true;
            }
        }
        return false;
    }

    private void deleteRequirementModule(RequirementInfo master) {
        String rootRequirementNo = master.getRootRequirementNo() == null || master.getRootRequirementNo().isBlank()
                ? master.getRequirementNo()
                : master.getRootRequirementNo();
        List<RequirementModuleInfo> moduleRequirements = requirementModuleInfoRepository.findByRootRequirementNo(rootRequirementNo);
        Set<String> requirementNos = moduleRequirements.stream()
                .map(RequirementModuleInfo::getRequirementNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        requirementNos.add(master.getRequirementNo());
        deleteRequirementContent(requirementNos);
        requirementModuleInfoRepository.deleteAll(moduleRequirements);
        requirementInfoRepository.delete(master);
    }

    private void deleteSingleRequirement(RequirementModuleInfo entity) {
        ensureSubRequirementMutable(entity, true);
        deleteRequirementContent(Set.of(entity.getRequirementNo()));
        requirementModuleInfoRepository.delete(entity);
    }

    private void deleteRequirementContent(Set<String> requirementNos) {
        releaseDefectAnalysisLinks(requirementNos);
        deleteWorkflowEdges(requirementNos);
        for (String item : requirementNos) {
            requirementDevLinkRepository.deleteByRequirementNo(item);
            requirementSessionBindingRepository.deleteByRequirementNo(item);
            requirementTestRecordRepository.deleteByRequirementNo(item);
        }
    }

    private void deleteWorkflowEdges(Set<String> requirementNos) {
        if (requirementNos == null || requirementNos.isEmpty()) {
            return;
        }
        for (String requirementNo : requirementNos) {
            RequirementInfo requirement = requirementInfoRepository.findByRequirementNo(requirementNo).orElse(null);
            if (requirement != null) {
                workflowEdgeRepository.deleteByRootRequirementNo(requirement.getRequirementNo());
                continue;
            }
            RequirementModuleInfo module = requirementModuleInfoRepository.findByRequirementNo(requirementNo).orElse(null);
            if (module != null) {
                workflowEdgeRepository.deleteByRootRequirementNoAndRequirementNoIn(module.getRootRequirementNo(), Set.of(module.getRequirementNo()));
            }
        }
    }

    private void releaseDefectAnalysisLinks(Set<String> requirementNos) {
        List<DefectAnalysisRecord> linkedAnalyses = defectAnalysisRecordRepository.findByNextRequirementNoIn(requirementNos);
        if (linkedAnalyses.isEmpty()) {
            return;
        }
        for (DefectAnalysisRecord analysis : linkedAnalyses) {
            analysis.setNextRequirementNo(null);
            analysis.setPushedAt(null);
            if (analysis.getPushStatus() == DefectPushStatus.PUSHED) {
                analysis.setPushStatus(trimToNull(analysis.getAnalysisResult()) == null
                        ? DefectPushStatus.DRAFT
                        : DefectPushStatus.ANALYZED);
            }
        }
        defectAnalysisRecordRepository.saveAll(linkedAnalyses);
    }

    private int normalizeSortNo(Integer sortNo) {
        return sortNo == null ? 0 : sortNo;
    }

    private SessionStrategy defaultSessionStrategy(SessionStrategy requestStrategy, SessionStrategy fallback) {
        return requestStrategy != null ? requestStrategy : fallback;
    }

    private RequirementExecutionMode defaultExecutionMode(RequirementExecutionMode requestMode, RequirementExecutionMode fallback) {
        return requestMode != null ? requestMode : fallback;
    }

    private Boolean defaultResultExtractable(Boolean value) {
        return value == null || value;
    }

    private Boolean defaultReviewRequired(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private Integer normalizeProjectKnowledgeSearchLimit(Integer value) {
        if (value == null) {
            return null;
        }
        return Math.min(Math.max(value, 1), 20);
    }

    private Double normalizeProjectKnowledgeSearchMinScore(Double value) {
        if (value == null) {
            return null;
        }
        return Math.min(Math.max(value, 0D), 100D);
    }

    private RequirementWorkflowResultResponse.Item workflowResultItem(RequirementModuleInfo requirement, List<RequirementDevLink> links) {
        RequirementDevLink latestLink = links.stream()
                .filter(link -> Boolean.TRUE.equals(hasLinkResult(link)))
                .max(Comparator.comparing(
                        RequirementDevLink::getFinishedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ).thenComparing(RequirementDevLink::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        return RequirementWorkflowResultResponse.Item.builder()
                .requirementNo(requirement.getRequirementNo())
                .title(requirement.getTitle())
                .agentCode(requirement.getMainAgentCode())
                .requirementStatus(requirement.getStatus())
                .linkId(latestLink == null ? null : latestLink.getId())
                .linkStatus(latestLink == null ? null : latestLink.getStatus())
                .resultSummary(latestLink == null ? null : latestLink.getResultSummary())
                .executionDetails(latestLink == null ? null : latestLink.getExecutionDetails())
                .deliverablePath(latestLink == null ? null : latestLink.getDeliverablePath())
                .finishedAt(latestLink == null ? null : latestLink.getFinishedAt())
                .build();
    }

    private Boolean hasLinkResult(RequirementDevLink link) {
        return link.getStatus() == LinkStatus.DONE
                || link.getStatus() == LinkStatus.SKIPPED
                || trimToNull(link.getResultSummary()) != null
                || trimToNull(link.getExecutionDetails()) != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimCsvToNull(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String result = List.of(normalized.split(",")).stream()
                .map(this::trimToNull)
                .filter(item -> item != null)
                .distinct()
                .collect(Collectors.joining(","));
        return result.isBlank() ? null : result;
    }

    private void broadcastRequirementChanged(String requirementNo, String rootRequirementNo, String action) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        data.put("requirementNo", requirementNo);
        data.put("rootRequirementNo", rootRequirementNo);
        consolePushWebSocketHandler.broadcast("REQUIREMENT_TASK_CHANGED", data);
    }

    private void ensureRequirementNoUnused(String requirementNo) {
        String normalized = trimToNull(requirementNo);
        if (normalized == null) {
            throw new BizException(400, "requirementNo is required");
        }
        if (requirementInfoRepository.findByRequirementNo(normalized).isPresent()
                || requirementModuleInfoRepository.findByRequirementNo(normalized).isPresent()) {
            throw new BizException(400, "requirementNo already exists");
        }
    }

    private RequirementInfo findMasterEntity(String requirementNo) {
        return requirementInfoRepository.findByRequirementNo(requirementNo)
                .filter(item -> item.getRequirementType() == RequirementType.MASTER)
                .orElseThrow(() -> new BizException(404, "requirement not found"));
    }

    private RequirementModuleInfo findModuleEntity(String requirementNo) {
        return requirementModuleInfoRepository.findByRequirementNo(requirementNo)
                .orElseThrow(() -> new BizException(404, "requirement not found"));
    }

    private RequirementInfo toCompatibleInfo(RequirementModuleInfo module) {
        RequirementInfo entity = new RequirementInfo();
        entity.setId(module.getId());
        entity.setRequirementNo(module.getRequirementNo());
        entity.setProjectCode(module.getProjectCode());
        entity.setTitle(module.getTitle());
        entity.setRequirementDesc(module.getRequirementDesc());
        entity.setPriority(module.getPriority());
        entity.setStatus(module.getStatus());
        entity.setSource(module.getSource());
        entity.setRequirementType(RequirementType.SUB);
        entity.setParentRequirementNo(module.getParentRequirementNo());
        entity.setRootRequirementNo(module.getRootRequirementNo());
        entity.setSortNo(module.getSortNo());
        entity.setMainAgentCode(module.getMainAgentCode());
        entity.setSessionStrategy(module.getSessionStrategy());
        entity.setPreferredSessionCode(module.getPreferredSessionCode());
        entity.setCurrentStage(module.getCurrentStage());
        entity.setExpectedDeadline(module.getExpectedDeadline());
        entity.setCreatedBy(module.getCreatedBy());
        entity.setExecutionSteps(module.getExecutionSteps());
        entity.setAutoExecuteFlag(false);
        entity.setFileSearchMcpAgentCodes(null);
        entity.setExecutionMode(module.getExecutionMode());
        entity.setResultExtractableFlag(module.getResultExtractableFlag());
        entity.setReviewRequiredFlag(module.getReviewRequiredFlag());
        entity.setReviewApprovedFlag(module.getReviewApprovedFlag());
        entity.setCreatedAt(module.getCreatedAt());
        entity.setUpdatedAt(module.getUpdatedAt());
        return entity;
    }

    private RequirementResponse toResponse(RequirementInfo entity) {
        RequirementTaskExecutionMarkerService.RequirementTaskExecutionMarker marker = taskExecutionMarkerService.get(entity.getRequirementNo());
        return RequirementResponse.builder()
                .id(entity.getId())
                .requirementNo(entity.getRequirementNo())
                .projectCode(entity.getProjectCode())
                .title(entity.getTitle())
                .requirementDesc(entity.getRequirementDesc())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .source(entity.getSource())
                .requirementType(entity.getRequirementType())
                .parentRequirementNo(entity.getParentRequirementNo())
                .rootRequirementNo(entity.getRootRequirementNo())
                .sortNo(entity.getSortNo())
                .mainAgentCode(entity.getMainAgentCode())
                .sessionStrategy(entity.getSessionStrategy())
                .preferredSessionCode(entity.getPreferredSessionCode())
                .currentStage(entity.getCurrentStage())
                .expectedDeadline(entity.getExpectedDeadline())
                .createdBy(entity.getCreatedBy())
                .executionSteps(entity.getExecutionSteps())
                .autoExecuteFlag(Boolean.TRUE.equals(entity.getAutoExecuteFlag()))
                .fileSearchMcpAgentCodes(entity.getFileSearchMcpAgentCodes())
                .mcpFileSearchEnabledFlag(false)
                .projectKnowledgeSearchEnabledFlag(false)
                .executionMode(defaultExecutionMode(entity.getExecutionMode(), RequirementExecutionMode.NORMAL))
                .resultExtractableFlag(defaultResultExtractable(entity.getResultExtractableFlag()))
                .reviewRequiredFlag(defaultReviewRequired(entity.getReviewRequiredFlag()))
                .reviewApprovedFlag(Boolean.TRUE.equals(entity.getReviewApprovedFlag()))
                .executionMarker(marker == null ? null : marker.markerId())
                .executionClientCode(marker == null ? null : marker.clientCode())
                .executionCommandId(marker == null ? null : marker.commandId())
                .executionSessionId(marker == null ? null : marker.sessionId())
                .executionAgentCode(marker == null ? null : marker.agentCode())
                .executionAcpFlag(marker == null ? null : marker.acpTask())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private RequirementResponse toResponse(RequirementModuleInfo entity) {
        RequirementTaskExecutionMarkerService.RequirementTaskExecutionMarker marker = taskExecutionMarkerService.get(entity.getRequirementNo());
        return RequirementResponse.builder()
                .id(entity.getId())
                .requirementNo(entity.getRequirementNo())
                .projectCode(entity.getProjectCode())
                .title(entity.getTitle())
                .requirementDesc(entity.getRequirementDesc())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .source(entity.getSource())
                .requirementType(RequirementType.SUB)
                .parentRequirementNo(entity.getParentRequirementNo())
                .rootRequirementNo(entity.getRootRequirementNo())
                .sortNo(entity.getSortNo())
                .mainAgentCode(entity.getMainAgentCode())
                .sessionStrategy(entity.getSessionStrategy())
                .preferredSessionCode(entity.getPreferredSessionCode())
                .currentStage(entity.getCurrentStage())
                .expectedDeadline(entity.getExpectedDeadline())
                .createdBy(entity.getCreatedBy())
                .executionSteps(entity.getExecutionSteps())
                .autoExecuteFlag(false)
                .fileSearchMcpAgentCodes(null)
                .mcpFileSearchEnabledFlag(Boolean.TRUE.equals(entity.getMcpFileSearchEnabledFlag()))
                .documentIds(entity.getDocumentIds())
                .projectKnowledgeSearchEnabledFlag(Boolean.TRUE.equals(entity.getProjectKnowledgeSearchEnabledFlag()))
                .projectKnowledgeSearchLimit(entity.getProjectKnowledgeSearchLimit())
                .projectKnowledgeSearchMinScore(entity.getProjectKnowledgeSearchMinScore())
                .executionMode(defaultExecutionMode(entity.getExecutionMode(), RequirementExecutionMode.NORMAL))
                .resultExtractableFlag(defaultResultExtractable(entity.getResultExtractableFlag()))
                .reviewRequiredFlag(defaultReviewRequired(entity.getReviewRequiredFlag()))
                .reviewApprovedFlag(Boolean.TRUE.equals(entity.getReviewApprovedFlag()))
                .executionMarker(marker == null ? null : marker.markerId())
                .executionClientCode(marker == null ? null : marker.clientCode())
                .executionCommandId(marker == null ? null : marker.commandId())
                .executionSessionId(marker == null ? null : marker.sessionId())
                .executionAgentCode(marker == null ? null : marker.agentCode())
                .executionAcpFlag(marker == null ? null : marker.acpTask())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
