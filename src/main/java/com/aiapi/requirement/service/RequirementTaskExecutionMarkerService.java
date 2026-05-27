package com.aiapi.requirement.service;

import com.aiapi.common.websocket.ConsolePushWebSocketHandler;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RequirementTaskExecutionMarkerService {

    private final ConsolePushWebSocketHandler consolePushWebSocketHandler;
    private final Map<String, RequirementTaskExecutionMarker> markers = new ConcurrentHashMap<>();

    public RequirementTaskExecutionMarker markRunning(String requirementNo,
                                                      String rootRequirementNo,
                                                      String commandId,
                                                      String clientCode,
                                                      String agentCode,
                                                      String sessionId,
                                                      boolean acpTask) {
        RequirementTaskExecutionMarker marker = new RequirementTaskExecutionMarker(
                "TASK-" + commandId,
                requirementNo,
                rootRequirementNo,
                commandId,
                clientCode,
                agentCode,
                sessionId,
                acpTask,
                LocalDateTime.now()
        );
        markers.put(requirementNo, marker);
        broadcast("EXECUTION_MARKED", marker);
        return marker;
    }

    public RequirementTaskExecutionMarker get(String requirementNo) {
        return markers.get(requirementNo);
    }

    public boolean isRunning(String requirementNo) {
        return markers.containsKey(requirementNo);
    }

    public void clear(String requirementNo, String commandId) {
        RequirementTaskExecutionMarker marker = markers.get(requirementNo);
        if (marker == null) {
            return;
        }
        if (commandId != null && !commandId.equals(marker.commandId())) {
            return;
        }
        if (markers.remove(requirementNo, marker)) {
            broadcast("EXECUTION_CLEARED", marker);
        }
    }

    public void clearByClient(String clientCode) {
        markers.values().stream()
                .filter(marker -> marker.clientCode().equals(clientCode))
                .toList()
                .forEach(marker -> clear(marker.requirementNo(), marker.commandId()));
    }

    private void broadcast(String action, RequirementTaskExecutionMarker marker) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        data.put("requirementNo", marker.requirementNo());
        data.put("rootRequirementNo", marker.rootRequirementNo());
        data.put("executionMarker", marker.markerId());
        data.put("commandId", marker.commandId());
        data.put("clientCode", marker.clientCode());
        data.put("agentCode", marker.agentCode());
        data.put("sessionId", marker.sessionId());
        data.put("acpTask", marker.acpTask());
        data.put("startedAt", marker.startedAt());
        consolePushWebSocketHandler.broadcast("REQUIREMENT_TASK_CHANGED", data);
    }

    public record RequirementTaskExecutionMarker(
            String markerId,
            String requirementNo,
            String rootRequirementNo,
            String commandId,
            String clientCode,
            String agentCode,
            String sessionId,
            boolean acpTask,
            LocalDateTime startedAt
    ) {
    }
}
