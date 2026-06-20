package com.unkittered.api.meetup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Wire payloads for Meetups. Field names match the Flutter {@code Meetup.fromJson}. */
public final class MeetupDtos {

    private MeetupDtos() { }

    public record MeetupDto(
            String id,
            String title,
            String category,
            String hostName,
            String hostAvatar,
            String area,
            String venue,
            Instant startsAt,
            int capacity,
            int attendeeCount,
            List<String> attendeeAvatars,
            String description,
            boolean joined,
            boolean hosting) {
    }

    /** Create payload. Extra client fields (hostName, joined, …) are ignored. */
    public record CreateMeetupRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank String category,
            @NotBlank @Size(max = 60) String area,
            String venue,
            Instant startsAt,
            int capacity,
            @Size(max = 1000) String description) {
    }
}
