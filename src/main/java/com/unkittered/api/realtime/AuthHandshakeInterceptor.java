package com.unkittered.api.realtime;

import com.unkittered.api.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Authenticates the WebSocket handshake from a {@code ?token=<jwt>} query
 * parameter (browsers can't set Authorization headers on a WS upgrade). On
 * success the user id is stashed in the session attributes for the handler.
 */
@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    static final String USER_ID = "userId";

    private final JwtService jwt;

    public AuthHandshakeInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = queryParam(request.getURI().getQuery(), "token");
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            UUID userId = jwt.parseUserId(token);
            attributes.put(USER_ID, userId);
            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private static String queryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return java.net.URLDecoder.decode(
                        pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
