package com.unkittered.api.meetup;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.meetup.MeetupDtos.CreateMeetupRequest;
import com.unkittered.api.meetup.MeetupDtos.MeetupDto;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lists, creates and RSVPs to meetups, mapping to the client wire shape. */
@Service
public class MeetupService {

    /** How many attendee avatars to surface on a card. */
    private static final int AVATAR_PREVIEW = 5;

    private final MeetupRepository meetups;
    private final MeetupRsvpRepository rsvps;
    private final ProfileRepository profiles;

    public MeetupService(MeetupRepository meetups, MeetupRsvpRepository rsvps,
                         ProfileRepository profiles) {
        this.meetups = meetups;
        this.rsvps = rsvps;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<MeetupDto> feedFor(UUID viewerId) {
        List<MeetupDto> out = new ArrayList<>();
        for (Meetup m : meetups.findAllByOrderByStartsAtAsc()) {
            out.add(toDto(m, viewerId));
        }
        return out;
    }

    @Transactional
    public MeetupDto create(UUID hostId, CreateMeetupRequest req) {
        Meetup saved = meetups.save(new Meetup(
                hostId, req.title(), req.category(), req.area(),
                req.venue(), req.startsAt(), req.capacity(), req.description()));
        // The host is automatically attending.
        rsvps.save(new MeetupRsvp(saved.getId(), hostId));
        return toDto(saved, hostId);
    }

    @Transactional
    public MeetupDto rsvp(UUID meetupId, UUID userId, boolean joining) {
        Meetup m = meetups.findById(meetupId)
                .orElseThrow(() -> ApiException.notFound("Meetup not found"));
        if (joining) {
            if (m.getCapacity() > 0
                    && rsvps.countByMeetupId(meetupId) >= m.getCapacity()
                    && !rsvps.existsByMeetupIdAndUserId(meetupId, userId)) {
                throw ApiException.badRequest("This meetup is fully booked");
            }
            if (!rsvps.existsByMeetupIdAndUserId(meetupId, userId)) {
                rsvps.save(new MeetupRsvp(meetupId, userId));
            }
        } else {
            rsvps.deleteByMeetupIdAndUserId(meetupId, userId);
        }
        return toDto(m, userId);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────
    private MeetupDto toDto(Meetup m, UUID viewerId) {
        Profile host = profiles.findById(m.getHostId()).orElse(null);
        List<MeetupRsvp> attendees = rsvps.findByMeetupId(m.getId());

        List<String> avatars = new ArrayList<>();
        for (MeetupRsvp r : attendees) {
            if (avatars.size() >= AVATAR_PREVIEW) break;
            avatars.add(avatarFor(r.getUserId()));
        }

        return new MeetupDto(
                m.getId().toString(),
                m.getTitle(),
                m.getCategory(),
                host != null ? host.getName() : "Someone",
                avatarFor(m.getHostId()),
                m.getArea(),
                m.getVenue(),
                m.getStartsAt(),
                m.getCapacity(),
                attendees.size(),
                avatars,
                m.getDescription(),
                rsvps.existsByMeetupIdAndUserId(m.getId(), viewerId),
                m.getHostId().equals(viewerId));
    }

    /** A ring emoji for a user: their first pet token, else a paw. */
    private String avatarFor(UUID userId) {
        return profiles.findById(userId).map(p -> {
            List<String> pets = p.getPets();
            if (pets != null && !pets.isEmpty()) {
                String first = pets.get(0).trim();
                int space = first.indexOf(' ');
                return space > 0 ? first.substring(0, space) : first;
            }
            return "🐾";
        }).orElse("🐾");
    }
}
