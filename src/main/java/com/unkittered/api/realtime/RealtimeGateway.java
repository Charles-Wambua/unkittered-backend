package com.unkittered.api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live WebSocket sessions per user and pushes JSON events to them.
 * A user may have several sessions (multiple devices/tabs). Outbound writes are
 * wrapped in a {@link ConcurrentWebSocketSessionDecorator} so concurrent sends
 * (e.g. a message and a match event at once) are serialised safely.
 */
@Component
public class RealtimeGateway extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeGateway.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT = 512 * 1024;

    private final ObjectMapper mapper;
    private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public RealtimeGateway(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = userId(session);
        if (userId == null) {
            close(session);
            return;
        }
        WebSocketSession safe =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(safe);
        log.debug("WS connected: user={} sessions={}", userId, sessions.get(userId).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = userId(session);
        if (userId == null) return;
        Set<WebSocketSession> set = sessions.get(userId);
        if (set != null) {
            set.removeIf(s -> sameSession(s, session));
            if (set.isEmpty()) sessions.remove(userId);
        }
    }

    /** Serialises {@code payload} to JSON and delivers it to every live session for {@code userId}. */
    public void sendToUser(UUID userId, Object payload) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) return;
        final String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialise realtime payload for {}: {}", userId, e.getMessage());
            return;
        }
        Iterator<WebSocketSession> iterator = set.iterator();
        while (iterator.hasNext()) {
            WebSocketSession s = iterator.next();
            if (!s.isOpen()) {
                iterator.remove();
                continue;
            }
            try {
                s.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                iterator.remove();
                log.debug("Dropping dead WS session for {}: {}", userId, e.getMessage());
            }
        }
        if (set.isEmpty()) {
            sessions.remove(userId, set);
        }
    }

    /** Number of distinct users with at least one live connection right now. */
    public int onlineCount() {
        return sessions.size();
    }

    /** Snapshot of the user ids currently connected (one or more devices each). */
    public Set<UUID> onlineUserIds() {
        return new java.util.HashSet<>(sessions.keySet());
    }

    /** Total open sockets across all users (a user may hold several). */
    public int connectionCount() {
        int n = 0;
        for (Set<WebSocketSession> s : sessions.values()) n += s.size();
        return n;
    }

    private static UUID userId(WebSocketSession session) {
        Object id = session.getAttributes().get(AuthHandshakeInterceptor.USER_ID);
        return id instanceof UUID u ? u : null;
    }

    private static boolean sameSession(WebSocketSession a, WebSocketSession b) {
        // Decorators wrap the raw session but keep its id.
        return a.getId().equals(b.getId());
    }

    private static void close(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
        }
    }
}
