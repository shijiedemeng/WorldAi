package com.aiapi.client.websocket;

import com.aiapi.client.event.ClientNodeDisconnectedEvent;
import com.aiapi.client.dto.ClientAgentSessionResponse;
import com.aiapi.client.dto.ClientCommandResponse;
import com.aiapi.client.service.ClientNodeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class ClientDispatchWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ClientNodeService clientNodeService;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionClientCodes = new ConcurrentHashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode payload = objectMapper.readTree(message.getPayload());
        if ("REGISTER".equals(payload.path("type").asText())) {
            String clientCode = payload.path("clientCode").asText("");
            if (!clientCode.isBlank()) {
                clientSessions.put(clientCode, session);
                sessionClientCodes.put(session.getId(), clientCode);
                heartbeat(clientCode);
            }
            return;
        }
        if ("HEARTBEAT".equals(payload.path("type").asText())) {
            String clientCode = sessionClientCodes.get(session.getId());
            if (clientCode != null) {
                heartbeat(clientCode);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String clientCode = sessionClientCodes.remove(session.getId());
        if (clientCode != null) {
            if (clientSessions.remove(clientCode, session)) {
                clientNodeService.offline(clientCode);
                eventPublisher.publishEvent(new ClientNodeDisconnectedEvent(clientCode));
            }
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void refreshConnectedClientHeartbeats() {
        clientSessions.forEach((clientCode, session) -> {
            if (session.isOpen()) {
                heartbeat(clientCode);
                return;
            }
            if (clientSessions.remove(clientCode, session)) {
                sessionClientCodes.remove(session.getId());
                clientNodeService.offline(clientCode);
                eventPublisher.publishEvent(new ClientNodeDisconnectedEvent(clientCode));
            }
        });
    }

    public void sendSessionCreate(String clientCode, ClientAgentSessionResponse sessionCreateRequest) {
        WebSocketSession session = clientSessions.get(clientCode);
        if (session == null || !session.isOpen()) {
            return;
        }
        sendPayload(clientCode, session, Map.<String, Object>of(
                "type", "CREATE_SESSION",
                "data", sessionCreateRequest
        ));
    }

    public void sendCommand(String clientCode, ClientCommandResponse command) {
        WebSocketSession session = clientSessions.get(clientCode);
        if (session == null || !session.isOpen()) {
            return;
        }
        sendPayload(clientCode, session, Map.<String, Object>of(
                "type", "EXECUTE_COMMAND",
                "data", command
        ));
    }

    public boolean sendMcpFileRequest(String clientCode, Object request) {
        WebSocketSession session = clientSessions.get(clientCode);
        if (session == null || !session.isOpen()) {
            return false;
        }
        return sendPayload(clientCode, session, Map.<String, Object>of(
                "type", "MCP_FILE_REQUEST",
                "data", request
        ));
    }

    private boolean sendPayload(String clientCode, WebSocketSession session, Map<String, Object> payload) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            return true;
        } catch (IOException ignored) {
            clientSessions.remove(clientCode, session);
            clientNodeService.offline(clientCode);
            eventPublisher.publishEvent(new ClientNodeDisconnectedEvent(clientCode));
            return false;
        }
    }

    private void heartbeat(String clientCode) {
        try {
            clientNodeService.heartbeat(clientCode);
        } catch (RuntimeException ignored) {
            // The HTTP registration is the source of client metadata; a WS heartbeat before registration is ignored.
        }
    }
}
