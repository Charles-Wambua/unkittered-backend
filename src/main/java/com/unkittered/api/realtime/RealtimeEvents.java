package com.unkittered.api.realtime;

import com.unkittered.api.chat.ChatDtos.ReplyQuote;
import com.unkittered.api.chat.Message;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** JSON payloads pushed to clients over the WebSocket. */
public final class RealtimeEvents {

    private RealtimeEvents() { }

    /**
     * A new message in a conversation. {@code senderId} lets the client decide
     * whether it sent the message (so it can de-dupe its own optimistic bubble).
     */
    public record MessagePush(
            String type,
            String matchId,
            String id,
            String senderId,
            String text,
            Instant createdAt,
            String kind,
            Map<String, Object> payload,
            ReplyQuote replyTo) {

        public static MessagePush of(Message m, Message repliedTo) {
            return new MessagePush(
                    "message",
                    m.getMatchId().toString(),
                    m.getId().toString(),
                    m.getSenderId().toString(),
                    m.getBody(),
                    m.getCreatedAt(),
                    m.getKind(),
                    m.getPayload(),
                    ReplyQuote.of(repliedTo));
        }
    }

    /** The other person read everything sent to them in this conversation up to {@code readAt}. */
    public record ReadPush(String type, String matchId, Instant readAt) {

        public static ReadPush of(UUID matchId, Instant readAt) {
            return new ReadPush("read", matchId.toString(), readAt);
        }
    }

    /** Someone is typing in a conversation (relayed from their socket; nothing is stored). */
    public record TypingPush(String type, String matchId, String userId) {

        public static TypingPush of(UUID matchId, UUID userId) {
            return new TypingPush("typing", matchId.toString(), userId.toString());
        }
    }

    /** A match came online or went offline (only sent for members who show their activity). */
    public record PresencePush(String type, String userId, boolean online) {

        public static PresencePush of(UUID userId, boolean online) {
            return new PresencePush("presence", userId.toString(), online);
        }
    }

    /** A new mutual match. The client refetches /matches & /conversations. */
    public record MatchPush(
            String type,
            String matchId,
            String otherUserId) {

        public static MatchPush of(UUID matchId, UUID otherUserId) {
            return new MatchPush("match", matchId.toString(), otherUserId.toString());
        }
    }
}
