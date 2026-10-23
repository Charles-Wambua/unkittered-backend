package com.unkittered.api.meetup;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.meetup.MeetupDtos.CreateMeetupRequest;
import com.unkittered.api.meetup.MeetupDtos.MeetupDto;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.subscription.SubscriptionService;
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
    private final MeetupReportRepository reports;
    private final ProfileRepository profiles;
    private final SubscriptionService subscriptions;

    public MeetupService(MeetupRepository meetups, MeetupRsvpRepository rsvps,
                         MeetupReportRepository reports, ProfileRepository profiles,
                         SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
        this.meetups = meetups;
        this.rsvps = rsvps;
        this.reports = reports;
        this.profiles = profiles;
    }

    /** Host-only: delete a meetup (RSVPs/reports cascade). */
    @Transactional
    public void delete(UUID meetupId, UUID userId) {
        Meetup m = meetups.findById(meetupId)
                .orElseThrow(() -> ApiException.notFound("Meetup not found"));
        if (!m.getHostId().equals(userId)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Only the host can cancel this meetup");
        }
        meetups.deleteById(meetupId);
    }

    /** File a moderation report against a meetup. */
    @Transactional
    public void report(UUID meetupId, UUID reporterId, String reason) {
        if (!meetups.existsById(meetupId)) {
            throw ApiException.notFound("Meetup not found");
        }
        reports.save(new MeetupReport(meetupId, reporterId,
                reason == null || reason.isBlank() ? "Unspecified" : reason));
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
        MeetupEnums.Kind kind = req.kind() != null ? req.kind() : MeetupEnums.Kind.CAFE;
        MeetupEnums.PetPolicy policy = req.petPolicy() != null ? req.petPolicy() : MeetupEnums.PetPolicy.PETS_WELCOME;
        String category = req.category() == null || req.category().isBlank() ? kind.label() : req.category();
        Meetup m = new Meetup(
                hostId, req.title(), category, req.area(),
                req.venue(), req.startsAt(), req.capacity(), req.description());
        m.setKind(kind.wire());
        m.setPetPolicy(policy.wire());
        if (req.durationMinutes() != null) m.setDurationMinutes(req.durationMinutes());
        Meetup saved = meetups.save(m);
        // The host is automatically attending.
        rsvps.save(new MeetupRsvp(saved.getId(), hostId));
        return toDto(saved, hostId);
    }

    /**
     * Join or leave. A full meetup is closed to everyone except Gold members,
     * who "skip the waitlist". Pets only come along when the policy allows.
     */
    @Transactional
    public MeetupDto rsvp(UUID meetupId, UUID userId, boolean joining, boolean withPet) {
        Meetup m = meetups.findById(meetupId)
                .orElseThrow(() -> ApiException.notFound("Meetup not found"));
        if (joining) {
            boolean bringPet = withPet && MeetupEnums.PetPolicy.fromWire(m.getPetPolicy()).allowsPets();
            var existing = rsvps.findByMeetupIdAndUserId(meetupId, userId);
            if (existing.isPresent()) {
                existing.get().setWithPet(bringPet);
            } else {
                boolean full = m.getCapacity() > 0 && rsvps.countByMeetupId(meetupId) >= m.getCapacity();
                if (full && !"gold".equalsIgnoreCase(subscriptions.tierOf(userId))) {
                    throw ApiException.conflict("This pack is full. Gold members skip the waitlist.");
                }
                MeetupRsvp r = new MeetupRsvp(meetupId, userId);
                r.setWithPet(bringPet);
                rsvps.save(r);
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
                m.getHostId().equals(viewerId),
                MeetupEnums.Kind.fromWire(m.getKind()),
                MeetupEnums.PetPolicy.fromWire(m.getPetPolicy()),
                m.getDurationMinutes(),
                rsvps.countByMeetupIdAndWithPetTrue(m.getId()),
                rsvps.findByMeetupIdAndUserId(m.getId(), viewerId).map(MeetupRsvp::isWithPet).orElse(false));
    }

    /**
     * A user's face for the host row and attendee strip: their photo path,
     * else (no photo yet) their first pet emoji, else a paw.
     */
    private String avatarFor(UUID userId) {
        return profiles.findById(userId).map(p -> {
            if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) {
                return p.getImageUrl();
            }
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
