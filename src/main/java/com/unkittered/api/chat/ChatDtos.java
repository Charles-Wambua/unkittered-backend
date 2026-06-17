package com.unkittered.api.chat;

import com.unkittered.api.profile.ProfileDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

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
            long unreadCount) {
    }

    /** A single chat message. {@code isMe} is true when the viewer is the sender. */
    public record MessageDto(
            String id,
            boolean isMe,
            String text,
            Instant createdAt,
            boolean read) {

        public static MessageDto from(Message m, java.util.UUID viewerId) {
            return new MessageDto(
                    m.getId().toString(),
                    m.getSenderId().equals(viewerId),
                    m.getBody(),
                    m.getCreatedAt(),
                    m.getReadAt() != null);
        }
    }

    public record SendMessageRequest(
            @NotBlank @Size(max = 4000) String text) {
    }
}
