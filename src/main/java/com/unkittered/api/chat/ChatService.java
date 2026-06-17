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
import com.unkittered.api.realtime.RealtimeEvents.MessagePush;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.subscription.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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

    public ChatService(MatchRepository matches, MessageRepository messages,
                       ProfileRepository profiles, ProfileMapper mapper,
                       RealtimeGateway realtime, SubscriptionService subscriptions) {
        this.matches = matches;
        this.messages = messages;
        this.profiles = profiles;
        this.mapper = mapper;
        this.realtime = realtime;
        this.subscriptions = subscriptions;
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
                    var last = messages.findFirstByMatchIdOrderByCreatedAtDesc(m.getId());
                    long unread = messages.countByMatchIdAndSenderIdNotAndReadAtIsNull(m.getId(), viewerId);
                    return new ConversationDto(
                            m.getId().toString(),
                            otherProfile(m, viewerId),
                            last.map(Message::getBody).orElse(null),
                            last.map(Message::getCreatedAt).orElse(m.getCreatedAt()),
                            unread);
                })
                // Most recent activity first.
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
        realtime.sendToUser(otherUserId(match, viewerId), MessagePush.of(saved));

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
