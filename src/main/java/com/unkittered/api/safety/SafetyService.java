package com.unkittered.api.safety;

import com.unkittered.api.chat.MessageRepository;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.interaction.Match;
import com.unkittered.api.interaction.MatchRepository;
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

    public SafetyService(BlockRepository blocks, ReportRepository reports, MatchRepository matches,
                         MessageRepository messages, ProfileRepository profiles, UserRepository users,
                         ProfileMapper mapper, DiscoverService discoverService) {
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
        if (!profiles.existsById(blockedId)) {
            throw ApiException.notFound("Profile not found");
        }
        if (!blocks.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            blocks.save(new Block(blockerId, blockedId));
        }
        removeMatchBetween(blockerId, blockedId);
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
                .map(p -> mapper.toDto(p, null, 0.0))
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
        messages.deleteByMatchId(matchId);
        matches.delete(m);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void removeMatchBetween(UUID a, UUID b) {
        matches.findByUserLowAndUserHigh(Match.low(a, b), Match.high(a, b)).ifPresent(m -> {
            messages.deleteByMatchId(m.getId());
            matches.delete(m);
        });
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
