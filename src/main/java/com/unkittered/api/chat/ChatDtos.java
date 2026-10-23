package com.unkittered.api.chat;

import com.unkittered.api.profile.ProfileDto;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/** Wire payloads for matches and conversations. */
public final class ChatDtos {

    private ChatDtos() { }

    /** A mutual match. {@code id} is the match/conversation id. */
    public record MatchDto(
            String id,
            ProfileDto profile,
            Instant createdAt) {
    }

    /** A conversation row for the messages list: the match plus its latest activity. */
    public record ConversationDto(
            String matchId,
            ProfileDto profile,
            String lastMessage,
            Instant lastMessageAt,
            long unreadCount,
            /** Kind of the latest message, so the list can render a preview. */
            RichMessages.Kind lastMessageKind,
            /** The most recent date plan in this chat (plan payload), or null. */
            Map<String, Object> plan,
            /** "proposed" | "accepted" | "declined", or null without a plan. */
            String planStatus) {
    }

    /** A single chat message. {@code isMe} is true when the viewer is the sender. */
    public record MessageDto(
            String id,
            boolean isMe,
            String text,
            Instant createdAt,
            boolean read,
            RichMessages.Kind kind,
            Map<String, Object> payload) {

        public static MessageDto from(Message m, java.util.UUID viewerId) {
            return new MessageDto(
                    m.getId().toString(),
                    m.getSenderId().equals(viewerId),
                    m.getBody(),
                    m.getCreatedAt(),
                    m.getReadAt() != null,
                    RichMessages.Kind.fromWire(m.getKind()),
                    m.getPayload());
        }
    }

    /**
     * Send a message. Plain text needs only {@code text}; other kinds put their
     * fields in {@code payload} (see {@link RichMessages}).
     */
    public record SendMessageRequest(
            @Size(max = 4000) String text,
            RichMessages.Kind kind,
            Map<String, Object> payload) {
        public RichMessages.Kind kindOrText() { return kind == null ? RichMessages.Kind.TEXT : kind; }
    }
}
