package com.aiapi.config;

import com.aiapi.client.websocket.ClientDispatchWebSocketHandler;
import com.aiapi.common.websocket.ConsolePushWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClientDispatchWebSocketHandler clientDispatchWebSocketHandler;
    private final ConsolePushWebSocketHandler consolePushWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(clientDispatchWebSocketHandler, "/ws/clients")
                .setAllowedOrigins("*");
        registry.addHandler(consolePushWebSocketHandler, "/ws/console")
                .setAllowedOrigins("*");
    }
}
