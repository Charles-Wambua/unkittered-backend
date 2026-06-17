package com.unkittered.api.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the chat WebSocket endpoint at {@code /ws/chat}. */
@Configuration
@EnableWebSocket
public class RealtimeConfig implements WebSocketConfigurer {

    private final RealtimeGateway gateway;
    private final AuthHandshakeInterceptor authInterceptor;

    public RealtimeConfig(RealtimeGateway gateway, AuthHandshakeInterceptor authInterceptor) {
        this.gateway = gateway;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gateway, "/ws/chat")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
