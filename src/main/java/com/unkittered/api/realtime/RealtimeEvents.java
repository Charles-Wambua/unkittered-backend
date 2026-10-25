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
