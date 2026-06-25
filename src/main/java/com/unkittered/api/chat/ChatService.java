package com.unkittered.api.chat;

import com.unkittered.api.chat.ChatDtos.ConversationDto;
import com.unkittered.api.chat.ChatDtos.MatchDto;
import com.unkittered.api.chat.ChatDtos.MessageDto;
import com.unkittered.api.common.ApiException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

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

    public ChatService(MatchRepository matches, MessageRepository messages,
                       ProfileRepository profiles, ProfileMapper mapper,
                       RealtimeGateway realtime, SubscriptionService subscriptions,
                       PushService push) {
        this.matches = matches;
        this.messages = messages;
        this.profiles = profiles;
        this.mapper = mapper;
        this.realtime = realtime;
        this.subscriptions = subscriptions;
        this.push = push;
    }

    @Transactional(readOnly = true)
    public List<MatchDto> matchesFor(UUID viewerId) {
        List<Match> userMatches = matches.findAllForUser(viewerId);
        if (userMatches.isEmpty()) return List.of();

        Set<UUID> otherIds = userMatches.stream()
                .map(m -> otherUserId(m, viewerId))
                .collect(java.util.stream.Collectors.toSet());

        Map<UUID, ProfileDto> byUserId = StreamSupport.stream(profiles.findAllById(otherIds).spliterator(), false)
                .map(p -> mapper.toDto(p, null, 10.0))
                .collect(java.util.stream.Collectors.toMap(ProfileDto::id, p -> p));

        return userMatches.stream()
                .map(m -> new MatchDto(
                        m.getId().toString(),
                        byUserId.get(otherUserId(m, viewerId)),
                        m.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> conversationsFor(UUID viewerId) {
        List<Match> userMatches = matches.findAllForUser(viewerId);
        if (userMatches.isEmpty()) return List.of();

        Set<UUID> otherIds = userMatches.stream()
                .map(m -> otherUserId(m, viewerId))
                .collect(java.util.stream.Collectors.toSet());

        Map<UUID, ProfileDto> byUserId = StreamSupport.stream(profiles.findAllById(otherIds).spliterator(), false)
                .map(p -> mapper.toDto(p, null, 10.0))
                .collect(java.util.stream.Collectors.toMap(ProfileDto::id, p -> p));

        Map<UUID, MessageRepository.ConversationSummary> summaries = messages.findConversationSummaries(viewerId).stream()
                .collect(java.util.stream.Collectors.toMap(MessageRepository.ConversationSummary::getMatchId, s -> s));

        return userMatches.stream()
                .map(m -> {
                    var summary = summaries.get(m.getId());
                    ProfileDto profile = byUserId.get(otherUserId(m, viewerId));
                    return new ConversationDto(
                            m.getId().toString(),
                            profile,
                            summary == null ? null : summary.getLastMessage(),
                            summary == null ? m.getCreatedAt() : summary.getLastMessageAt(),
                            summary == null ? 0 : summary.getUnreadCount());
                })
                .sorted(Comparator.comparing(ConversationDto::lastMessageAt).reversed())
                .toList();
    }

    @Transactional
    public List<MessageDto> messages(UUID viewerId, UUID matchId) {
        requireMembership(matchId, viewerId);
        messages.markRead(matchId, viewerId, Instant.now());
        return messages.findByMatchIdOrderByCreatedAtAsc(matchId).stream()
                .map(m -> MessageDto.from(m, viewerId))
                .toList();
    }

    @Transactional
    public MessageDto send(UUID viewerId, UUID matchId, String text) {
        Match match = requireMembership(matchId, viewerId);

        // Safety filter — reject profanity always; reject contact info unless Gold.
        boolean isGold = "gold".equalsIgnoreCase(subscriptions.tierOf(viewerId));
        MessageModeration.Flag flag = MessageModeration.check(text, isGold);
        if (flag != MessageModeration.Flag.CLEAN) {
            throw ApiException.badRequest(MessageModeration.reason(flag));
        }

        Message saved = messages.save(new Message(matchId, viewerId, text.trim()));

        // Push the message live to the recipient's open sessions. The sender
        // already gets it as this method's HTTP response, so we skip echoing.
        UUID recipient = otherUserId(match, viewerId);
        realtime.sendToUser(recipient, MessagePush.of(saved));

        // Mobile push for when their app is closed.
        String senderName = profiles.findById(viewerId).map(Profile::getName)
                .filter(n -> !n.isBlank()).orElse("New message");
        String preview = text.length() > 120 ? text.substring(0, 117) + "…" : text;
        push.sendToUser(recipient, senderName, preview,
                Map.of("type", "message", "matchId", matchId.toString(),
                        "senderId", viewerId.toString()));

        return MessageDto.from(saved, viewerId);
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
