package com.unkittered.api.meetup;

import com.unkittered.api.chat.MessageModeration;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.push.PushService;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.subscription.SubscriptionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A pack's group chat. Only members (everyone who RSVP'd, host included) can
 * read or post, and only while {@link PackTimeline#chatState} is "open";
 * afterwards it stays readable.
 *
 * <p>Unlike 1:1 chats, contact details are never allowed here, Gold or not:
 * a pack is a room of people you haven't chosen. Members connect by opening
 * each other's profiles and wagging instead.
 */
@Service
public class PackChatService {

    private static final int MAX_TEXT = 2000;
    private static final int MAX_QUOTE = 120;
    static final String DELETED_TEXT = "This message was deleted";
    /** "I'm here" opens this long before the start. */
    private static final Duration ARRIVAL_WINDOW = Duration.ofHours(1);

    /** Message kinds: what people write, and the two the app posts for them. */
    public static final String TEXT = "text";
    public static final String ARRIVAL = "arrival";
    public static final String MEETING_POINT = "meeting_point";

    private final MeetupRepository meetups;
    private final MeetupRsvpRepository rsvps;
    private final PackMessageRepository messages;
    private final ProfileRepository profiles;
    private final MeetupService meetupService;
    private final SubscriptionService subscriptions;
    private final RealtimeGateway realtime;
    private final PushService push;

    public PackChatService(MeetupRepository meetups, MeetupRsvpRepository rsvps,
                           PackMessageRepository messages, ProfileRepository profiles,
                           MeetupService meetupService, SubscriptionService subscriptions,
                           RealtimeGateway realtime, PushService push) {
        this.meetups = meetups;
        this.rsvps = rsvps;
        this.messages = messages;
        this.profiles = profiles;
        this.meetupService = meetupService;
        this.subscriptions = subscriptions;
        this.realtime = realtime;
        this.push = push;
    }

    /** Quoted message in a reply. */
    public record Quote(String id, String senderId, String senderName, String text) { }

    /** {@code kind}: text | arrival | meeting_point. Deleted messages keep their place. */
    public record PackMessageDto(String id, String senderId, String senderName, String senderAvatar,
                                 boolean isMe, String text, Instant createdAt, Quote replyTo,
                                 String kind, boolean deleted) { }

    public record ThreadDto(String meetupId, String title, String chatState, Instant chatOpensAt,
                            Instant chatClosesAt, int memberCount, List<PackMessageDto> messages,
                            String meetingPoint, boolean hosting, boolean canArrive, boolean arrived) { }

    public record SendRequest(@NotBlank @Size(max = MAX_TEXT) String text, String replyToId) { }

    public record MeetingPointRequest(@Size(max = 140) String text) { }

    /** Live events for other members' open sessions. */
    public record PackMessagePush(String type, String meetupId, PackMessageDto message) { }

    public record PackMessageDeletedPush(String type, String meetupId, String id) { }

    @Transactional
    public ThreadDto thread(UUID meetupId, UUID viewerId) {
        Meetup m = find(meetupId);
        MeetupRsvp me = requireMember(m, viewerId);
        me.setChatReadAt(Instant.now());
        List<PackMessage> all = messages.findByMeetupIdOrderByCreatedAtAsc(meetupId);
        Map<UUID, PackMessage> byId = new HashMap<>();
        all.forEach(x -> byId.put(x.getId(), x));
        Names names = new Names();
        List<PackMessageDto> out = all.stream()
                .map(x -> toDto(x, viewerId, x.getReplyToId() == null ? null : byId.get(x.getReplyToId()), names))
                .toList();
        Instant now = Instant.now();
        boolean arrived = all.stream().anyMatch(x -> ARRIVAL.equals(x.getKind()) && x.getSenderId().equals(viewerId));
        return new ThreadDto(meetupId.toString(), m.getTitle(), PackTimeline.chatState(m, now),
                PackTimeline.chatOpensAt(m), PackTimeline.chatClosesAt(m),
                rsvps.findByMeetupId(meetupId).size(), out, m.getMeetingPoint(),
                m.getHostId().equals(viewerId), canArrive(m, now), arrived);
    }

    @Transactional
    public PackMessageDto send(UUID meetupId, UUID viewerId, SendRequest req) {
        Meetup m = find(meetupId);
        MeetupRsvp me = requireMember(m, viewerId);
        requireOpen(m);
        String text = req.text().strip();
        if (text.isEmpty()) throw ApiException.badRequest("Write a message first.");
        moderate(text);
        PackMessage repliedTo = replyTarget(meetupId, req.replyToId());
        PackMessage saved = messages.save(new PackMessage(meetupId, viewerId, text,
                repliedTo == null ? null : repliedTo.getId()));
        me.setChatReadAt(saved.getCreatedAt());
        return broadcast(m, saved, viewerId, repliedTo, null);
    }

    /** "I'm here 📍": once per member, from an hour before until the pack ends. */
    @Transactional
    public PackMessageDto arrived(UUID meetupId, UUID viewerId) {
        Meetup m = find(meetupId);
        requireMember(m, viewerId);
        if (!canArrive(m, Instant.now())) {
            throw ApiException.conflict("You can check in from an hour before the pack starts.");
        }
        Names names = new Names();
        var existing = messages.findFirstByMeetupIdAndSenderIdAndKind(meetupId, viewerId, ARRIVAL);
        if (existing.isPresent()) return toDto(existing.get(), viewerId, null, names);
        PackMessage saved = messages.save(new PackMessage(meetupId, viewerId,
                "📍 " + names.of(viewerId) + " is here", null).kind(ARRIVAL));
        return broadcast(m, saved, viewerId, null, names);
    }

    /** Host only: where to find the group. Posted to the chat when it's open. */
    @Transactional
    public ThreadDto setMeetingPoint(UUID meetupId, UUID viewerId, MeetingPointRequest req) {
        Meetup m = find(meetupId);
        if (!m.getHostId().equals(viewerId)) throw ApiException.forbidden("Only the host can set the meeting point.");
        String text = req.text() == null ? "" : req.text().strip();
        if (!text.isEmpty()) moderate(text);
        m.setMeetingPoint(text.isEmpty() ? null : text);
        if (!text.isEmpty() && "open".equals(PackTimeline.chatState(m, Instant.now()))) {
            PackMessage saved = messages.save(new PackMessage(meetupId, viewerId,
                    "📌 Meeting point: " + text, null).kind(MEETING_POINT));
            broadcast(m, saved, viewerId, null, null);
        }
        return thread(meetupId, viewerId);
    }

    /**
     * Deletes a message for everyone. Your own needs Gold; the host can
     * remove anything in their pack (moderation).
     */
    @Transactional
    public void delete(UUID meetupId, UUID viewerId, UUID messageId) {
        Meetup m = find(meetupId);
        requireMember(m, viewerId);
        PackMessage msg = messages.findById(messageId)
                .filter(x -> x.getMeetupId().equals(meetupId))
                .orElseThrow(() -> ApiException.notFound("Message not found"));
        boolean host = m.getHostId().equals(viewerId);
        boolean own = msg.getSenderId().equals(viewerId);
        if (!host) {
            if (!own) throw ApiException.forbidden("You can only delete your own messages.");
            if (!"gold".equalsIgnoreCase(subscriptions.tierOf(viewerId))) {
                throw ApiException.forbidden("Deleting messages is a Gold feature.");
            }
        }
        if (msg.isDeleted()) return;
        msg.delete();
        rsvps.findByMeetupId(meetupId).stream().map(MeetupRsvp::getUserId).filter(id -> !id.equals(viewerId))
                .forEach(id -> realtime.sendToUser(id,
                        new PackMessageDeletedPush("pack_message_deleted", meetupId.toString(), messageId.toString())));
    }

    private PackMessageDto broadcast(Meetup m, PackMessage saved, UUID viewerId, PackMessage repliedTo, Names cached) {
        Names names = cached != null ? cached : new Names();
        List<UUID> others = rsvps.findByMeetupId(m.getId()).stream()
                .map(MeetupRsvp::getUserId).filter(id -> !id.equals(viewerId)).toList();
        PackMessageDto forOthers = toDto(saved, null, repliedTo, names);
        others.forEach(id -> realtime.sendToUser(id,
                new PackMessagePush("pack_message", m.getId().toString(), forOthers)));
        String body = saved.getBody();
        String preview = ARRIVAL.equals(saved.getKind()) || MEETING_POINT.equals(saved.getKind())
                ? body
                : names.of(viewerId) + ": " + (body.length() > 100 ? body.substring(0, 99) + "…" : body);
        push.sendToUsers(others, m.getTitle(), preview,
                Map.of("type", "pack_message", "meetupId", m.getId().toString(),
                        "senderId", viewerId.toString()));
        return toDto(saved, viewerId, repliedTo, names);
    }

    private static boolean canArrive(Meetup m, Instant now) {
        return !now.isBefore(m.getStartsAt().minus(ARRIVAL_WINDOW)) && now.isBefore(m.getEndsAt());
    }

    /** Kindness, and no contact details for anyone (see the class comment). */
    private static void moderate(String text) {
        MessageModeration.Flag flag = MessageModeration.check(text, false);
        if (flag != MessageModeration.Flag.CLEAN) {
            throw ApiException.badRequest(MessageModeration.reasonInGroup(flag));
        }
    }

    private void requireOpen(Meetup m) {
        String state = PackTimeline.chatState(m, Instant.now());
        if ("locked".equals(state)) {
            throw ApiException.conflict("The pack chat opens a day before the pack starts.");
        }
        if ("closed".equals(state)) {
            throw ApiException.conflict("This pack chat has closed.");
        }
    }

    private PackMessageDto toDto(PackMessage x, UUID viewerId, PackMessage repliedTo, Names names) {
        Quote quote = null;
        if (repliedTo != null) {
            String body = repliedTo.isDeleted() ? DELETED_TEXT : repliedTo.getBody();
            quote = new Quote(repliedTo.getId().toString(), repliedTo.getSenderId().toString(),
                    names.of(repliedTo.getSenderId()),
                    body.length() > MAX_QUOTE ? body.substring(0, MAX_QUOTE - 1) + "…" : body);
        }
        return new PackMessageDto(x.getId().toString(), x.getSenderId().toString(), names.of(x.getSenderId()),
                meetupService.avatarFor(x.getSenderId()), x.getSenderId().equals(viewerId),
                x.isDeleted() ? DELETED_TEXT : x.getBody(), x.getCreatedAt(), x.isDeleted() ? null : quote,
                x.getKind(), x.isDeleted());
    }

    private PackMessage replyTarget(UUID meetupId, String replyToId) {
        if (replyToId == null || replyToId.isBlank()) return null;
        try {
            return messages.findById(UUID.fromString(replyToId))
                    .filter(x -> x.getMeetupId().equals(meetupId))
                    .orElseThrow(() -> ApiException.badRequest("Unknown message to reply to"));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown message to reply to");
        }
    }

    private Meetup find(UUID meetupId) {
        return meetups.findById(meetupId).orElseThrow(() -> ApiException.notFound("This pack no longer exists"));
    }

    private MeetupRsvp requireMember(Meetup m, UUID viewerId) {
        return rsvps.findByMeetupIdAndUserId(m.getId(), viewerId)
                .orElseThrow(() -> ApiException.forbidden("Join the pack to see its chat."));
    }

    /** Display names, looked up once per request. */
    private final class Names {
        private final Map<UUID, String> cache = new HashMap<>();

        String of(UUID userId) {
            return cache.computeIfAbsent(userId, id -> profiles.findById(id).map(Profile::getName)
                    .filter(n -> n != null && !n.isBlank()).orElse("Someone"));
        }
    }
}
