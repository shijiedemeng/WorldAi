package com.aiapi.common.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class ConsolePushWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    public void broadcast(String type, Object data) {
        String payload = toJson(type, data);
        sessions.forEach((id, session) -> {
            if (!session.isOpen()) {
                sessions.remove(id, session);
                return;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException ignored) {
                sessions.remove(id, session);
            }
        });
    }

    private String toJson(String type, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "PUSH-" + UUID.randomUUID());
        payload.put("type", type);
        payload.put("data", data);
        payload.put("createdAt", LocalDateTime.now());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ignored) {
            return "{\"type\":\"" + type + "\"}";
        }
    }
}
