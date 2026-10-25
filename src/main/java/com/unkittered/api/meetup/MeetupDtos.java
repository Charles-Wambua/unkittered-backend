package com.unkittered.api.meetup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
            boolean hosting,
            MeetupEnums.Kind kind,
            MeetupEnums.PetPolicy petPolicy,
            int durationMinutes,
            /** Attendees bringing a pet. */
            long petCount,
            /** Whether the viewer RSVP'd with their pet. */
            boolean bringingPet,
            Instant endsAt,
            /** upcoming | live | ended. RSVPs close once it starts. */
            String status,
            /** Pack chat for members: locked | open | closed (read-only). */
            String chatState,
            Instant chatOpensAt,
            Instant chatClosesAt,
            /** Messages from others the viewer hasn't seen (members only). */
            long chatUnread) {
    }

    /** Create payload. Extra client fields (hostName, joined, …) are ignored. */
    public record CreateMeetupRequest(
            @NotBlank @Size(max = 120) String title,
            /** Legacy free text; derived from {@code kind} when omitted. */
            String category,
            @NotBlank @Size(max = 60) String area,
            String venue,
            Instant startsAt,
            int capacity,
            @Size(max = 1000) String description,
            MeetupEnums.Kind kind,
            MeetupEnums.PetPolicy petPolicy,
            @Min(15) @Max(1440) Integer durationMinutes) {
    }

    /** Optional RSVP body. */
    public record RsvpRequest(Boolean withPet) {
        public boolean bringingPet() { return Boolean.TRUE.equals(withPet); }
    }
}
