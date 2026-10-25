package com.unkittered.api.chat;

import com.unkittered.api.chat.ChatDtos.ConversationDto;
import com.unkittered.api.chat.ChatDtos.MatchDto;
import com.unkittered.api.chat.ChatDtos.MessageDto;
import com.unkittered.api.chat.ChatDtos.SendMessageRequest;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.pet.PetRepository;
import com.unkittered.api.interaction.Match;
import com.unkittered.api.interaction.MatchRepository;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.push.PushService;
import com.unkittered.api.realtime.RealtimeEvents.MessagePush;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.subscription.SubscriptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Matches and 1:1 conversations for the signed-in user. */
@Service
public class ChatService {

    private final MatchRepository matches;
    private final MessageRepository messages;
    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final RealtimeGateway realtime;
    private final SubscriptionService subscriptions;
    private final PushService push;
    private final PetRepository pets;
    private final PlanFeedbackRepository feedback;
    private final ChatClearRepository clears;
    /** Public base our storage serves media from (may be empty = relative URLs). */
    private final String mediaBase;

    public ChatService(MatchRepository matches, MessageRepository messages,
                       ProfileRepository profiles, ProfileMapper mapper,
                       RealtimeGateway realtime, SubscriptionService subscriptions,
                       PushService push, PetRepository pets, PlanFeedbackRepository feedback,
                       ChatClearRepository clears,
                       @Value("${unkittered.storage.public-base-url:}") String mediaBase) {
        this.feedback = feedback;
        this.clears = clears;
        this.mediaBase = mediaBase.replaceAll("/+$", "");
        this.matches = matches;
        this.messages = messages;
        this.profiles = profiles;
        this.mapper = mapper;
        this.realtime = realtime;
        this.subscriptions = subscriptions;
        this.push = push;
        this.pets = pets;
    }

