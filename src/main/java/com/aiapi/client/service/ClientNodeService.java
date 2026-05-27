package com.aiapi.client.service;

import com.aiapi.agent.entity.AgentInfo;
import com.aiapi.agent.repository.AgentInfoRepository;
import com.aiapi.client.event.ClientNodeDisconnectedEvent;
import com.aiapi.client.event.ClientNodeOnlineEvent;
import com.aiapi.client.dto.ClientAgentRuntimeRequest;
import com.aiapi.client.dto.ClientAgentRuntimeResponse;
import com.aiapi.client.dto.ClientControllableAgentRequest;
import com.aiapi.client.dto.ClientControllableAgentResponse;
import com.aiapi.client.dto.ClientNodeResponse;
import com.aiapi.client.dto.ClientUnlinkedAgentResponse;
import com.aiapi.client.dto.RegisterClientNodeRequest;
import com.aiapi.common.enums.ClientNodeStatus;
import com.aiapi.common.enums.ConnectionProtocol;
import com.aiapi.common.exception.BizException;
import com.aiapi.common.websocket.ConsolePushWebSocketHandler;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientNodeService {

    private static final Duration ONLINE_HEARTBEAT_TTL = Duration.ofSeconds(90);
    private static final Duration OFFLINE_RETENTION = Duration.ofMinutes(5);

    private final AgentInfoRepository agentInfoRepository;
    private final ConsolePushWebSocketHandler consolePushWebSocketHandler;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, OnlineClientNode> onlineClients = new ConcurrentHashMap<>();

    public List<ClientNodeResponse> list() {
        cleanupExpiredClients();
        return onlineClients.values().stream()
                .sorted(Comparator.comparing(OnlineClientNode::lastHeartbeatTime).reversed())
                .map(this::toResponse)
                .toList();
    }

    public ClientNodeResponse get(String clientCode) {
        cleanupExpiredClients();
        OnlineClientNode node = onlineClients.get(clientCode);
        if (node == null) {
            throw new BizException(404, "client node not online");
        }
        return toResponse(node);
    }

    public ClientNodeResponse register(RegisterClientNodeRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String clientCode = request.getClientCode().trim();
        AgentLinkResolution agentLinkResolution = resolveControllableAgents(request.getAgents(), now);
        OnlineClientNode existing = onlineClients.get(clientCode);
        OnlineClientNode node = new OnlineClientNode(
                clientCode,
                request.getClientName().trim(),
                request.getOsType(),
                trimToNull(request.getHostName()),
                trimToNull(request.getIpAddress()),
                request.getConnectionProtocol() == null ? ConnectionProtocol.WEBSOCKET : request.getConnectionProtocol(),
                trimToNull(request.getConnectionSessionId()),
                ClientNodeStatus.ONLINE,
                now,
                trimToNull(request.getSupportedAgentTypes()),
                trimToNull(request.getAppVersion()),
                Boolean.TRUE.equals(request.getMcpEnabled()),
                trimToNull(request.getMcpServerUrl()),
                toRuntimeResponses(request.getRuntimes(), now),
                agentLinkResolution.linkedAgents(),
                agentLinkResolution.unlinkedAgents(),
                existing == null ? now : existing.registeredAt(),
                now,
                null
        );
        onlineClients.put(clientCode, node);
        broadcastClientStatus(node);
        eventPublisher.publishEvent(new ClientNodeOnlineEvent(clientCode));
        return toResponse(node);
    }

    public ClientNodeResponse heartbeat(String clientCode) {
        cleanupExpiredClients();
        OnlineClientNode existing = onlineClients.get(clientCode);
        if (existing == null) {
            throw new BizException(404, "client node not online");
        }
        OnlineClientNode updated = existing.withHeartbeat(LocalDateTime.now());
        onlineClients.put(clientCode, updated);
        if (existing.status() != ClientNodeStatus.ONLINE) {
            broadcastClientStatus(updated);
            eventPublisher.publishEvent(new ClientNodeOnlineEvent(clientCode));
        }
        return toResponse(updated);
    }

    public void offline(String clientCode) {
        OnlineClientNode existing = onlineClients.get(clientCode);
        if (existing == null) {
            return;
        }
        OnlineClientNode updated = existing.withOffline(LocalDateTime.now());
        onlineClients.put(clientCode, updated);
        if (existing.status() != ClientNodeStatus.OFFLINE) {
            broadcastClientStatus(updated);
        }
    }

    public boolean exists(String clientCode) {
        cleanupExpiredClients();
        return onlineClients.containsKey(clientCode);
    }

    public boolean isOnline(String clientCode) {
        cleanupExpiredClients();
        OnlineClientNode node = onlineClients.get(clientCode);
        return node != null && node.status() == ClientNodeStatus.ONLINE;
    }

    public ClientNodeStatus statusOf(String clientCode) {
        cleanupExpiredClients();
        OnlineClientNode node = onlineClients.get(clientCode);
        return node == null ? null : node.status();
    }

    private List<ClientAgentRuntimeResponse> toRuntimeResponses(List<ClientAgentRuntimeRequest> runtimes, LocalDateTime now) {
        if (runtimes == null || runtimes.isEmpty()) {
            return List.of();
        }
        return runtimes.stream()
                .map(item -> ClientAgentRuntimeResponse.builder()
                        .id(null)
                        .agentType(item.getAgentType())
                        .agentName(item.getAgentName().trim())
                        .agentVersion(trimToNull(item.getAgentVersion()))
                        .commandPath(trimToNull(item.getCommandPath()))
                        .availableFlag(item.getAvailableFlag() == null || item.getAvailableFlag())
                        .capabilityTags(trimToNull(item.getCapabilityTags()))
                        .supportsSessionReuse(Boolean.TRUE.equals(item.getSupportsSessionReuse()))
                        .defaultWorkspaceDir(trimToNull(item.getDefaultWorkspaceDir()))
                        .lastProbeTime(now)
                        .build())
                .toList();
    }

    private AgentLinkResolution resolveControllableAgents(List<ClientControllableAgentRequest> agents, LocalDateTime now) {
        if (agents == null || agents.isEmpty()) {
            return new AgentLinkResolution(List.of(), List.of());
        }
        List<String> requestedAgentCodes = agents.stream()
                .map(ClientControllableAgentRequest::getAgentCode)
                .map(this::trimToNull)
                .filter(item -> item != null)
                .distinct()
                .toList();
        if (requestedAgentCodes.isEmpty()) {
            return new AgentLinkResolution(List.of(), List.of());
        }
        Map<String, AgentInfo> agentMap = agentInfoRepository.findByAgentCodeIn(requestedAgentCodes).stream()
                .collect(Collectors.toMap(AgentInfo::getAgentCode, Function.identity(), (left, right) -> left));
        Set<String> existingAgentCodes = agentMap.keySet();
        List<ClientControllableAgentResponse> linkedAgents = new ArrayList<>();
        List<ClientUnlinkedAgentResponse> unlinkedAgents = new ArrayList<>();
        for (ClientControllableAgentRequest request : agents) {
            String agentCode = trimToNull(request.getAgentCode());
            if (agentCode == null) {
                continue;
            }
            if (!existingAgentCodes.contains(agentCode)) {
                unlinkedAgents.add(ClientUnlinkedAgentResponse.builder()
                        .agentCode(agentCode)
                        .enabledFlag(request.getEnabledFlag() == null || request.getEnabledFlag())
                        .skillsDir(trimToNull(request.getSkillsDir()))
                        .workspaceDir(trimToNull(request.getWorkspaceDir()))
                        .workerCommand(trimToNull(request.getWorkerCommand()))
                        .reason("Agent 管理中不存在该编号，未关联")
                        .lastSeenTime(now)
                        .build());
                continue;
            }
            AgentInfo agent = agentMap.get(agentCode);
            linkedAgents.add(ClientControllableAgentResponse.builder()
                    .id(null)
                    .agentCode(agentCode)
                    .agentName(agent.getAgentName())
                    .projectCode(agent.getProjectCode())
                    .agentEngineType(agent.getAgentEngineType() == null ? null : agent.getAgentEngineType().name())
                    .agentRole(agent.getAgentRole().name())
                    .mcpTransportProtocol(agent.getMcpTransportProtocol() == null ? "SSE" : agent.getMcpTransportProtocol().name())
                    .status(agent.getStatus().name())
                    .enabledFlag(request.getEnabledFlag() == null || request.getEnabledFlag())
                    .skillCodes(splitSkillCodes(agent.getSkillCodes()))
                    .skillsDir(trimToNull(request.getSkillsDir()))
                    .workspaceDir(trimToNull(request.getWorkspaceDir()))
                    .workerCommand(trimToNull(request.getWorkerCommand()))
                    .lastSeenTime(now)
                    .build());
        }
        return new AgentLinkResolution(linkedAgents, unlinkedAgents);
    }

    private ClientNodeResponse toResponse(OnlineClientNode node) {
        return ClientNodeResponse.builder()
                .id(null)
                .clientCode(node.clientCode())
                .clientName(node.clientName())
                .osType(node.osType())
                .hostName(node.hostName())
                .ipAddress(node.ipAddress())
                .connectionProtocol(node.connectionProtocol())
                .connectionSessionId(node.connectionSessionId())
                .status(node.status())
                .lastHeartbeatTime(node.lastHeartbeatTime())
                .supportedAgentTypes(node.supportedAgentTypes())
                .appVersion(node.appVersion())
                .mcpEnabled(node.mcpEnabled())
                .mcpServerUrl(node.mcpServerUrl())
                .runtimes(node.runtimes())
                .agents(node.agents())
                .unlinkedAgents(node.unlinkedAgents())
                .createdAt(node.registeredAt())
                .updatedAt(node.updatedAt())
                .build();
    }

    private void cleanupExpiredClients() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleOnlineCutoff = now.minus(ONLINE_HEARTBEAT_TTL);
        LocalDateTime removeOfflineCutoff = now.minus(OFFLINE_RETENTION);
        onlineClients.forEach((clientCode, node) -> {
            if (node.status() == ClientNodeStatus.ONLINE && node.lastHeartbeatTime().isBefore(staleOnlineCutoff)) {
                OnlineClientNode offline = node.withOffline(now);
                onlineClients.put(clientCode, offline);
                broadcastClientStatus(offline);
                eventPublisher.publishEvent(new ClientNodeDisconnectedEvent(clientCode));
                return;
            }
            if (node.status() == ClientNodeStatus.OFFLINE
                    && node.offlineAt() != null
                    && node.offlineAt().isBefore(removeOfflineCutoff)) {
                onlineClients.remove(clientCode, node);
            }
        });
    }

    private void broadcastClientStatus(OnlineClientNode node) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clientCode", node.clientCode());
        data.put("clientName", node.clientName());
        data.put("status", node.status());
        data.put("lastHeartbeatTime", node.lastHeartbeatTime());
        data.put("updatedAt", node.updatedAt());
        consolePushWebSocketHandler.broadcast("CLIENT_STATUS_CHANGED", data);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> splitSkillCodes(String skillCodes) {
        if (skillCodes == null || skillCodes.isBlank()) {
            return List.of();
        }
        return List.of(skillCodes.split(",")).stream()
                .map(this::trimToNull)
                .filter(item -> item != null)
                .distinct()
                .toList();
    }

    private record OnlineClientNode(
            String clientCode,
            String clientName,
            com.aiapi.common.enums.ClientOsType osType,
            String hostName,
            String ipAddress,
            ConnectionProtocol connectionProtocol,
            String connectionSessionId,
            ClientNodeStatus status,
            LocalDateTime lastHeartbeatTime,
            String supportedAgentTypes,
            String appVersion,
            Boolean mcpEnabled,
            String mcpServerUrl,
            List<ClientAgentRuntimeResponse> runtimes,
            List<ClientControllableAgentResponse> agents,
            List<ClientUnlinkedAgentResponse> unlinkedAgents,
            LocalDateTime registeredAt,
            LocalDateTime updatedAt,
            LocalDateTime offlineAt
    ) {
        OnlineClientNode withHeartbeat(LocalDateTime heartbeatTime) {
            return new OnlineClientNode(
                    clientCode,
                    clientName,
                    osType,
                    hostName,
                    ipAddress,
                    connectionProtocol,
                    connectionSessionId,
                    ClientNodeStatus.ONLINE,
                    heartbeatTime,
                    supportedAgentTypes,
                    appVersion,
                    mcpEnabled,
                    mcpServerUrl,
                    runtimes,
                    agents,
                    unlinkedAgents,
                    registeredAt,
                    heartbeatTime,
                    null
            );
        }

        OnlineClientNode withOffline(LocalDateTime now) {
            return new OnlineClientNode(
                    clientCode,
                    clientName,
                    osType,
                    hostName,
                    ipAddress,
                    connectionProtocol,
                    connectionSessionId,
                    ClientNodeStatus.OFFLINE,
                    lastHeartbeatTime,
                    supportedAgentTypes,
                    appVersion,
                    mcpEnabled,
                    mcpServerUrl,
                    runtimes,
                    agents,
                    unlinkedAgents,
                    registeredAt,
                    now,
                    now
            );
        }
    }

    private record AgentLinkResolution(
            List<ClientControllableAgentResponse> linkedAgents,
            List<ClientUnlinkedAgentResponse> unlinkedAgents
    ) {
    }
}
