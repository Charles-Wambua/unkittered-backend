package com.unkittered.api.safety;

import com.unkittered.api.chat.MessageRepository;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.interaction.Match;
import com.unkittered.api.interaction.MatchRepository;
import com.unkittered.api.interaction.LikeRepository;
import com.unkittered.api.chat.OfficialChatService;
import com.unkittered.api.interaction.Pass;
import org.springframework.jdbc.core.JdbcTemplate;
import com.unkittered.api.interaction.PassRepository;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Block, unblock, report, and unmatch — the user-safety operations. */
@Service
public class SafetyService {

    private final BlockRepository blocks;
    private final ReportRepository reports;
    private final MatchRepository matches;
    private final MessageRepository messages;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final ProfileMapper mapper;
    private final DiscoverService discoverService;
    private final LikeRepository likes;
    private final PassRepository passes;
    private final JdbcTemplate jdbc;
    private final OfficialChatService officialChat;

    /** The Unkittered team chat stays, like a support line. */
    static final String CANT_REMOVE_OFFICIAL = "The Unkittered team chat can't be removed. To stop news and tips, "
            + "turn off \"Reminders, tips & news\" in Settings.";

    public SafetyService(BlockRepository blocks, ReportRepository reports, MatchRepository matches,
                         MessageRepository messages, ProfileRepository profiles, UserRepository users,
                         ProfileMapper mapper, DiscoverService discoverService, LikeRepository likes,
                         PassRepository passes, JdbcTemplate jdbc, OfficialChatService officialChat) {
        this.officialChat = officialChat;
        this.jdbc = jdbc;
        this.passes = passes;
        this.likes = likes;
        this.blocks = blocks;
        this.reports = reports;
        this.matches = matches;
        this.messages = messages;
        this.profiles = profiles;
        this.users = users;
        this.mapper = mapper;
        this.discoverService = discoverService;
    }

    /** Block a user: drops any match/conversation between them and hides each from the other. */
    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        requireOther(blockerId, blockedId);
        if (officialChat.isOfficial(blockedId)) throw ApiException.badRequest(CANT_REMOVE_OFFICIAL);
        if (!profiles.existsById(blockedId)) {
            throw ApiException.notFound("Profile not found");
        }
        if (!blocks.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            blocks.save(new Block(blockerId, blockedId));
        }
        removeMatchBetween(blockerId, blockedId);
        // Neither stays in a pack the other is hosting (upcoming or happening now),
        // so the chat, meeting point and arrivals are closed to them.
        jdbc.update("""
                DELETE FROM meetup_rsvps r USING meetups m
                WHERE r.meetup_id = m.id AND m.starts_at + make_interval(mins => m.duration_minutes) > now()
                  AND ((m.host_id = ? AND r.user_id = ?) OR (m.host_id = ? AND r.user_id = ?))
                """, blockerId, blockedId, blockedId, blockerId);
        evictBoth(blockerId, blockedId);
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        blocks.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
        evictBoth(blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public List<ProfileDto> blockedProfiles(UUID blockerId) {
        return blocks.findBlockedIds(blockerId).stream()
                .map(profiles::findById)
                .flatMap(java.util.Optional::stream)
                .map(p -> mapper.toDto(p, null, DiscoverService.UNKNOWN_DISTANCE))
                .toList();
    }

    @Transactional
    public void report(UUID reporterId, UUID reportedId, String reason, String details) {
        requireOther(reporterId, reportedId);
        if (!users.existsById(reportedId)) {
            throw ApiException.notFound("User not found");
        }
        reports.save(new Report(reporterId, reportedId, reason.trim(),
                details == null || details.isBlank() ? null : details.trim()));
    }

    /** Dissolve a match the caller is part of, deleting its messages. */
    @Transactional
    public void unmatch(UUID userId, UUID matchId) {
        Match m = matches.findById(matchId)
                .orElseThrow(() -> ApiException.notFound("Match not found"));
        if (!m.getUserLow().equals(userId) && !m.getUserHigh().equals(userId)) {
            throw ApiException.notFound("Match not found");
        }
        if (officialChat.isOfficial(m.getUserLow().equals(userId) ? m.getUserHigh() : m.getUserLow())) {
            throw ApiException.badRequest(CANT_REMOVE_OFFICIAL);
        }
        messages.deleteByMatchId(matchId);
        matches.delete(m);
        // Otherwise one wag from either of them would bring the match straight back.
        likes.deleteBetween(m.getUserLow(), m.getUserHigh());
        // ...and they shouldn't turn up in the deck of the person who ended it.
        UUID other = m.getUserLow().equals(userId) ? m.getUserHigh() : m.getUserLow();
        if (!passes.existsByPasserIdAndPasseeId(userId, other)) passes.save(new Pass(userId, other));
        evictBoth(userId, other);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void removeMatchBetween(UUID a, UUID b) {
        matches.findByUserLowAndUserHigh(Match.low(a, b), Match.high(a, b)).ifPresent(m -> {
            messages.deleteByMatchId(m.getId());
            matches.delete(m);
        });
        likes.deleteBetween(a, b);
    }

    private void requireOther(UUID actor, UUID target) {
        if (actor.equals(target)) {
            throw ApiException.badRequest("You cannot do that to your own profile");
        }
    }

    private void evictBoth(UUID a, UUID b) {
        discoverService.evict(a);
        discoverService.evict(b);
    }
}
