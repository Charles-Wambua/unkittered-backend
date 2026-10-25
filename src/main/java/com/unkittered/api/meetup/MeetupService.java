package com.unkittered.api.meetup;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.meetup.MeetupDtos.CreateMeetupRequest;
import com.unkittered.api.meetup.MeetupDtos.MeetupDto;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.push.PushService;
import com.unkittered.api.safety.BlockRepository;
import com.unkittered.api.subscription.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Lists, creates and RSVPs to meetups, mapping to the client wire shape. */
@Service
public class MeetupService {

    /** How many attendee avatars to surface on a card. */
    private static final int AVATAR_PREVIEW = 5;

    /** How far back "Past packs" goes. */
    private static final Duration PAST_WINDOW = Duration.ofDays(90);

    private final MeetupRepository meetups;
    private final MeetupRsvpRepository rsvps;
    private final MeetupReportRepository reports;
    private final ProfileRepository profiles;
    private final SubscriptionService subscriptions;
    private final PackMessageRepository chat;
    private final PushService push;
    private final BlockRepository blocks;
    private final DiscoverService discover;

    public MeetupService(MeetupRepository meetups, MeetupRsvpRepository rsvps,
                         MeetupReportRepository reports, ProfileRepository profiles,
                         SubscriptionService subscriptions, PackMessageRepository chat,
                         PushService push, BlockRepository blocks, DiscoverService discover) {
        this.subscriptions = subscriptions;
        this.chat = chat;
        this.push = push;
        this.blocks = blocks;
        this.discover = discover;
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
        // Tell everyone who was going before the RSVPs disappear with it.
        List<UUID> going = rsvps.findByMeetupId(meetupId).stream()
                .map(MeetupRsvp::getUserId).filter(id -> !id.equals(userId)).toList();
        if (!going.isEmpty() && !"ended".equals(PackTimeline.status(m, Instant.now()))) {
            push.sendToUsers(going, "Pack cancelled",
                    "“" + m.getTitle() + "” was cancelled by the host.",
                    java.util.Map.of("type", "meetup_cancelled", "meetupId", meetupId.toString()));
        }
        meetups.deleteById(meetupId);
    }

    /**
     * Someone else in the same pack, to open their profile from the pack chat.
     * Both must be going, and neither may have blocked the other.
     */
    @Transactional(readOnly = true)
    public ProfileDto memberProfile(UUID meetupId, UUID viewerId, UUID memberId) {
        if (!rsvps.existsByMeetupIdAndUserId(meetupId, viewerId)
                || !rsvps.existsByMeetupIdAndUserId(meetupId, memberId)
                || blocks.existsByBlockerIdAndBlockedId(viewerId, memberId)
                || blocks.existsByBlockerIdAndBlockedId(memberId, viewerId)) {
            throw ApiException.notFound("Profile not found");
        }
        Profile p = profiles.findById(memberId).orElseThrow(() -> ApiException.notFound("Profile not found"));
        return discover.viewOf(viewerId, p);
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

    /** The board: packs that haven't ended yet, soonest first. */
    @Transactional(readOnly = true)
    public List<MeetupDto> feedFor(UUID viewerId) {
        Instant now = Instant.now();
        List<MeetupDto> out = new ArrayList<>();
        for (Meetup m : meetups.findByStartsAtAfterOrderByStartsAtAsc(now.minus(Duration.ofDays(1)))) {
            if (m.getEndsAt().isAfter(now)) out.add(toDto(m, viewerId, now));
        }
        return out;
    }

    /** Packs the viewer went to (or hosted) that have ended, most recent first. */
    @Transactional(readOnly = true)
    public List<MeetupDto> pastFor(UUID viewerId) {
        Instant now = Instant.now();
        Set<UUID> mine = rsvps.findByUserId(viewerId).stream()
                .map(MeetupRsvp::getMeetupId).collect(Collectors.toSet());
        return meetups.findAllById(mine).stream()
                .filter(m -> !m.getEndsAt().isAfter(now) && m.getEndsAt().isAfter(now.minus(PAST_WINDOW)))
                .sorted(Comparator.comparing(Meetup::getStartsAt).reversed())
                .map(m -> toDto(m, viewerId, now))
                .toList();
    }

    /** One pack, in any state (links and notifications can point at old ones). */
    @Transactional(readOnly = true)
    public MeetupDto one(UUID meetupId, UUID viewerId) {
        Meetup m = meetups.findById(meetupId)
                .orElseThrow(() -> ApiException.notFound("This pack no longer exists"));
        return toDto(m, viewerId, Instant.now());
    }

    @Transactional
    public MeetupDto create(UUID hostId, CreateMeetupRequest req) {
        if (req.startsAt() == null || !req.startsAt().isAfter(Instant.now())) {
            throw ApiException.badRequest("Pick a start time in the future.");
        }
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
        return toDto(saved, hostId, Instant.now());
    }

    /**
     * Join or leave. A full meetup is closed to everyone except Gold members,
     * who "skip the waitlist". Pets only come along when the policy allows.
     */
    @Transactional
    public MeetupDto rsvp(UUID meetupId, UUID userId, boolean joining, boolean withPet) {
        Meetup m = meetups.findById(meetupId)
                .orElseThrow(() -> ApiException.notFound("Meetup not found"));
        Instant now = Instant.now();
        if (!PackTimeline.acceptsRsvps(m, now)) {
            throw ApiException.conflict("ended".equals(PackTimeline.status(m, now))
                    ? "This pack has ended."
                    : "This pack has already started, so RSVPs are closed.");
        }
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
        return toDto(m, userId, now);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────
    MeetupDto toDto(Meetup m, UUID viewerId, Instant now) {
        Profile host = profiles.findById(m.getHostId()).orElse(null);
        List<MeetupRsvp> attendees = rsvps.findByMeetupId(m.getId());
        MeetupRsvp mine = attendees.stream().filter(r -> r.getUserId().equals(viewerId)).findFirst().orElse(null);
        long unread = mine == null ? 0 : mine.getChatReadAt() == null
                ? chat.countByMeetupIdAndSenderIdNot(m.getId(), viewerId)
                : chat.countByMeetupIdAndSenderIdNotAndCreatedAtAfter(m.getId(), viewerId, mine.getChatReadAt());

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
                mine != null,
                m.getHostId().equals(viewerId),
                MeetupEnums.Kind.fromWire(m.getKind()),
                MeetupEnums.PetPolicy.fromWire(m.getPetPolicy()),
                m.getDurationMinutes(),
                rsvps.countByMeetupIdAndWithPetTrue(m.getId()),
                mine != null && mine.isWithPet(),
                m.getEndsAt(),
                PackTimeline.status(m, now),
                PackTimeline.chatState(m, now),
                PackTimeline.chatOpensAt(m),
                PackTimeline.chatClosesAt(m),
                unread,
                mine != null ? m.getMeetingPoint() : null);
    }

    /**
     * A user's face for the host row and attendee strip: their photo path,
     * else (no photo yet) their first pet emoji, else a paw.
     */
    String avatarFor(UUID userId) {
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