    @Transactional(readOnly = true)
    public List<MatchDto> matchesFor(UUID viewerId) {
        return matches.findAllForUser(viewerId).stream()
                .map(m -> new MatchDto(
                        m.getId().toString(),
                        otherProfile(m, viewerId),
                        m.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> conversationsFor(UUID viewerId) {
        return matches.findAllForUser(viewerId).stream()
                .map(m -> {
                    // "Delete chat" hides everything up to when it was cleared.
                    Instant cleared = clearedAt(viewerId, m.getId());
                    var last = messages.findFirstByMatchIdOrderByCreatedAtDesc(m.getId())
                            .filter(msg -> cleared == null || msg.getCreatedAt().isAfter(cleared));
                    long unread = cleared == null
                            ? messages.countByMatchIdAndSenderIdNotAndReadAtIsNull(m.getId(), viewerId)
                            : messages.countByMatchIdAndSenderIdNotAndReadAtIsNullAndCreatedAtAfter(
                                    m.getId(), viewerId, cleared);
                    var plan = latestPlan(m.getId());
                    return new ConversationDto(
                            m.getId().toString(),
                            otherProfile(m, viewerId),
                            last.map(msg -> previewFor(msg, viewerId)).orElse(null),
                            last.map(Message::getCreatedAt).orElse(m.getCreatedAt()),
                            unread,
                            last.map(msg -> RichMessages.Kind.fromWire(msg.getKind())).orElse(null),
                            plan.map(Map.Entry::getKey).orElse(null),
                            plan.map(Map.Entry::getValue).orElse(null),
                            last.map(msg -> msg.getSenderId().equals(viewerId)).orElse(false));
                })
                // Most recent activity first.
                .sorted(Comparator.comparing(ConversationDto::lastMessageAt).reversed())
                .toList();
    }

    @Transactional
    public List<MessageDto> messages(UUID viewerId, UUID matchId) {
        requireMembership(matchId, viewerId);
        messages.markRead(matchId, viewerId, Instant.now());
        Instant cleared = clearedAt(viewerId, matchId);
        List<Message> all = messages.findByMatchIdOrderByCreatedAtAsc(matchId).stream()
                .filter(m -> cleared == null || m.getCreatedAt().isAfter(cleared))
                .toList();
        Map<UUID, Message> byId = new java.util.HashMap<>();
        all.forEach(m -> byId.put(m.getId(), m));
        return all.stream()
                .map(m -> MessageDto.from(m, viewerId,
                        m.getReplyToId() == null ? null : byId.get(m.getReplyToId())))
                .toList();
    }

    /**
     * Drops a gift into the chat between two matched members (no-op if they
     * aren't matched). Only the wallet calls this, after charging the sender.
     */
    @Transactional
    public void postGift(UUID fromId, UUID toId, String giftId, String giftName, String emoji) {
        matches.findByUserLowAndUserHigh(Match.low(fromId, toId), Match.high(fromId, toId)).ifPresent(match -> {
            var prepared = new RichMessages.Prepared(RichMessages.Kind.GIFT,
                    emoji + " Sent you a " + giftName,
                    Map.of("giftId", giftId, "name", giftName, "emoji", emoji));
            Message saved = messages.save(new Message(match.getId(), fromId, prepared));
            realtime.sendToUser(toId, MessagePush.of(saved, null));
        });
    }

    /** Date ideas for a match: Gold gets three, everyone else one (+ how many more). */
    public record ConciergeResult(List<DateConcierge.Plan> plans, int more) { }

    @Transactional(readOnly = true)
    public ConciergeResult datePlans(UUID viewerId, UUID matchId) {
        Match match = requireMembership(matchId, viewerId);
        UUID otherId = otherUserId(match, viewerId);
        Profile me = profiles.findById(viewerId).orElseThrow(() -> ApiException.notFound("Profile not found"));
        Profile them = profiles.findById(otherId).orElseThrow(() -> ApiException.notFound("Profile not found"));
        boolean bothPets = !pets.findByOwnerIdOrderByPositionAsc(viewerId).isEmpty()
                && !pets.findByOwnerIdOrderByPositionAsc(otherId).isEmpty();
        String area = them.getLocation() != null && !them.getLocation().isBlank() ? them.getLocation() : me.getLocation();
        var all = DateConcierge.suggest(new DateConcierge.Input(
                them.getName() == null || them.getName().isBlank() ? "They" : them.getName(),
                area, List.copyOf(me.getInterests()), List.copyOf(them.getInterests()), bothPets),
                java.time.ZonedDateTime.now(DateConcierge.ZONE), 3);
        boolean gold = "gold".equalsIgnoreCase(subscriptions.tierOf(viewerId));
        int shown = gold ? all.size() : Math.min(1, all.size());
        return new ConciergeResult(all.subList(0, shown), all.size() - shown);
    }

    @Transactional
    public MessageDto send(UUID viewerId, UUID matchId, SendMessageRequest req) {
        Match match = requireMembership(matchId, viewerId);
        RichMessages.Prepared prepared = RichMessages.prepare(
                req.kindOrText(), req.text(), req.payload(), viewerId,
                planId -> findPlan(matchId, planId),
                this::isOwnMediaUrl,
                Instant.now());

        // Safety filter — reject profanity always; reject contact info unless Gold.
        // Applies to every human-written part of the message, not just the body.
        boolean isGold = "gold".equalsIgnoreCase(subscriptions.tierOf(viewerId));
        for (String part : prepared.moderatedText()) {
            MessageModeration.Flag flag = MessageModeration.check(part, isGold);
            if (flag != MessageModeration.Flag.CLEAN) {
                throw ApiException.badRequest(MessageModeration.reason(flag));
            }
        }
        Message repliedTo = replyTarget(matchId, req.replyToId());
        Message toSave = new Message(matchId, viewerId, prepared);
        if (repliedTo != null) toSave.setReplyToId(repliedTo.getId());
        Message saved = messages.save(toSave);

        // Push the message live to the recipient's open sessions. The sender
        // already gets it as this method's HTTP response, so we skip echoing.
        UUID recipient = otherUserId(match, viewerId);
        realtime.sendToUser(recipient, MessagePush.of(saved, repliedTo));

        // Mobile push for when their app is closed.
        String senderName = profiles.findById(viewerId).map(Profile::getName)
                .filter(n -> !n.isBlank()).orElse("New message");
        String body = saved.getBody();
        String preview = body.length() > 120 ? body.substring(0, 117) + "…" : body;
        push.sendToUser(recipient, senderName, preview,
                Map.of("type", "message", "matchId", matchId.toString(),
                        "senderId", viewerId.toString(), "kind", saved.getKind()));
        return MessageDto.from(saved, viewerId, repliedTo);
    }

    /** The message being replied to; it must be in the same conversation. */
    private Message replyTarget(UUID matchId, String replyToId) {
        if (replyToId == null || replyToId.isBlank()) return null;
        UUID id;
        try {
            id = UUID.fromString(replyToId);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown message to reply to");
        }
        return messages.findById(id)
                .filter(m -> m.getMatchId().equals(matchId))
                .orElseThrow(() -> ApiException.badRequest("Unknown message to reply to"));
    }

    /** A plan message in this conversation, by its payload id. */
    private RichMessages.PlanRef findPlan(UUID matchId, String planId) {
        return messages.findByMatchIdAndKindOrderByCreatedAtAsc(matchId, RichMessages.Kind.PLAN.wire()).stream()
                .filter(m -> m.getPayload() != null && planId.equals(m.getPayload().get("id")))
                .findFirst()
                .map(m -> new RichMessages.PlanRef(m.getSenderId(), planStart(m.getPayload())))
                .orElse(null);
    }

    private static Instant planStart(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("startsAt");
        try {
            return raw == null ? null : Instant.parse(raw.toString());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /**
     * The chat-list preview, worded for the viewer. Gifts are stored as the
     * recipient reads them ("🌹 Sent you a Rose"), so the sender sees
     * "🌹 You sent a Rose" instead.
     */
    static String previewFor(Message m, UUID viewerId) {
        if (m.isDeleted()) return ChatDtos.DELETED_TEXT;
        if (RichMessages.Kind.GIFT.wire().equals(m.getKind()) && m.getSenderId().equals(viewerId)
                && m.getPayload() != null) {
            return m.getPayload().get("emoji") + " You sent a " + m.getPayload().get("name");
        }
        return m.getBody();
    }

    /** Deletes my message for both of us (Gold). */
    @Transactional
    public void deleteMessage(UUID viewerId, UUID matchId, UUID messageId) {
        Match match = requireMembership(matchId, viewerId);
        Message m = messages.findById(messageId)
                .filter(x -> x.getMatchId().equals(matchId))
                .orElseThrow(() -> ApiException.notFound("Message not found"));
        if (!m.getSenderId().equals(viewerId)) throw ApiException.forbidden("You can only delete your own messages.");
        requireGold(viewerId, "Deleting messages is a Gold feature.");
        if (m.isDeleted()) return;
        m.delete();
        realtime.sendToUser(otherUserId(match, viewerId), Map.of(
                "type", "message_deleted", "matchId", matchId.toString(), "id", messageId.toString()));
    }

    /** "Delete chat": clears this conversation's history for me only (Gold). */
    @Transactional
    public void clearConversation(UUID viewerId, UUID matchId) {
        requireMembership(matchId, viewerId);
        requireGold(viewerId, "Deleting conversations is a Gold feature.");
        Instant now = Instant.now();
        clears.findById(new ChatClear.Key(viewerId, matchId)).ifPresentOrElse(
                c -> c.setClearedAt(now),
                () -> clears.save(new ChatClear(viewerId, matchId, now)));
    }

    private Instant clearedAt(UUID viewerId, UUID matchId) {
        return clears.findById(new ChatClear.Key(viewerId, matchId)).map(ChatClear::getClearedAt).orElse(null);
    }

    private void requireGold(UUID userId, String message) {
        if (!"gold".equalsIgnoreCase(subscriptions.tierOf(userId))) throw ApiException.forbidden(message);
    }

    /** My private feedback on this chat's past dates: plan id → good | bad. */
    @Transactional(readOnly = true)
    public Map<String, String> planFeedback(UUID viewerId, UUID matchId) {
        requireMembership(matchId, viewerId);
        Map<String, String> out = new java.util.HashMap<>();
        feedback.findByUserIdAndMatchId(viewerId, matchId).forEach(f -> out.put(f.getPlanId(), f.getRating()));
        return out;
    }

    /** "How was it?" — only for an accepted plan whose time is over. */
    @Transactional
    public Map<String, String> ratePlan(UUID viewerId, UUID matchId, String planId, String rating) {
        requireMembership(matchId, viewerId);
        if (!"good".equals(rating) && !"bad".equals(rating)) {
            throw ApiException.badRequest("rating must be good or bad");
        }
        Message plan = messages.findByMatchIdAndKindOrderByCreatedAtAsc(matchId, RichMessages.Kind.PLAN.wire()).stream()
                .filter(m -> m.getPayload() != null && planId.equals(m.getPayload().get("id")))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("That plan isn't in this conversation"));
        boolean accepted = messages.findByMatchIdAndKindOrderByCreatedAtAsc(matchId, RichMessages.Kind.PLAN_RESPONSE.wire())
                .stream().anyMatch(r -> r.getPayload() != null && planId.equals(r.getPayload().get("planId"))
                        && Boolean.TRUE.equals(r.getPayload().get("accepted")));
        if (!accepted) throw ApiException.badRequest("Only confirmed plans can be rated");
        Instant start = planStart(plan.getPayload());
        Object minutes = plan.getPayload().get("durationMinutes");
        Instant end = start == null ? null
                : start.plus(java.time.Duration.ofMinutes(minutes instanceof Number n ? n.longValue() : 90));
        if (end == null || Instant.now().isBefore(end)) {
            throw ApiException.badRequest("You can say how it went once the date is over");
        }
        feedback.findById(new PlanFeedback.Key(viewerId, planId)).ifPresentOrElse(
                f -> f.setRating(rating),
                () -> feedback.save(new PlanFeedback(viewerId, matchId, planId, rating)));
        return planFeedback(viewerId, matchId);
    }

    /** The latest plan in a conversation and whether it was answered. */
    private Optional<Map.Entry<Map<String, Object>, String>> latestPlan(UUID matchId) {
        return messages.findFirstByMatchIdAndKindOrderByCreatedAtDesc(matchId, RichMessages.Kind.PLAN.wire())
                .filter(m -> m.getPayload() != null)
                .map(plan -> {
                    Object id = plan.getPayload().get("id");
                    String status = "proposed";
                    for (Message r : messages.findByMatchIdAndKindOrderByCreatedAtAsc(
                            matchId, RichMessages.Kind.PLAN_RESPONSE.wire())) {
                        if (r.getPayload() != null && id.equals(r.getPayload().get("planId"))) {
                            status = Boolean.TRUE.equals(r.getPayload().get("accepted")) ? "accepted" : "declined";
                        }
                    }
                    return Map.entry(plan.getPayload(), status);
                });
    }

    private boolean isOwnMediaUrl(String url) {
        return !url.contains("..")
                && (url.startsWith(mediaBase + "/files/") || url.startsWith("/files/"));
    }

    // ── internals ────────────────────────────────────────────────────────────

    private Match requireMembership(UUID matchId, UUID viewerId) {
        Match m = matches.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (!m.getUserLow().equals(viewerId) && !m.getUserHigh().equals(viewerId)) {
            // Don't reveal that the match exists to non-participants.
            throw ApiException.notFound("Conversation not found");
        }
        return m;
    }

    private UUID otherUserId(Match m, UUID viewerId) {
        return m.getUserLow().equals(viewerId) ? m.getUserHigh() : m.getUserLow();
    }

    private ProfileDto otherProfile(Match m, UUID viewerId) {
        UUID otherId = otherUserId(m, viewerId);
        Profile p = profiles.findById(otherId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
        return mapper.toDto(p, null, 10.0);
    }
}
