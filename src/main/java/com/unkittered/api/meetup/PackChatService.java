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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A pack's group chat. Only members (everyone who RSVP'd, host included) can
 * read or post, and only while {@link PackTimeline#chatState} is "open";
 * afterwards it stays readable.
 */
@Service
public class PackChatService {

    private static final int MAX_TEXT = 2000;
    private static final int MAX_QUOTE = 120;

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

    public record PackMessageDto(String id, String senderId, String senderName, String senderAvatar,
                                 boolean isMe, String text, Instant createdAt, Quote replyTo) { }

    public record ThreadDto(String meetupId, String title, String chatState, Instant chatOpensAt,
                            Instant chatClosesAt, int memberCount, List<PackMessageDto> messages) { }

    public record SendRequest(@NotBlank @Size(max = MAX_TEXT) String text, String replyToId) { }

    /** Live event for other members' open sessions. */
    public record PackMessagePush(String type, String meetupId, PackMessageDto message) { }

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
        return new ThreadDto(meetupId.toString(), m.getTitle(), PackTimeline.chatState(m, now),
                PackTimeline.chatOpensAt(m), PackTimeline.chatClosesAt(m),
                rsvps.findByMeetupId(meetupId).size(), out);
    }

    @Transactional
    public PackMessageDto send(UUID meetupId, UUID viewerId, SendRequest req) {
        Meetup m = find(meetupId);
        MeetupRsvp me = requireMember(m, viewerId);
        String state = PackTimeline.chatState(m, Instant.now());
        if ("locked".equals(state)) {
            throw ApiException.conflict("The pack chat opens a day before the pack starts.");
        }
        if ("closed".equals(state)) {
            throw ApiException.conflict("This pack chat has closed.");
        }
        String text = req.text().strip();
        if (text.isEmpty()) throw ApiException.badRequest("Write a message first.");
        boolean gold = "gold".equalsIgnoreCase(subscriptions.tierOf(viewerId));
        MessageModeration.Flag flag = MessageModeration.check(text, gold);
        if (flag != MessageModeration.Flag.CLEAN) {
            throw ApiException.badRequest(MessageModeration.reason(flag));
        }
        PackMessage repliedTo = replyTarget(meetupId, req.replyToId());
        PackMessage saved = messages.save(new PackMessage(meetupId, viewerId, text,
                repliedTo == null ? null : repliedTo.getId()));
        me.setChatReadAt(saved.getCreatedAt());

        Names names = new Names();
        List<UUID> others = rsvps.findByMeetupId(meetupId).stream()
                .map(MeetupRsvp::getUserId).filter(id -> !id.equals(viewerId)).toList();
        PackMessageDto forOthers = toDto(saved, null, repliedTo, names);
        others.forEach(id -> realtime.sendToUser(id,
                new PackMessagePush("pack_message", meetupId.toString(), forOthers)));

        String preview = text.length() > 100 ? text.substring(0, 99) + "…" : text;
        push.sendToUsers(others, m.getTitle(), names.of(viewerId) + ": " + preview,
                Map.of("type", "pack_message", "meetupId", meetupId.toString(),
                        "senderId", viewerId.toString()));
        return toDto(saved, viewerId, repliedTo, names);
    }

    private PackMessageDto toDto(PackMessage x, UUID viewerId, PackMessage repliedTo, Names names) {
        Quote quote = null;
        if (repliedTo != null) {
            String body = repliedTo.getBody();
            quote = new Quote(repliedTo.getId().toString(), repliedTo.getSenderId().toString(),
                    names.of(repliedTo.getSenderId()),
                    body.length() > MAX_QUOTE ? body.substring(0, MAX_QUOTE - 1) + "…" : body);
        }
        return new PackMessageDto(x.getId().toString(), x.getSenderId().toString(), names.of(x.getSenderId()),
                meetupService.avatarFor(x.getSenderId()), x.getSenderId().equals(viewerId), x.getBody(),
                x.getCreatedAt(), quote);
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
