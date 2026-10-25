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
            String planStatus,
            /** Whether the viewer sent the latest message. */
            boolean lastMessageFromMe) {
    }

    /** What a deleted message shows, for both people. */
    public static final String DELETED_TEXT = "This message was deleted";

    /**
     * The message a reply quotes: who wrote it and a one-line preview (every
     * kind has a readable body, e.g. "📷 Photo" or "📅 Walk · Karura").
     */
    public record ReplyQuote(String id, String senderId, String text, RichMessages.Kind kind) {

        static final int MAX_PREVIEW = 120;

        public static ReplyQuote of(Message original) {
            if (original == null) return null;
            String body = original.isDeleted() ? DELETED_TEXT
                    : original.getBody() == null ? "" : original.getBody();
            String preview = body.length() > MAX_PREVIEW ? body.substring(0, MAX_PREVIEW - 1) + "…" : body;
            return new ReplyQuote(original.getId().toString(), original.getSenderId().toString(), preview,
                    RichMessages.Kind.fromWire(original.getKind()));
        }
    }

    /**
     * A single chat message. {@code isMe} is true when the viewer is the sender;
     * {@code replyTo} quotes the message it answers, if any.
     */
    public record MessageDto(
            String id,
            boolean isMe,
            String text,
            Instant createdAt,
            boolean read,
            RichMessages.Kind kind,
            Map<String, Object> payload,
            ReplyQuote replyTo,
            /** Deleted by its sender: shown as "This message was deleted". */
            boolean deleted) {

        public static MessageDto from(Message m, java.util.UUID viewerId, Message repliedTo) {
            return new MessageDto(
                    m.getId().toString(),
                    m.getSenderId().equals(viewerId),
                    m.isDeleted() ? DELETED_TEXT : m.getBody(),
                    m.getCreatedAt(),
                    m.getReadAt() != null,
                    RichMessages.Kind.fromWire(m.getKind()),
                    m.getPayload(),
                    m.isDeleted() ? null : ReplyQuote.of(repliedTo),
                    m.isDeleted());
        }
    }

    /**
     * Send a message. Plain text needs only {@code text}; other kinds put their
     * fields in {@code payload} (see {@link RichMessages}).
     */
    public record SendMessageRequest(
            @Size(max = 4000) String text,
            RichMessages.Kind kind,
            Map<String, Object> payload,
            /** Optional: the id of the message in this chat being replied to. */
            String replyToId) {
        public RichMessages.Kind kindOrText() { return kind == null ? RichMessages.Kind.TEXT : kind; }
    }
}
