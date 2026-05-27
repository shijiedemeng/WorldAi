package com.aiapi.client.service;

import com.aiapi.client.dto.ClientAgentSessionResponse;
import com.aiapi.client.dto.ClientCommandResponse;
import com.aiapi.client.dto.ClientControllableAgentResponse;
import com.aiapi.client.dto.ClientNodeResponse;
import com.aiapi.client.dto.ClientSessionEventResponse;
import com.aiapi.client.dto.CompleteClientAgentSessionRequest;
import com.aiapi.client.dto.CompleteClientCommandRequest;
import com.aiapi.client.dto.CreateClientAgentSessionRequest;
import com.aiapi.client.dto.DispatchClientCommandRequest;
import com.aiapi.client.dto.AppendClientSessionEventRequest;
import com.aiapi.client.event.ClientNodeDisconnectedEvent;
import com.aiapi.client.event.ClientNodeOnlineEvent;
import com.aiapi.client.websocket.ClientDispatchWebSocketHandler;
import com.aiapi.common.enums.AgentRuntimeType;
import com.aiapi.common.enums.AgentSessionStatus;
import com.aiapi.common.enums.ClientNodeStatus;
import com.aiapi.common.enums.DispatchStatus;
import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.enums.McpTransportProtocol;
import com.aiapi.common.enums.RequirementExecutionMode;
import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.common.enums.RequirementType;
import com.aiapi.common.enums.SessionStrategy;
import com.aiapi.common.exception.BizException;
import com.aiapi.link.dto.UpdateLinkProgressRequest;
import com.aiapi.link.service.RequirementDevLinkService;
import com.aiapi.requirement.dto.RequirementResponse;
import com.aiapi.requirement.entity.RequirementInfo;
import com.aiapi.requirement.event.RequirementTaskCompletedEvent;
import com.aiapi.requirement.service.RequirementService;
import com.aiapi.requirement.service.RequirementTaskExecutionMarkerService;
import com.aiapi.requirement.service.RequirementWorkflowService;
import com.aiapi.project.service.ProjectMarkdownService;
import com.aiapi.session.service.AgentSessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientExecutionService {

    private static final String SESSION_CREATING = "CREATING";
    private static final String SESSION_READY = "READY";
    private static final String SESSION_FAILED = "FAILED";
    private static final String DOCUMENT_SKILL_CODE = "ai-api-agent-integration";
    private static final int DEFAULT_PROJECT_KNOWLEDGE_SEARCH_LIMIT = 5;
    private static final double DEFAULT_PROJECT_KNOWLEDGE_SEARCH_MIN_SCORE = 70D;

    private final ClientNodeService clientNodeService;
    private final RequirementDevLinkService requirementDevLinkService;
    private final ClientDispatchWebSocketHandler webSocketHandler;
    private final AgentSessionService agentSessionService;
    private final RequirementService requirementService;
    private final RequirementTaskExecutionMarkerService taskExecutionMarkerService;
    private final RequirementWorkflowService requirementWorkflowService;
    private final ProjectMarkdownService projectMarkdownService;
    private final ObjectMapper objectMapper;
    private final Map<String, ClientAgentSessionRuntime> sessions = new ConcurrentHashMap<>();
    private final Map<String, ClientCommandRuntime> commands = new ConcurrentHashMap<>();
    private final Map<String, List<ClientSessionEventRuntime>> sessionEvents = new ConcurrentHashMap<>();

    public void clearClientRuntime(String clientCode) {
        String normalized = trimToNull(clientCode);
        if (normalized == null) {
            return;
        }
        sessions.entrySet().removeIf(entry -> entry.getValue().clientCode().equals(normalized));
        sessionEvents.entrySet().removeIf(entry -> entry.getKey().startsWith(normalized + ":"));
        commands.entrySet().removeIf(entry -> entry.getValue().clientCode().equals(normalized));
        taskExecutionMarkerService.clearByClient(normalized);
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredClientRuntime() {
        sessions.values().stream()
                .map(ClientAgentSessionRuntime::clientCode)
                .distinct()
                .filter(clientCode -> !clientNodeService.exists(clientCode))
                .toList()
                .forEach(this::clearClientRuntime);
    }

    public List<ClientAgentSessionResponse> listSessions(String clientCode, String agentCode) {
        ClientNodeResponse client = clientNodeService.get(clientCode);
        String normalizedAgentCode = trimToNull(agentCode);
        cleanupDuplicateDefaultSessions(client.getClientCode(), normalizedAgentCode);
        ensureDefaultSessionRequests(client, normalizedAgentCode);
        cleanupDuplicateDefaultSessions(client.getClientCode(), normalizedAgentCode);
        return sessions.values().stream()
                .filter(item -> item.clientCode().equals(clientCode))
                .filter(item -> normalizedAgentCode == null || item.agentCode().equals(normalizedAgentCode))
                .sorted(Comparator.comparing(ClientAgentSessionRuntime::updatedAt).reversed())
                .map(this::toSessionResponse)
                .toList();
    }

    public List<ClientAgentSessionResponse> listPendingSessionCreations(String clientCode) {
        ClientNodeResponse client = clientNodeService.get(clientCode);
        if (client.getStatus() != ClientNodeStatus.ONLINE) {
            return List.of();
        }
        cleanupDuplicateDefaultSessions(client.getClientCode(), null);
        return sessions.values().stream()
                .filter(item -> item.clientCode().equals(clientCode))
                .filter(item -> SESSION_CREATING.equals(item.status()))
                .sorted(Comparator.comparing(ClientAgentSessionRuntime::createdAt))
                .map(this::toSessionResponse)
                .toList();
    }

    public ClientAgentSessionResponse createSession(String clientCode, CreateClientAgentSessionRequest request) {
        ClientNodeResponse client = clientNodeService.get(clientCode);
        if (client.getStatus() != ClientNodeStatus.ONLINE) {
            throw new BizException(400, "client is offline");
        }
        ClientControllableAgentResponse agent = findLinkedAgent(client, request.getAgentCode());
        return queueSessionCreate(client, agent, request, true);
    }

    public ClientAgentSessionResponse completeSessionCreation(String clientCode, String requestId, CompleteClientAgentSessionRequest request) {
        ClientAgentSessionRuntime session = sessions.get(requestId);
        if (session == null || !session.clientCode().equals(clientCode)) {
            throw new BizException(404, "client session create request not found");
        }
        LocalDateTime now = LocalDateTime.now();
        String sessionId = trimToNull(request.getSessionId());
        String status = normalizeSessionStatus(request.getStatus(), sessionId);
        if (SESSION_READY.equals(status) && sessionId == null) {
            throw new BizException(400, "sessionId is required when session status is READY");
        }
        ClientAgentSessionRuntime updated = session.withCompletion(
                sessionId,
                defaultText(trimToNull(request.getRuntimeType()), session.runtimeType()),
                status,
                trimToNull(request.getErrorMessage()),
                request.getInitializedSkills() == null ? session.initializedSkills() : request.getInitializedSkills(),
                now
        );
        if (SESSION_READY.equals(status) && updated.defaultFlag()) {
            clearDefault(clientCode, updated.agentCode());
            updated = updated.withDefault(now);
        }
        sessions.put(requestId, updated);
        appendSessionEvent(
                updated,
                "IN",
                "SESSION_CREATE_RESULT",
                incomingHttpPayload("POST", "/api/clients/%s/sessions/%s/complete".formatted(clientCode, requestId), request)
        );
        cleanupDuplicateDefaultSessions(clientCode, updated.agentCode());
        if (SESSION_READY.equals(status)) {
            resumeWorkflowTasksForAgent(updated.agentCode(), true);
        }
        return toSessionResponse(updated);
    }

    public ClientAgentSessionResponse setDefaultSession(String clientCode, String sessionId) {
        ClientAgentSessionRuntime session = findSession(clientCode, sessionId);
        if (!SESSION_READY.equals(session.status()) || session.sessionId() == null) {
            throw new BizException(400, "client session is not ready");
        }
        clearDefault(clientCode, session.agentCode());
        ClientAgentSessionRuntime updated = session.withDefault(LocalDateTime.now());
        sessions.put(updated.requestId(), updated);
        return toSessionResponse(updated);
    }

    public ClientAgentSessionResponse appendClientSessionEvent(String clientCode, String sessionId, AppendClientSessionEventRequest request) {
        ClientAgentSessionRuntime session = findSession(clientCode, sessionId);
        appendSessionEvent(
                session,
                defaultText(trimToNull(request.getDirection()), "IN"),
                defaultText(trimToNull(request.getEventType()), "AGENT_EVENT"),
                defaultText(trimToNull(request.getPayload()), "-")
        );
        return toSessionResponse(session);
    }

    public ClientCommandResponse dispatch(DispatchClientCommandRequest request) {
        ClientNodeResponse client = clientNodeService.get(request.getClientCode());
        if (client.getStatus() != ClientNodeStatus.ONLINE) {
            throw new BizException(400, "client is offline");
        }
        if (taskExecutionMarkerService.isRunning(request.getRequirementNo())) {
            throw new BizException(400, "requirement task is already running");
        }
        findLinkedAgent(client, request.getAgentCode());
        ClientAgentSessionRuntime session = resolveSession(
                client,
                request.getAgentCode(),
                defaultText(trimToNull(request.getSessionId()), trimToNull(request.getSessionCode()))
        );
        LocalDateTime now = LocalDateTime.now();
        String commandId = "CMD-" + UUID.randomUUID();
        ClientCommandRuntime command = new ClientCommandRuntime(
                commandId,
                request.getClientCode().trim(),
                request.getAgentCode().trim(),
                session.sessionId(),
                request.getRequirementNo().trim(),
                request.getLinkId(),
                defaultText(trimToNull(request.getTitle()), "执行子模块任务"),
                buildDispatchPrompt(request, session),
                DispatchStatus.DISPATCHED,
                null,
                null,
                null,
                now,
                null,
                null,
                now
        );
        if (shouldMarkOnDispatch(command)) {
            markTaskRunning(command, now);
        }
        commands.put(commandId, command);
        ClientCommandResponse response = toCommandResponse(command);
        appendSessionEvent(session, "OUT", "EXECUTE_COMMAND", websocketPayload("EXECUTE_COMMAND", response));
        webSocketHandler.sendCommand(command.clientCode(), response);
        return response;
    }

    public List<ClientCommandResponse> listCommands(String clientCode) {
        return commands.values().stream()
                .filter(item -> item.clientCode().equals(clientCode))
                .sorted(Comparator.comparing(ClientCommandRuntime::createdAt).reversed())
                .map(this::toCommandResponse)
                .toList();
    }

    public List<ClientCommandResponse> listPendingCommands(String clientCode) {
        ClientNodeResponse client = clientNodeService.get(clientCode);
        if (client.getStatus() != ClientNodeStatus.ONLINE) {
            return List.of();
        }
        return commands.values().stream()
                .filter(item -> item.clientCode().equals(clientCode))
                .filter(item -> item.status() == DispatchStatus.DISPATCHED || item.status() == DispatchStatus.ACKED)
                .sorted(Comparator.comparing(ClientCommandRuntime::createdAt))
                .map(this::toCommandResponse)
                .toList();
    }

    public void prepareClientWorkflowSessions(String clientCode) {
        ClientNodeResponse client = clientNodeService.get(clientCode);
        if (client.getStatus() != ClientNodeStatus.ONLINE) {
            return;
        }
        ensureDefaultSessionRequests(client, null);
        for (ClientControllableAgentResponse agent : client.getAgents()) {
            resumeWorkflowTasksForAgent(agent.getAgentCode(), true);
        }
    }

    public ClientCommandResponse startCommand(String clientCode, String commandId) {
        ClientCommandRuntime command = findCommand(clientCode, commandId);
        LocalDateTime now = LocalDateTime.now();
        ClientCommandRuntime updated = command.withStatus(DispatchStatus.RUNNING, now, command.finishedAt(), now);
        commands.put(commandId, updated);
        appendCommandEvent(
                updated,
                "IN",
                "COMMAND_STARTED",
                incomingHttpPayload("POST", "/api/clients/%s/commands/%s/start".formatted(clientCode, commandId), null)
        );
        markTaskRunning(updated, now);
        markTaskExecutionIdentifier(updated);
        recordAgentExecutionSession(updated, AgentSessionStatus.RUNNING);
        return toCommandResponse(updated);
    }

    public ClientCommandResponse completeCommand(String clientCode, String commandId, CompleteClientCommandRequest request) {
        ClientCommandRuntime command = findCommand(clientCode, commandId);
        LocalDateTime now = LocalDateTime.now();
        DispatchStatus status = request.getStatus() == null ? DispatchStatus.SUCCESS : request.getStatus();
        ClientCommandRuntime updated = command.withResult(
                status,
                defaultText(trimToNull(request.getResultSummary()), status == DispatchStatus.SUCCESS ? "执行完成" : "执行失败"),
                trimToNull(request.getExecutionDetails()),
                trimToNull(request.getDeliverablePath()),
                command.startedAt() == null ? now : command.startedAt(),
                now,
                now
        );
        commands.put(commandId, updated);
        appendCommandEvent(
                updated,
                "IN",
                "COMMAND_COMPLETED",
                incomingHttpPayload("POST", "/api/clients/%s/commands/%s/complete".formatted(clientCode, commandId), request)
        );
        recordAgentExecutionSession(updated, updated.status() == DispatchStatus.SUCCESS ? AgentSessionStatus.IDLE : AgentSessionStatus.BLOCKED);
        taskExecutionMarkerService.clear(updated.requirementNo(), updated.commandId());
        updateLinkIfNeeded(updated);
        updateRequirementIfNoLink(updated);
        if (updated.linkId() == null && updated.status() == DispatchStatus.SUCCESS) {
            dispatchNextWorkflowTasks(updated.requirementNo());
        }
        resumeWorkflowTasksForAgent(updated.agentCode(), false);
        return toCommandResponse(updated);
    }

    @EventListener
    public void handleRequirementTaskCompleted(RequirementTaskCompletedEvent event) {
        dispatchNextWorkflowTasks(event.requirementNo());
    }

    @EventListener
    public void handleClientNodeDisconnected(ClientNodeDisconnectedEvent event) {
        releaseClientRunningTasks(event.clientCode());
    }

    @EventListener
    public void handleClientNodeOnline(ClientNodeOnlineEvent event) {
        prepareClientWorkflowSessions(event.clientCode());
    }

    public List<ClientCommandResponse> dispatchReadyWorkflowTasks(String rootRequirementNo) {
        RequirementInfo root = requirementService.findEntity(rootRequirementNo);
        if (root.getRequirementType() != RequirementType.MASTER) {
            root = requirementService.findEntity(root.getRootRequirementNo());
        }
        if (root.getExecutionMode() != RequirementExecutionMode.WORKFLOW) {
            return List.of();
        }
        return dispatchWorkflowTasks(root, requirementWorkflowService.listReadyRequirements(root.getRequirementNo()));
    }

    private void dispatchNextWorkflowTasks(String completedRequirementNo) {
        String normalized = trimToNull(completedRequirementNo);
        if (normalized == null) {
            return;
        }
        RequirementInfo completed;
        try {
            completed = requirementService.findEntity(normalized);
        } catch (RuntimeException ignored) {
            return;
        }
        if (completed.getRequirementType() != RequirementType.SUB) {
            return;
        }
        RequirementInfo root = requirementService.findEntity(completed.getRootRequirementNo());
        if (root.getExecutionMode() != RequirementExecutionMode.WORKFLOW) {
            return;
        }
        dispatchWorkflowTasks(root, requirementWorkflowService.listNextReadyRequirements(completedRequirementNo));
    }

    private void resumeWorkflowTasksForAgent(String agentCode, boolean autoExecutableOnly) {
        String normalizedAgentCode = trimToNull(agentCode);
        if (normalizedAgentCode == null) {
            return;
        }
        List<String> roots = requirementService.listOpenTasks(null, normalizedAgentCode).stream()
                .filter(item -> item.getRequirementType() == RequirementType.SUB)
                .filter(item -> item.getExecutionMode() == RequirementExecutionMode.WORKFLOW)
                .map(RequirementResponse::getRootRequirementNo)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .toList();
        for (String root : roots) {
            if (autoExecutableOnly && !isAutoExecutableRoot(root)) {
                continue;
            }
            dispatchReadyWorkflowTasks(root);
        }
    }

    private boolean isAutoExecutableRoot(String rootRequirementNo) {
        try {
            RequirementInfo root = requirementService.findEntity(rootRequirementNo);
            return Boolean.TRUE.equals(root.getAutoExecuteFlag());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private List<ClientCommandResponse> dispatchWorkflowTasks(RequirementInfo root, List<RequirementResponse> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        List<ClientCommandResponse> responses = new ArrayList<>();
        Map<String, List<RequirementResponse>> targetsByAgent = new LinkedHashMap<>();
        for (RequirementResponse target : targets) {
            String agentCode = trimToNull(target.getMainAgentCode());
            if (!isExecutableWorkflowTask(target) || agentCode == null) {
                continue;
            }
            targetsByAgent.computeIfAbsent(agentCode, ignored -> new ArrayList<>()).add(target);
        }
        targetsByAgent.forEach((agentCode, agentTargets) -> responses.addAll(dispatchWorkflowAgentTasks(root, agentCode, agentTargets)));
        return responses;
    }

    private List<ClientCommandResponse> dispatchWorkflowAgentTasks(RequirementInfo root,
                                                                   String agentCode,
                                                                   List<RequirementResponse> targets) {
        List<ClientAgentRuntimeTarget> runtimeTargets = findOnlineAgentTargets(agentCode);
        if (runtimeTargets.isEmpty()) {
            return List.of();
        }
        int requiredParallelSessions = targets.size();
        List<ClientAgentSessionRuntime> activeSessions = listWorkflowAgentSessions(agentCode, runtimeTargets);
        long readyCount = activeSessions.stream()
                .filter(item -> SESSION_READY.equals(item.status()))
                .filter(item -> item.sessionId() != null)
                .count();
        long creatingCount = activeSessions.stream()
                .filter(item -> SESSION_CREATING.equals(item.status()))
                .count();
        List<ClientAgentSessionRuntime> idleSessions = activeSessions.stream()
                .filter(item -> SESSION_READY.equals(item.status()))
                .filter(item -> item.sessionId() != null)
                .filter(item -> !isSessionBusy(item))
                .sorted(this::compareWorkflowIdleSession)
                .toList();

        Set<String> assignedSessionKeys = new HashSet<>();
        List<WorkflowDispatchAssignment> assignments = new ArrayList<>();
        List<RequirementResponse> pendingTargets = new ArrayList<>();
        for (RequirementResponse target : targets) {
            ClientAgentSessionRuntime session = chooseWorkflowSession(target, idleSessions, assignedSessionKeys);
            if (session == null) {
                pendingTargets.add(target);
                continue;
            }
            ClientNodeResponse client = runtimeTargets.stream()
                    .map(ClientAgentRuntimeTarget::client)
                    .filter(item -> item.getClientCode().equals(session.clientCode()))
                    .findFirst()
                    .orElse(null);
            if (client == null) {
                return List.of();
            }
            assignedSessionKeys.add(sessionRuntimeKey(session));
            assignments.add(new WorkflowDispatchAssignment(target, client, session));
        }

        int pendingNewSessionTasks = (int) pendingTargets.stream()
                .filter(this::shouldCreateWorkflowSessionWhenBusy)
                .count();
        int missingSessionCapacity = Math.max(0, requiredParallelSessions - (int) (readyCount + creatingCount));
        if (missingSessionCapacity > 0 && pendingNewSessionTasks > 0) {
            queueWorkflowSessionCreates(runtimeTargets, agentCode, Math.min(missingSessionCapacity, pendingNewSessionTasks));
        } else if (creatingCount == 0 && pendingNewSessionTasks > 0) {
            queueWorkflowSessionCreates(runtimeTargets, agentCode, pendingNewSessionTasks);
        }

        List<ClientCommandResponse> responses = new ArrayList<>();
        for (WorkflowDispatchAssignment assignment : assignments) {
            RequirementResponse target = assignment.target();
            DispatchClientCommandRequest request = new DispatchClientCommandRequest();
            request.setClientCode(assignment.client().getClientCode());
            request.setAgentCode(agentCode);
            request.setSessionId(assignment.session().sessionId());
            request.setRequirementNo(target.getRequirementNo());
            request.setTitle(target.getTitle());
            request.setPrompt(workflowPrompt(root, target));
            try {
                responses.add(dispatch(request));
            } catch (RuntimeException ex) {
                log.warn("workflow task dispatch skipped, requirementNo={}", target.getRequirementNo(), ex);
            }
        }
        return responses;
    }

    private List<ClientAgentRuntimeTarget> findOnlineAgentTargets(String agentCode) {
        return clientNodeService.list().stream()
                .filter(client -> client.getStatus() == ClientNodeStatus.ONLINE)
                .flatMap(client -> client.getAgents().stream()
                        .filter(agent -> agent.getAgentCode().equals(agentCode))
                        .map(agent -> new ClientAgentRuntimeTarget(client, agent)))
                .toList();
    }

    private List<ClientAgentSessionRuntime> listWorkflowAgentSessions(String agentCode,
                                                                      List<ClientAgentRuntimeTarget> runtimeTargets) {
        Set<String> onlineClientCodes = runtimeTargets.stream()
                .map(target -> target.client().getClientCode())
                .collect(java.util.stream.Collectors.toSet());
        return sessions.values().stream()
                .filter(item -> onlineClientCodes.contains(item.clientCode()))
                .filter(item -> item.agentCode().equals(agentCode))
                .filter(item -> SESSION_READY.equals(item.status()) || SESSION_CREATING.equals(item.status()))
                .sorted(Comparator.comparing(ClientAgentSessionRuntime::updatedAt))
                .toList();
    }

    private void queueWorkflowSessionCreates(List<ClientAgentRuntimeTarget> runtimeTargets, String agentCode, int count) {
        if (count <= 0 || runtimeTargets.isEmpty()) {
            return;
        }
        ClientAgentRuntimeTarget target = runtimeTargets.get(0);
        for (int index = 0; index < count; index++) {
            CreateClientAgentSessionRequest request = new CreateClientAgentSessionRequest();
            request.setAgentCode(agentCode);
            request.setSessionName("工作流并行会话");
            request.setSessionType("CLI_PROMPT");
            request.setDefaultFlag(false);
            request.setWorkspaceDir(target.agent().getWorkspaceDir());
            queueSessionCreate(target.client(), target.agent(), request, false);
        }
    }

    private ClientAgentSessionRuntime chooseWorkflowSession(RequirementResponse target,
                                                            List<ClientAgentSessionRuntime> idleSessions,
                                                            Set<String> assignedSessionKeys) {
        SessionStrategy strategy = target.getSessionStrategy() == null ? SessionStrategy.NEW : target.getSessionStrategy();
        if (strategy == SessionStrategy.REUSE_SELECTED) {
            String preferredSessionCode = trimToNull(target.getPreferredSessionCode());
            if (preferredSessionCode == null) {
                return null;
            }
            return idleSessions.stream()
                    .filter(item -> !assignedSessionKeys.contains(sessionRuntimeKey(item)))
                    .filter(item -> preferredSessionCode.equals(item.sessionId()) || preferredSessionCode.equals(item.requestId()))
                    .findFirst()
                    .orElse(null);
        }
        Comparator<ClientAgentSessionRuntime> comparator = Comparator.comparing(ClientAgentSessionRuntime::updatedAt);
        if (strategy == SessionStrategy.REUSE_LATEST) {
            return idleSessions.stream()
                    .filter(item -> !assignedSessionKeys.contains(sessionRuntimeKey(item)))
                    .max(comparator)
                    .orElse(null);
        }
        return idleSessions.stream()
                .filter(item -> !assignedSessionKeys.contains(sessionRuntimeKey(item)))
                .min(this::compareWorkflowIdleSession)
                .orElse(null);
    }

    private int compareWorkflowIdleSession(ClientAgentSessionRuntime left, ClientAgentSessionRuntime right) {
        int defaultCompare = Boolean.compare(!left.defaultFlag(), !right.defaultFlag());
        if (defaultCompare != 0) {
            return defaultCompare;
        }
        return left.updatedAt().compareTo(right.updatedAt());
    }

    private boolean shouldCreateWorkflowSessionWhenBusy(RequirementResponse target) {
        SessionStrategy strategy = target.getSessionStrategy() == null ? SessionStrategy.NEW : target.getSessionStrategy();
        return strategy == SessionStrategy.NEW;
    }

    private boolean isSessionBusy(ClientAgentSessionRuntime session) {
        return commands.values().stream()
                .filter(command -> session.clientCode().equals(command.clientCode()))
                .filter(command -> session.sessionId() != null && session.sessionId().equals(command.sessionId()))
                .anyMatch(command -> !isFinishedCommand(command.status()));
    }

    private String sessionRuntimeKey(ClientAgentSessionRuntime session) {
        return session.clientCode() + ":" + session.sessionId();
    }

    private ClientDispatchTarget findReadyDispatchTarget(String agentCode) {
        for (ClientNodeResponse client : clientNodeService.list()) {
            if (client.getStatus() != ClientNodeStatus.ONLINE) {
                continue;
            }
            ClientControllableAgentResponse linkedAgent = client.getAgents().stream()
                    .filter(agent -> agent.getAgentCode().equals(agentCode))
                    .findFirst()
                    .orElse(null);
            if (linkedAgent == null) {
                continue;
            }
            ClientAgentSessionRuntime session = findDefaultSession(client.getClientCode(), agentCode);
            if (session != null && SESSION_READY.equals(session.status()) && session.sessionId() != null) {
                return new ClientDispatchTarget(client, linkedAgent, session);
            }
            ensureDefaultSessionRequests(client, agentCode);
        }
        return null;
    }

    private String workflowPrompt(RequirementInfo root, RequirementResponse target) {
        return """
                当前使用已初始化的客户端长会话执行工作流子模块，不要重复初始化或重新加载技能。
                这是 ai-api 工作流模式任务，本次任务开始状态已由服务端标记为执行中。

                工作流根需求：%s
                本次执行子模块：%s
                任务标题：%s
                负责 Agent：%s

                工作流要求：
                1. 执行前先用 ai-api-agent-integration 查询当前子模块和工作流历史结果。
                2. 历史结果接口：GET /api/requirements/%s/workflow-results，只会返回允许提取响应的已完成子模块回执。
                3. 如果历史结果里有上游回执，需要整合上游结果后再处理当前子模块。
                4. 若没有已有开发链路，请先为当前子模块创建开发链路；完成后回写 DONE，失败或无法继续回写 BLOCKED。
                5. 回执只写回 ai-api 的 executionDetails 字段，不要在本地创建 receipt.md、receipt_*.md 或其他任务回执文件。
                """.formatted(
                root.getRequirementNo(),
                target.getRequirementNo(),
                target.getTitle(),
                defaultText(target.getMainAgentCode(), "-"),
                target.getRequirementNo());
    }

    private boolean isExecutableWorkflowTask(RequirementResponse item) {
        if (taskExecutionMarkerService.isRunning(item.getRequirementNo())) {
            return false;
        }
        return item.getStatus() == RequirementStatus.PENDING || item.getStatus() == RequirementStatus.BLOCKED;
    }

    private record ClientDispatchTarget(
            ClientNodeResponse client,
            ClientControllableAgentResponse agent,
            ClientAgentSessionRuntime session
    ) {
    }

    private record ClientAgentRuntimeTarget(
            ClientNodeResponse client,
            ClientControllableAgentResponse agent
    ) {
    }

    private record WorkflowDispatchAssignment(
            RequirementResponse target,
            ClientNodeResponse client,
            ClientAgentSessionRuntime session
    ) {
    }

    private ClientAgentSessionResponse queueSessionCreate(ClientNodeResponse client,
                                                          ClientControllableAgentResponse agent,
                                                          CreateClientAgentSessionRequest request,
                                                          boolean retryExistingFailedDefault) {
        LocalDateTime now = LocalDateTime.now();
        cleanupDuplicateDefaultSessions(client.getClientCode(), agent.getAgentCode());
        List<Object> initializedSkills = List.of();
        boolean defaultFlag = Boolean.TRUE.equals(request.getDefaultFlag()) || noDefaultSessionFor(client.getClientCode(), agent.getAgentCode());
        if (defaultFlag) {
            ClientAgentSessionRuntime existingDefault = findDefaultSession(client.getClientCode(), agent.getAgentCode());
            if (existingDefault != null) {
                if (retryExistingFailedDefault && SESSION_FAILED.equals(existingDefault.status())) {
                    ClientAgentSessionRuntime retried = existingDefault.withCreateRetry(
                            defaultText(trimToNull(request.getSessionName()), existingDefault.sessionName()),
                            defaultText(trimToNull(request.getSessionType()), existingDefault.sessionType()),
                            defaultText(trimToNull(request.getRuntimeType()), existingDefault.runtimeType()),
                            defaultText(trimToNull(request.getWorkspaceDir()), defaultText(agent.getWorkspaceDir(), existingDefault.workspaceDir())),
                            existingDefault.initializedSkills(),
                            now
                    );
                    sessions.put(retried.requestId(), retried);
                    ClientAgentSessionResponse response = toSessionResponse(retried);
                    appendSessionEvent(retried, "OUT", "CREATE_SESSION", websocketPayload("CREATE_SESSION", response));
                    webSocketHandler.sendSessionCreate(client.getClientCode(), response);
                    return response;
                }
                return toSessionResponse(existingDefault);
            }
        }
        String requestId = "SESSION_REQ-" + UUID.randomUUID();
        ClientAgentSessionRuntime runtime = new ClientAgentSessionRuntime(
                requestId,
                null,
                client.getClientCode(),
                agent.getAgentCode(),
                defaultText(trimToNull(request.getSessionName()), "默认会话"),
                defaultText(trimToNull(request.getSessionType()), "CLI_PROMPT"),
                defaultText(trimToNull(request.getRuntimeType()), null),
                defaultFlag,
                SESSION_CREATING,
                defaultText(trimToNull(request.getWorkspaceDir()), agent.getWorkspaceDir()),
                null,
                initializedSkills,
                now,
                now
        );
        sessions.put(requestId, runtime);
        ClientAgentSessionResponse response = toSessionResponse(runtime);
        appendSessionEvent(runtime, "OUT", "CREATE_SESSION", websocketPayload("CREATE_SESSION", response));
        webSocketHandler.sendSessionCreate(client.getClientCode(), response);
        return response;
    }

    private void ensureDefaultSessionRequests(ClientNodeResponse client, String agentCode) {
        String normalizedAgentCode = trimToNull(agentCode);
        cleanupDuplicateDefaultSessions(client.getClientCode(), normalizedAgentCode);
        for (ClientControllableAgentResponse agent : client.getAgents()) {
            if (normalizedAgentCode != null && !normalizedAgentCode.equals(agent.getAgentCode())) {
                continue;
            }
            if (noDefaultSessionFor(client.getClientCode(), agent.getAgentCode())) {
                CreateClientAgentSessionRequest request = new CreateClientAgentSessionRequest();
                request.setAgentCode(agent.getAgentCode());
                request.setSessionName("默认会话");
                request.setSessionType("CLI_PROMPT");
                request.setDefaultFlag(true);
                request.setWorkspaceDir(agent.getWorkspaceDir());
                queueSessionCreate(client, agent, request, false);
            }
        }
    }

    private ClientAgentSessionRuntime resolveSession(ClientNodeResponse client, String agentCode, String sessionId) {
        ensureDefaultSessionRequests(client, agentCode);
        if (sessionId != null) {
            ClientAgentSessionRuntime session = findSession(client.getClientCode(), sessionId);
            if (!session.agentCode().equals(agentCode)) {
                throw new BizException(404, "client session not found");
            }
            if (!SESSION_READY.equals(session.status()) || session.sessionId() == null) {
                throw new BizException(400, "client session is not ready");
            }
            return session;
        }
        return sessions.values().stream()
                .filter(item -> item.clientCode().equals(client.getClientCode()))
                .filter(item -> item.agentCode().equals(agentCode))
                .filter(item -> SESSION_READY.equals(item.status()))
                .filter(item -> item.sessionId() != null)
                .filter(ClientAgentSessionRuntime::defaultFlag)
                .findFirst()
                .orElseThrow(() -> new BizException(404, "default client session not ready"));
    }

    private ClientAgentSessionRuntime findSession(String clientCode, String sessionIdOrRequestId) {
        String normalized = trimToNull(sessionIdOrRequestId);
        if (normalized == null) {
            throw new BizException(400, "sessionId is required");
        }
        return sessions.values().stream()
                .filter(item -> item.clientCode().equals(clientCode))
                .filter(item -> normalized.equals(item.sessionId()) || normalized.equals(item.requestId()))
                .findFirst()
                .orElseThrow(() -> new BizException(404, "client session not found"));
    }

    private ClientControllableAgentResponse findLinkedAgent(ClientNodeResponse client, String agentCode) {
        String normalized = trimToNull(agentCode);
        if (normalized == null) {
            throw new BizException(400, "agentCode is required");
        }
        return client.getAgents().stream()
                .filter(item -> normalized.equals(item.getAgentCode()))
                .findFirst()
                .orElseThrow(() -> new BizException(400, "agent is not linked to this online client"));
    }

    private boolean noDefaultSessionFor(String clientCode, String agentCode) {
        return findDefaultSession(clientCode, agentCode) == null;
    }

    private ClientAgentSessionRuntime findDefaultSession(String clientCode, String agentCode) {
        return sessions.values().stream()
                .filter(item -> item.clientCode().equals(clientCode))
                .filter(item -> item.agentCode().equals(agentCode))
                .filter(ClientAgentSessionRuntime::defaultFlag)
                .max(this::compareDefaultSession)
                .orElse(null);
    }

    private void cleanupDuplicateDefaultSessions(String clientCode, String agentCode) {
        String normalizedAgentCode = trimToNull(agentCode);
        Map<String, List<ClientAgentSessionRuntime>> grouped = new LinkedHashMap<>();
        sessions.values().stream()
                .filter(item -> item.clientCode().equals(clientCode))
                .filter(item -> normalizedAgentCode == null || item.agentCode().equals(normalizedAgentCode))
                .filter(ClientAgentSessionRuntime::defaultFlag)
                .forEach(item -> grouped.computeIfAbsent(item.agentCode(), ignored -> new java.util.ArrayList<>()).add(item));
        grouped.values().forEach(items -> {
            if (items.size() <= 1) {
                return;
            }
            ClientAgentSessionRuntime keeper = items.stream().max(this::compareDefaultSession).orElse(null);
            if (keeper == null) {
                return;
            }
            items.stream()
                    .filter(item -> !item.requestId().equals(keeper.requestId()))
                    .forEach(item -> {
                        sessions.remove(item.requestId());
                        sessionEvents.remove(sessionEventKey(item));
                    });
        });
    }

    private int compareDefaultSession(ClientAgentSessionRuntime left, ClientAgentSessionRuntime right) {
        int statusCompare = Integer.compare(sessionStatusRank(left.status()), sessionStatusRank(right.status()));
        if (statusCompare != 0) {
            return statusCompare;
        }
        return left.updatedAt().compareTo(right.updatedAt());
    }

    private int sessionStatusRank(String status) {
        if (SESSION_READY.equals(status)) {
            return 3;
        }
        if (SESSION_CREATING.equals(status)) {
            return 2;
        }
        if (SESSION_FAILED.equals(status)) {
            return 1;
        }
        return 0;
    }

    private void clearDefault(String clientCode, String agentCode) {
        LocalDateTime now = LocalDateTime.now();
        sessions.replaceAll((key, value) -> value.clientCode().equals(clientCode) && value.agentCode().equals(agentCode)
                ? value.withoutDefault(now)
                : value);
    }

    private ClientCommandRuntime findCommand(String clientCode, String commandId) {
        ClientCommandRuntime command = commands.get(commandId);
        if (command == null || !command.clientCode().equals(clientCode)) {
            throw new BizException(404, "client command not found");
        }
        return command;
    }

    private void updateLinkIfNeeded(ClientCommandRuntime command) {
        if (command.linkId() == null) {
            return;
        }
        UpdateLinkProgressRequest request = new UpdateLinkProgressRequest();
        request.setStatus(command.status() == DispatchStatus.SUCCESS ? LinkStatus.DONE : LinkStatus.BLOCKED);
        request.setResultSummary(defaultText(command.resultSummary(), "客户端执行完成"));
        request.setExecutionDetails(defaultText(command.executionDetails(), command.resultSummary()));
        request.setDeliverablePath(command.deliverablePath());
        request.setStartedAt(command.startedAt());
        request.setFinishedAt(command.finishedAt());
        requirementDevLinkService.updateProgress(command.linkId(), request);
    }

    private void markLinkRunning(ClientCommandRuntime command, LocalDateTime startedAt) {
        if (command.linkId() == null) {
            return;
        }
        UpdateLinkProgressRequest request = new UpdateLinkProgressRequest();
        request.setStatus(LinkStatus.DOING);
        request.setStartedAt(startedAt);
        requirementDevLinkService.updateProgress(command.linkId(), request);
    }

    private void markTaskRunning(ClientCommandRuntime command, LocalDateTime startedAt) {
        if (command.linkId() == null) {
            requirementService.markExecutionStarted(command.requirementNo());
            return;
        }
        markLinkRunning(command, startedAt);
    }

    private boolean shouldMarkOnDispatch(ClientCommandRuntime command) {
        try {
            RequirementInfo requirement = requirementService.findEntity(command.requirementNo());
            return requirement.getExecutionMode() == RequirementExecutionMode.WORKFLOW;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void updateRequirementIfNoLink(ClientCommandRuntime command) {
        if (command.linkId() != null) {
            return;
        }
        boolean success = command.status() == DispatchStatus.SUCCESS;
        requirementService.finishExecutionWithoutLink(command.requirementNo(), success);
    }

    private void markTaskExecutionIdentifier(ClientCommandRuntime command) {
        RequirementInfo requirement;
        try {
            requirement = requirementService.findEntity(command.requirementNo());
        } catch (RuntimeException ignored) {
            return;
        }
        ClientAgentSessionRuntime session = findRuntimeSessionForCommand(command);
        boolean acpTask = session != null && "ACP".equalsIgnoreCase(session.sessionType());
        taskExecutionMarkerService.markRunning(
                requirement.getRequirementNo(),
                requirement.getRootRequirementNo(),
                command.commandId(),
                command.clientCode(),
                command.agentCode(),
                command.sessionId(),
                acpTask
        );
    }

    public void releaseClientRunningTasks(String clientCode) {
        String normalized = trimToNull(clientCode);
        if (normalized == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        commands.values().stream()
                .filter(command -> normalized.equals(command.clientCode()))
                .filter(command -> !isFinishedCommand(command.status()))
                .toList()
                .forEach(command -> {
                    ClientCommandRuntime released = command.withResult(
                            DispatchStatus.CANCELLED,
                            "客户端断开，任务执行标识已释放",
                            "客户端断开连接，服务端已释放该子模块执行标识，等待重新下发。",
                            command.deliverablePath(),
                            command.startedAt(),
                            now,
                            now
                    );
                    commands.put(released.commandId(), released);
                    taskExecutionMarkerService.clear(released.requirementNo(), released.commandId());
                    releaseLinkIfNeeded(released);
                    requirementService.releaseExecutionLock(released.requirementNo());
                });
    }

    private boolean isFinishedCommand(DispatchStatus status) {
        return status == DispatchStatus.SUCCESS
                || status == DispatchStatus.FAILED
                || status == DispatchStatus.BLOCKED
                || status == DispatchStatus.CANCELLED
                || status == DispatchStatus.TIMEOUT;
    }

    private void releaseLinkIfNeeded(ClientCommandRuntime command) {
        if (command.linkId() == null) {
            return;
        }
        UpdateLinkProgressRequest request = new UpdateLinkProgressRequest();
        request.setStatus(LinkStatus.TODO);
        requirementDevLinkService.updateProgress(command.linkId(), request);
    }

    private void recordAgentExecutionSession(ClientCommandRuntime command, AgentSessionStatus status) {
        if (command.sessionId() == null) {
            return;
        }
        ClientAgentSessionRuntime runtimeSession = findRuntimeSessionForCommand(command);
        if (runtimeSession == null) {
            return;
        }
        try {
            RequirementInfo requirement = requirementService.findEntity(command.requirementNo());
            agentSessionService.recordExecutedClientSession(
                    command.clientCode(),
                    command.agentCode(),
                    command.sessionId(),
                    normalizeRuntimeType(runtimeSession.runtimeType()),
                    requirement.getProjectCode(),
                    requirement.getRequirementNo(),
                    requirement.getRootRequirementNo(),
                    runtimeSession.sessionName(),
                    status
            );
        } catch (RuntimeException ex) {
            log.warn("failed to record executed agent session, commandId={}", command.commandId(), ex);
        }
    }

    private ClientAgentSessionRuntime findRuntimeSessionForCommand(ClientCommandRuntime command) {
        return sessions.values().stream()
                .filter(item -> item.clientCode().equals(command.clientCode()))
                .filter(item -> command.sessionId().equals(item.sessionId()))
                .findFirst()
                .orElse(null);
    }

    private AgentRuntimeType normalizeRuntimeType(String runtimeType) {
        String normalized = defaultText(runtimeType, AgentRuntimeType.CODEX_CLI.name()).toUpperCase();
        try {
            return AgentRuntimeType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return AgentRuntimeType.CODEX_CLI;
        }
    }

    private String defaultPrompt(DispatchClientCommandRequest request) {
        return """
                当前使用已初始化的客户端长会话执行任务，不要重复初始化或重新加载技能。
                需要查询任务、创建链路或回写结果时，直接使用会话中已初始化的 ai-api-agent-integration 能力。
                requirementNo: %s
                linkId: %s
                agentCode: %s
                如果 linkId 为空，说明这是按子模块直接下发的任务：必须先用 ai-api-agent-integration 查询当前需求和子模块，再为该子模块创建开发链路。
                请围绕该子模块完成实际任务，完成后回写开发链路为 DONE；无法完成时回写 BLOCKED，并给出原因、执行过程和详细回执。
                回执内容只需要回写到 ai-api 的 executionDetails 字段，不要在当前工作目录创建 receipt.md、receipt_*.md 或其他任务回执文件。
                """.formatted(
                request.getRequirementNo(),
                request.getLinkId() == null ? "-" : request.getLinkId(),
                request.getAgentCode());
    }

    private String buildDispatchPrompt(DispatchClientCommandRequest request, ClientAgentSessionRuntime session) {
        String basePrompt = defaultText(trimToNull(request.getPrompt()), defaultPrompt(request));
        RequirementResponse target = requirementService.get(request.getRequirementNo());
        String documentPrompt = taskDocumentPrompt(target, hasInitializedSkill(session, DOCUMENT_SKILL_CODE));
        return joinPromptSections(
                basePrompt,
                documentPrompt,
                projectKnowledgeSearchPrompt(target),
                mcpFileSearchPrompt(request.getRequirementNo())
        );
    }

    private String joinPromptSections(String... sections) {
        StringBuilder builder = new StringBuilder();
        for (String section : sections) {
            String text = trimToNull(section);
            if (text == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(text);
        }
        return builder.toString();
    }

    private boolean hasInitializedSkill(ClientAgentSessionRuntime session, String skillCode) {
        String normalizedSkillCode = trimToNull(skillCode);
        if (session == null || session.initializedSkills() == null || normalizedSkillCode == null) {
            return false;
        }
        for (Object item : session.initializedSkills()) {
            if (!(item instanceof Map<?, ?> record)) {
                continue;
            }
            Object code = record.get("skillCode");
            String normalizedCode = trimToNull(code == null ? null : code.toString());
            if (!normalizedSkillCode.equals(normalizedCode)) {
                continue;
            }
            Object matched = record.get("matched");
            if (matched instanceof Boolean && !((Boolean) matched)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private String taskDocumentPrompt(RequirementResponse target, boolean useSkill) {
        String documentIds = trimToNull(target.getDocumentIds());
        if (documentIds == null) {
            return "";
        }
        if (useSkill) {
            return """

                    子模块关联文档：
                    当前会话已初始化 ai-api-agent-integration，请优先通过该技能按以下 documentId 查询对应文档，并带出子文档/子树内容后再开始执行。
                    documentId 列表：%s
                    """.formatted(documentIds);
        }
        String context = projectMarkdownService.buildDocumentContextByCsv(documentIds);
        if (trimToNull(context) == null) {
            return """

                    子模块关联文档：
                    当前子模块配置了关联文档，但服务端没有读取到有效文档内容；请先确认文档库关联是否正确。
                    """;
        }
        return """

                子模块关联文档：
                当前会话未初始化 ai-api-agent-integration，服务端已提前查询并注入以下文档内容；执行任务前必须先阅读并遵守。
                %s
                """.formatted(context);
    }

    private String projectKnowledgeSearchPrompt(RequirementResponse target) {
        if (!Boolean.TRUE.equals(target.getProjectKnowledgeSearchEnabledFlag())) {
            return "";
        }
        String projectCode = trimToNull(target.getProjectCode());
        if (projectCode == null) {
            return "";
        }
        int limit = normalizeProjectKnowledgeSearchLimit(target.getProjectKnowledgeSearchLimit());
        double minScore = normalizeProjectKnowledgeSearchMinScore(target.getProjectKnowledgeSearchMinScore());
        return """

                项目储备库：
                当前子模块允许阅读项目储备库。执行前可以通过 project-knowledge-language-search 技能查询一次项目储备库，优先根据任务标题、任务描述、执行步骤提炼 query。
                固定项目编号 projectCode：%s
                数量约束 limit：%d
                最低准确值 minMatchScore：%s（百分制）
                如果当前会话没有 project-knowledge-language-search 技能，或连续 2-3 次无法访问/查询失败，直接跳过，不要中断任务；回执中简单说明项目储备库读取已跳过及原因。
                """.formatted(projectCode, limit, formatProjectKnowledgeSearchScore(minScore));
    }

    private int normalizeProjectKnowledgeSearchLimit(Integer value) {
        if (value == null) {
            return DEFAULT_PROJECT_KNOWLEDGE_SEARCH_LIMIT;
        }
        return Math.min(Math.max(value, 1), 20);
    }

    private double normalizeProjectKnowledgeSearchMinScore(Double value) {
        if (value == null) {
            return DEFAULT_PROJECT_KNOWLEDGE_SEARCH_MIN_SCORE;
        }
        return Math.min(Math.max(value, 0D), 100D);
    }

    private String formatProjectKnowledgeSearchScore(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

    private String mcpFileSearchPrompt(String requirementNo) {
        try {
            RequirementResponse target = requirementService.get(requirementNo);
            RequirementInfo root = target.getRequirementType() == RequirementType.MASTER
                    ? requirementService.findEntity(target.getRequirementNo())
                    : requirementService.findEntity(target.getRootRequirementNo());
            return mcpFileSearchPrompt(root, target);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String mcpFileSearchPrompt(RequirementInfo root, RequirementResponse target) {
        if (!Boolean.TRUE.equals(target.getMcpFileSearchEnabledFlag())) {
            return "";
        }
        String agentCodes = trimToNull(root.getFileSearchMcpAgentCodes());
        if (agentCodes == null) {
            return """

                    MCP 文件检索：
                    当前子模块已启用 MCP 文件检索，但总需求未配置可用 MCP Agent，请先在页面补充后再调用。
                    """;
        }
        return """

                MCP 文件检索：
                当前子模块允许使用服务端 MCP 文件检索能力。
                不同 Agent 的 MCP 传输协议以 Agent 配置为准；如果会话未预置 MCP，请优先使用下面给出的会话配置或接口地址。
                可用 Agent：
                %s
                支持操作：TREE、LIST、READ、SEARCH。请求时必须选择上述 Agent 编号。
                """.formatted(formatMcpAgentDescriptions(agentCodes));
    }

    private String formatMcpAgentDescriptions(String agentCodes) {
        Set<String> configured = List.of(agentCodes.split(",")).stream()
                .map(this::trimToNull)
                .filter(item -> item != null)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (configured.isEmpty()) {
            return "-";
        }
        List<String> lines = clientNodeService.list().stream()
                .flatMap(client -> client.getAgents().stream()
                        .filter(agent -> configured.contains(agent.getAgentCode()))
                        .map(agent -> formatMcpAgentDescription(client, agent)))
                .distinct()
                .toList();
        if (!lines.isEmpty()) {
            return String.join("\n", lines);
        }
        return configured.stream().map(item -> "- " + item).collect(java.util.stream.Collectors.joining("\n"));
    }

    private String formatMcpAgentDescription(ClientNodeResponse client, ClientControllableAgentResponse agent) {
        String prefix = "- %s | %s | 项目：%s | 客户端：%s | MCP状态：%s | 工作目录：%s".formatted(
                agent.getAgentCode(),
                defaultText(agent.getAgentName(), "-"),
                defaultText(agent.getProjectCode(), "-"),
                defaultText(client.getClientName(), client.getClientCode()),
                Boolean.TRUE.equals(client.getMcpEnabled()) ? "已开启" : "已关闭",
                defaultText(agent.getWorkspaceDir(), "-")
        );
        McpTransportProtocol protocol = parseMcpTransportProtocol(agent.getMcpTransportProtocol());
        if (protocol == McpTransportProtocol.NONE) {
            return prefix + " | MCP传输：不注入，会话内不要自动追加 MCP 配置，按本地已有配置使用";
        }
        if (protocol == McpTransportProtocol.REST) {
            return prefix + " | MCP传输：REST | 工具列表：" + mcpToolsEndpoint(client, agent.getAgentCode())
                    + " | 文件调用：" + mcpFilesEndpoint(client, agent.getAgentCode());
        }
        return prefix + " | MCP传输：SSE | 会话配置：" + mcpSseConfig(agent.getAgentCode(), mcpSseEndpoint(client, agent.getAgentCode()));
    }

    private McpTransportProtocol parseMcpTransportProtocol(String value) {
        String normalized = defaultText(trimToNull(value), McpTransportProtocol.SSE.name()).toUpperCase();
        try {
            return McpTransportProtocol.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return McpTransportProtocol.SSE;
        }
    }

    private String mcpToolsEndpoint(ClientNodeResponse client, String agentCode) {
        return normalizedMcpBase(client) + "/agents/" + agentCode + "/tools";
    }

    private String mcpFilesEndpoint(ClientNodeResponse client, String agentCode) {
        return normalizedMcpBase(client) + "/agents/" + agentCode + "/files";
    }

    private String mcpSseEndpoint(ClientNodeResponse client, String agentCode) {
        return normalizedMcpBase(client) + "/agents/" + agentCode + "/sse";
    }

    private String normalizedMcpBase(ClientNodeResponse client) {
        String base = defaultText(trimToNull(client.getMcpServerUrl()), "/api/mcp");
        while (base.endsWith("/") && base.length() > 1) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String mcpSseConfig(String agentCode, String url) {
        return "{\"mcpServers\":{\"%s\":{\"type\":\"sse\",\"url\":\"%s\"}}}".formatted(agentCode, url);
    }

    private ClientAgentSessionResponse toSessionResponse(ClientAgentSessionRuntime item) {
        return ClientAgentSessionResponse.builder()
                .requestId(item.requestId())
                .sessionId(item.sessionId())
                .sessionCode(item.sessionId())
                .clientCode(item.clientCode())
                .agentCode(item.agentCode())
                .sessionName(item.sessionName())
                .sessionType(item.sessionType())
                .runtimeType(item.runtimeType())
                .defaultFlag(item.defaultFlag())
                .status(item.status())
                .workspaceDir(item.workspaceDir())
                .errorMessage(item.errorMessage())
                .initializedSkills(item.initializedSkills())
                .events(toSessionEventResponses(item))
                .createdAt(item.createdAt())
                .updatedAt(item.updatedAt())
                .build();
    }

    private List<ClientSessionEventResponse> toSessionEventResponses(ClientAgentSessionRuntime item) {
        return sessionEvents.getOrDefault(sessionEventKey(item), List.of()).stream()
                .sorted(Comparator.comparing(ClientSessionEventRuntime::createdAt))
                .map(event -> ClientSessionEventResponse.builder()
                        .eventId(event.eventId())
                        .direction(event.direction())
                        .eventType(event.eventType())
                        .payload(event.payload())
                        .createdAt(event.createdAt())
                        .build())
                .toList();
    }

    private ClientCommandResponse toCommandResponse(ClientCommandRuntime item) {
        return ClientCommandResponse.builder()
                .commandId(item.commandId())
                .clientCode(item.clientCode())
                .agentCode(item.agentCode())
                .sessionId(item.sessionId())
                .sessionCode(item.sessionId())
                .requirementNo(item.requirementNo())
                .linkId(item.linkId())
                .title(item.title())
                .prompt(item.prompt())
                .status(item.status())
                .resultSummary(item.resultSummary())
                .executionDetails(item.executionDetails())
                .deliverablePath(item.deliverablePath())
                .createdAt(item.createdAt())
                .startedAt(item.startedAt())
                .finishedAt(item.finishedAt())
                .updatedAt(item.updatedAt())
                .build();
    }

    private String normalizeSessionStatus(String status, String sessionId) {
        String normalized = defaultText(trimToNull(status), sessionId == null ? SESSION_FAILED : SESSION_READY).toUpperCase();
        if (!SESSION_READY.equals(normalized) && !SESSION_FAILED.equals(normalized)) {
            throw new BizException(400, "unsupported session status");
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

    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String websocketPayload(String type, Object data) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("data", data);
        return toPrettyJson(payload);
    }

    private String incomingHttpPayload(String method, String path, Object body) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", method);
        payload.put("path", path);
        payload.put("body", body);
        return toPrettyJson(payload);
    }

    private String toPrettyJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ignored) {
            return String.valueOf(value);
        }
    }

    private void appendCommandEvent(ClientCommandRuntime command, String direction, String eventType, String payload) {
        if (command.sessionId() == null) {
            return;
        }
        sessions.values().stream()
                .filter(item -> item.clientCode().equals(command.clientCode()))
                .filter(item -> command.sessionId().equals(item.sessionId()))
                .findFirst()
                .ifPresent(session -> appendSessionEvent(session, direction, eventType, payload));
    }

    private void appendSessionEvent(ClientAgentSessionRuntime session, String direction, String eventType, String payload) {
        String key = sessionEventKey(session);
        List<ClientSessionEventRuntime> events = sessionEvents.computeIfAbsent(key, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>());
        events.add(new ClientSessionEventRuntime(
                "EVT-" + UUID.randomUUID(),
                direction,
                eventType,
                payload,
                LocalDateTime.now()
        ));
        if (events.size() > 200) {
            events.remove(0);
        }
    }

    private String sessionEventKey(ClientAgentSessionRuntime session) {
        return session.clientCode() + ":" + session.requestId();
    }

    private record ClientAgentSessionRuntime(
            String requestId,
            String sessionId,
            String clientCode,
            String agentCode,
            String sessionName,
            String sessionType,
            String runtimeType,
            boolean defaultFlag,
            String status,
            String workspaceDir,
            String errorMessage,
            List<Object> initializedSkills,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        ClientAgentSessionRuntime withCompletion(String nextSessionId,
                                                 String nextRuntimeType,
                                                 String nextStatus,
                                                 String nextErrorMessage,
                                                 List<Object> nextInitializedSkills,
                                                 LocalDateTime now) {
            return new ClientAgentSessionRuntime(requestId, nextSessionId, clientCode, agentCode, sessionName, sessionType, nextRuntimeType, defaultFlag, nextStatus, workspaceDir, nextErrorMessage, nextInitializedSkills, createdAt, now);
        }

        ClientAgentSessionRuntime withDefault(LocalDateTime now) {
            return new ClientAgentSessionRuntime(requestId, sessionId, clientCode, agentCode, sessionName, sessionType, runtimeType, true, status, workspaceDir, errorMessage, initializedSkills, createdAt, now);
        }

        ClientAgentSessionRuntime withoutDefault(LocalDateTime now) {
            return new ClientAgentSessionRuntime(requestId, sessionId, clientCode, agentCode, sessionName, sessionType, runtimeType, false, status, workspaceDir, errorMessage, initializedSkills, createdAt, now);
        }

        ClientAgentSessionRuntime withCreateRetry(String nextSessionName,
                                                  String nextSessionType,
                                                  String nextRuntimeType,
                                                  String nextWorkspaceDir,
                                                  List<Object> nextInitializedSkills,
                                                  LocalDateTime now) {
            return new ClientAgentSessionRuntime(requestId, null, clientCode, agentCode, nextSessionName, nextSessionType, nextRuntimeType, true, SESSION_CREATING, nextWorkspaceDir, null, nextInitializedSkills, createdAt, now);
        }
    }

    private record ClientCommandRuntime(
            String commandId,
            String clientCode,
            String agentCode,
            String sessionId,
            String requirementNo,
            Long linkId,
            String title,
            String prompt,
            DispatchStatus status,
            String resultSummary,
            String executionDetails,
            String deliverablePath,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime updatedAt
    ) {
        ClientCommandRuntime withStatus(DispatchStatus nextStatus, LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime updatedAt) {
            return new ClientCommandRuntime(commandId, clientCode, agentCode, sessionId, requirementNo, linkId, title, prompt, nextStatus, resultSummary, executionDetails, deliverablePath, createdAt, startedAt, finishedAt, updatedAt);
        }

        ClientCommandRuntime withResult(DispatchStatus nextStatus, String resultSummary, String executionDetails, String deliverablePath, LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime updatedAt) {
            return new ClientCommandRuntime(commandId, clientCode, agentCode, sessionId, requirementNo, linkId, title, prompt, nextStatus, resultSummary, executionDetails, deliverablePath, createdAt, startedAt, finishedAt, updatedAt);
        }
    }

    private record ClientSessionEventRuntime(
            String eventId,
            String direction,
            String eventType,
            String payload,
            LocalDateTime createdAt
    ) {
    }
}
