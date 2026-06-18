package com.unkittered.api.stories;

import java.time.Instant;
import java.util.List;

/** Wire payloads for Stories. Field names match the Flutter {@code Story.fromJson} contract. */
public final class StoryDtos {

    private StoryDtos() { }

    /** One frame within a story. */
    public record StoryItemDto(
            String id,
            String imageUrl,
            String text,
            Instant createdAt) {
    }

    /** All of one user's live frames, plus the ring metadata. */
    public record StoryDto(
            String userId,
            String name,
            String avatar,
            String avatarImageUrl,
            List<StoryItemDto> items,
            boolean seen,
            boolean isMine) {
    }

    /** Text-only story creation payload. */
    public record CreateStoryRequest(String text) {
    }
}
