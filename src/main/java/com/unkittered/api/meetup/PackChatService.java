package com.unkittered.api.meetup;

import com.unkittered.api.chat.MessageModeration;
import com.unkittered.api.chat.Reactions;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    public static final String POLL = "poll";

    private static final int MAX_POLL_OPTIONS = 6;

    private final MeetupRepository meetups;
    private final MeetupRsvpRepository rsvps;
    private final PackMessageRepository messages;
    private final ProfileRepository profiles;
    private final MeetupService meetupService;
    private final SubscriptionService subscriptions;
    private final RealtimeGateway realtime;
    private final PushService push;
    private final PackMessageReactionRepository reactions;
    private final PollVoteRepository votes;

    public PackChatService(MeetupRepository meetups, MeetupRsvpRepository rsvps,
                           PackMessageRepository messages, ProfileRepository profiles,
                           MeetupService meetupService, SubscriptionService subscriptions,
                           RealtimeGateway realtime, PushService push,
                           PackMessageReactionRepository reactions, PollVoteRepository votes) {
        this.reactions = reactions;
        this.votes = votes;
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

    /** A poll option and who chose it. */
    public record PollOption(String text, List<String> voterIds) { }

    /** {@code question} is the message text. */
    public record PollDto(List<PollOption> options, boolean closed) { }

    /**
     * {@code kind}: text | arrival | meeting_point | poll. Deleted messages keep
     * their place. {@code reactions}: emoji → user ids.
     */
    public record PackMessageDto(String id, String senderId, String senderName, String senderAvatar,
                                 boolean isMe, String text, Instant createdAt, Quote replyTo,
                                 String kind, boolean deleted, Instant editedAt,
                                 Map<String, List<String>> reactions, PollDto poll) { }

    public record EditRequest(@NotBlank @Size(max = MAX_TEXT) String text) { }

    public record ReactRequest(@NotBlank String emoji) { }

    public record PollRequest(@NotBlank @Size(max = 140) String question,
                              @jakarta.validation.constraints.NotNull List<String> options) { }

    public record VoteRequest(int option) { }

    public record ThreadDto(String meetupId, String title, String chatState, Instant chatOpensAt,
                            Instant chatClosesAt, int memberCount, List<PackMessageDto> messages,
                            String meetingPoint, boolean hosting, boolean canArrive, boolean arrived) { }

    public record SendRequest(@NotBlank @Size(max = MAX_TEXT) String text, String replyToId) { }

    public record MeetingPointRequest(@Size(max = 140) String text) { }

    /** Live events for other members' open sessions. */
    public record PackMessagePush(String type, String meetupId, PackMessageDto message) { }

    public record PackMessageDeletedPush(String type, String meetupId, String id) { }

    /** Edited text, reactions or poll votes changed. */
    public record PackMessageUpdatedPush(String type, String meetupId, PackMessageDto message) { }

    @Transactional
    public ThreadDto thread(UUID meetupId, UUID viewerId) {
        Meetup m = find(meetupId);
        MeetupRsvp me = requireMember(m, viewerId);
        me.setChatReadAt(Instant.now());
        List<PackMessage> all = messages.findByMeetupIdOrderByCreatedAtAsc(meetupId);
        Map<UUID, PackMessage> byId = new HashMap<>();
        all.forEach(x -> byId.put(x.getId(), x));
        Names names = new Names();
        Map<UUID, List<PackMessageReaction>> reacted = new HashMap<>();
        reactions.findByMessageIdIn(byId.keySet())
                .forEach(r -> reacted.computeIfAbsent(r.getMessageId(), k -> new ArrayList<>()).add(r));
        Map<UUID, List<PollVote>> voted = new HashMap<>();
        votes.findByMessageIdIn(byId.keySet())
                .forEach(v -> voted.computeIfAbsent(v.getMessageId(), k -> new ArrayList<>()).add(v));
        List<PackMessageDto> out = all.stream()
                .map(x -> toDto(x, viewerId, x.getReplyToId() == null ? null : byId.get(x.getReplyToId()), names,
                        reacted.getOrDefault(x.getId(), List.of()), voted.getOrDefault(x.getId(), List.of())))
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
        if (existing.isPresent()) return full(existing.get(), viewerId, names);
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

    /** Edits my text message, within {@link Reactions#EDIT_WINDOW}, while the chat is open. */
    @Transactional
    public PackMessageDto edit(UUID meetupId, UUID viewerId, UUID messageId, String text) {
        Meetup m = find(meetupId);
        requireMember(m, viewerId);
        requireOpen(m);
        PackMessage x = inPack(meetupId, messageId);
        if (!x.getSenderId().equals(viewerId)) throw ApiException.forbidden("You can only edit your own messages.");
        if (x.isDeleted() || !TEXT.equals(x.getKind())) throw ApiException.badRequest("Only text messages can be edited.");
        Reactions.requireEditable(x.getCreatedAt(), Instant.now());
        String t = text.strip();
        if (t.isEmpty()) throw ApiException.badRequest("Write a message first.");
        moderate(t);
        x.edit(t);
        return pushUpdate(m, viewerId, x);
    }

    /** Reacts to a message ({@code emoji} null removes mine). */
    @Transactional
    public PackMessageDto react(UUID meetupId, UUID viewerId, UUID messageId, String emoji) {
        Meetup m = find(meetupId);
        requireMember(m, viewerId);
        PackMessage x = inPack(meetupId, messageId);
        if (x.isDeleted()) throw ApiException.badRequest("That message was deleted.");
        var key = new PackMessageReaction.Key(messageId, viewerId);
        if (emoji == null) {
            reactions.deleteById(key);
        } else {
            String e = Reactions.validate(emoji);
            reactions.findById(key).ifPresentOrElse(r -> r.setEmoji(e),
                    () -> reactions.save(new PackMessageReaction(messageId, viewerId, e)));
        }
        reactions.flush();
        return pushUpdate(m, viewerId, x);
    }

    /** Any member can ask the pack something: a question and 2–6 options. */
    @Transactional
    public PackMessageDto createPoll(UUID meetupId, UUID viewerId, PollRequest req) {
        Meetup m = find(meetupId);
        requireMember(m, viewerId);
        requireOpen(m);
        String question = req.question().strip();
        List<String> options = req.options().stream().map(o -> o == null ? "" : o.strip())
                .filter(o -> !o.isEmpty()).distinct().toList();
        if (question.isEmpty()) throw ApiException.badRequest("Ask a question.");
        if (options.size() < 2 || options.size() > MAX_POLL_OPTIONS) {
            throw ApiException.badRequest("Give 2 to " + MAX_POLL_OPTIONS + " different options.");
        }
        if (options.stream().anyMatch(o -> o.length() > 80)) throw ApiException.badRequest("Keep options short.");
        moderate(question);
        options.forEach(PackChatService::moderate);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("options", options);
        payload.put("closed", false);
        PackMessage saved = messages.save(new PackMessage(meetupId, viewerId, question, null)
                .kind(POLL).payload(payload));
        Names names = new Names();
        PackMessageDto forOthers = toDto(saved, null, null, names);
        rsvps.findByMeetupId(meetupId).stream().map(MeetupRsvp::getUserId).filter(id -> !id.equals(viewerId))
                .forEach(id -> realtime.sendToUser(id, new PackMessagePush("pack_message", meetupId.toString(), forOthers)));
        push.sendToUsers(rsvps.findByMeetupId(meetupId).stream().map(MeetupRsvp::getUserId)
                        .filter(id -> !id.equals(viewerId)).toList(), m.getTitle(),
                "📊 " + names.of(viewerId) + " asked: " + question,
                Map.of("type", "pack_message", "meetupId", meetupId.toString(), "senderId", viewerId.toString()));
        return toDto(saved, viewerId, null, names);
    }

    /** Votes for an option; choosing the same one again takes the vote back. */
    @Transactional
    public PackMessageDto vote(UUID meetupId, UUID viewerId, UUID pollId, int option) {
        Meetup m = find(meetupId);
        requireMember(m, viewerId);
        PackMessage x = openPoll(meetupId, pollId);
        PollDto current = poll(x, List.of());
        if (option < 0 || option >= current.options().size()) throw ApiException.badRequest("Pick one of the options.");
        var key = new PollVote.Key(pollId, viewerId);
        var existing = votes.findById(key);
        if (existing.isPresent() && existing.get().getOption() == option) {
            votes.delete(existing.get());
        } else if (existing.isPresent()) {
            existing.get().setOption(option);
        } else {
            votes.save(new PollVote(pollId, viewerId, option));
        }
        votes.flush();
        return pushUpdate(m, viewerId, x);
    }

    /** The poll's creator or the host closes it; results stay visible. */
    @Transactional
    public PackMessageDto closePoll(UUID meetupId, UUID viewerId, UUID pollId) {
        Meetup m = find(meetupId);
        requireMember(m, viewerId);
        PackMessage x = openPoll(meetupId, pollId);
        if (!x.getSenderId().equals(viewerId) && !m.getHostId().equals(viewerId)) {
            throw ApiException.forbidden("Only whoever asked, or the host, can close this poll.");
        }
        Map<String, Object> payload = new LinkedHashMap<>(x.getPayload());
        payload.put("closed", true);
        x.payload(payload);
        return pushUpdate(m, viewerId, x);
    }

    private PackMessage openPoll(UUID meetupId, UUID pollId) {
        PackMessage x = inPack(meetupId, pollId);
        if (!POLL.equals(x.getKind()) || x.isDeleted()) throw ApiException.notFound("Poll not found");
        if (Boolean.TRUE.equals(x.getPayload().get("closed"))) throw ApiException.conflict("This poll is closed.");
        return x;
    }

    private PackMessage inPack(UUID meetupId, UUID messageId) {
        return messages.findById(messageId)
                .filter(x -> x.getMeetupId().equals(meetupId))
                .orElseThrow(() -> ApiException.notFound("Message not found"));
    }

    /** Sends the changed message to the other members live; returns it for me. */
    private PackMessageDto pushUpdate(Meetup m, UUID viewerId, PackMessage x) {
        Names names = new Names();
        PackMessageDto forOthers = full(x, null, names);
        rsvps.findByMeetupId(m.getId()).stream().map(MeetupRsvp::getUserId).filter(id -> !id.equals(viewerId))
                .forEach(id -> realtime.sendToUser(id,
                        new PackMessageUpdatedPush("pack_message_updated", m.getId().toString(), forOthers)));
        return full(x, viewerId, names);
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
        return toDto(x, viewerId, repliedTo, names, List.of(), List.of());
    }

    /** One message with its reactions and votes loaded. */
    private PackMessageDto full(PackMessage x, UUID viewerId, Names names) {
        PackMessage repliedTo = x.getReplyToId() == null ? null : messages.findById(x.getReplyToId()).orElse(null);
        return toDto(x, viewerId, repliedTo, names, reactions.findByMessageId(x.getId()),
                votes.findByMessageId(x.getId()));
    }

    @SuppressWarnings("unchecked")
    private static PollDto poll(PackMessage x, List<PollVote> cast) {
        if (!POLL.equals(x.getKind()) || x.getPayload() == null) return null;
        List<String> texts = (List<String>) x.getPayload().getOrDefault("options", List.of());
        List<PollOption> options = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            final int index = i;
            options.add(new PollOption(texts.get(i), cast.stream()
                    .filter(v -> v.getOption() == index).map(v -> v.getUserId().toString()).toList()));
        }
        return new PollDto(options, Boolean.TRUE.equals(x.getPayload().get("closed")));
    }

    private PackMessageDto toDto(PackMessage x, UUID viewerId, PackMessage repliedTo, Names names,
                                 List<PackMessageReaction> reacted, List<PollVote> cast) {
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
                x.getKind(), x.isDeleted(), x.getEditedAt(),
                x.isDeleted() ? Map.of()
                        : Reactions.group(reacted, PackMessageReaction::getEmoji, PackMessageReaction::getUserId),
                x.isDeleted() ? null : poll(x, cast));
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
