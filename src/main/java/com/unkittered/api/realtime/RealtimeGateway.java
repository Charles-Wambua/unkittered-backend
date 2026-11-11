package com.unkittered.api.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unkittered.api.realtime.RealtimeEvents.PresencePush;
import com.unkittered.api.realtime.RealtimeEvents.TypingPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
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
 *
 * <p>Clients may send {@code {"type":"typing","matchId":"…"}}, which is relayed
 * to the other member of that conversation; anything else inbound is ignored.
 * A member's matches are told when their first session opens and their last
 * one closes ({@code presence}), unless they keep their activity private.
 */
@Component
public class RealtimeGateway extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeGateway.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT = 512 * 1024;

    /** A session's typing frames closer together than this are dropped. */
    static final long TYPING_GAP_MS = 1_000;
    private static final String LAST_TYPING = "lastTypingAt";

    private final ObjectMapper mapper;
    private final RealtimePeers peers;
    private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public RealtimeGateway(ObjectMapper mapper, RealtimePeers peers) {
        this.mapper = mapper;
        this.peers = peers;
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
        boolean[] first = {false};
        sessions.compute(userId, (k, set) -> {
            if (set == null) {
                set = ConcurrentHashMap.newKeySet();
                first[0] = true;
            }
            set.add(safe);
            return set;
        });
        log.debug("WS connected: user={} first={}", userId, first[0]);
        if (first[0]) announcePresence(userId, true);
    }

    /**
     * Inbound frames. Only {@code typing} means anything; malformed or unknown
     * frames are dropped without a word, and never cost the client its socket.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            UUID userId = userId(session);
            if (userId == null) return;
            JsonNode frame = mapper.readTree(message.getPayload());
            if (frame == null || !"typing".equals(frame.path("type").asText(null))) return;
            UUID matchId = UUID.fromString(frame.path("matchId").asText(""));
            long now = System.currentTimeMillis();
            if (!typingAllowed(session.getAttributes().get(LAST_TYPING), now)) return;
            session.getAttributes().put(LAST_TYPING, now);
            peers.otherMember(matchId, userId)
                    .ifPresent(other -> sendToUser(other, TypingPush.of(matchId, userId)));
        } catch (Exception e) {
            log.debug("Ignoring inbound WS frame: {}", e.getMessage());
        }
    }

    /** Binary frames are ignored too (the default would close the socket). */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // no-op
    }

    static boolean typingAllowed(Object lastTypingAt, long now) {
        return !(lastTypingAt instanceof Long last) || now - last >= TYPING_GAP_MS;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = userId(session);
        if (userId == null) return;
        boolean[] last = {false};
        sessions.computeIfPresent(userId, (k, set) -> {
            set.removeIf(s -> sameSession(s, session));
            if (!set.isEmpty()) return set;
            last[0] = true;
            return null;
        });
        if (last[0]) announcePresence(userId, false);
    }

    /**
     * Tells the matches of {@code userId} who are connected right now that they
     * came online or left. Best effort: a failure here must never reach the
     * socket callbacks.
     */
    private void announcePresence(UUID userId, boolean online) {
        try {
            if (sessions.size() <= (online ? 1 : 0)) return;   // nobody else is listening
            PresencePush event = PresencePush.of(userId, online);
            for (UUID other : peers.presenceAudience(userId)) {
                if (sessions.containsKey(other)) sendToUser(other, event);
            }
        } catch (Exception e) {
            log.debug("Presence for {} not announced: {}", userId, e.getMessage());
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
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            } catch (IOException | RuntimeException e) {
                log.debug("Dropping dead WS session for {}: {}", userId, e.getMessage());
            }
        }
    }

    /** Closes every live connection a user has (e.g. their account was just suspended). */
    public void disconnect(UUID userId) {
        Set<WebSocketSession> set = sessions.remove(userId);
        if (set == null) return;
        announcePresence(userId, false);
        for (WebSocketSession s : set) {
            try {
                s.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException e) {
                log.debug("Closing WS session for {} failed: {}", userId, e.getMessage());
            }
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
