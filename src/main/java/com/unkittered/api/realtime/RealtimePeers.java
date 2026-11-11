package com.unkittered.api.realtime;

import com.unkittered.api.interaction.Match;
import com.unkittered.api.interaction.MatchRepository;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who a socket event may be relayed to. Kept apart from {@link RealtimeGateway}
 * (and from the chat service, which depends on the gateway) so the gateway can
 * answer inbound frames without a circular dependency.
 */
@Component
public class RealtimePeers {

    private final MatchRepository matches;
    private final UserRepository users;
    private final ProfileRepository profiles;

    public RealtimePeers(MatchRepository matches, UserRepository users, ProfileRepository profiles) {
        this.matches = matches;
        this.users = users;
        this.profiles = profiles;
    }

    /**
     * The other member of a conversation, if {@code userId} may use it: the same
     * rule as opening the chat (a member of the match, the other person not
     * suspended; a block removes the match itself).
     */
    public Optional<UUID> otherMember(UUID matchId, UUID userId) {
        return matches.findById(matchId)
                .filter(m -> m.getUserLow().equals(userId) || m.getUserHigh().equals(userId))
                .map(m -> other(m, userId))
                .filter(users::existsByIdAndSuspendedFalse);
    }

    /**
     * Who hears that {@code userId} came online or went offline: the people
     * they're matched with, and nobody at all when they keep their activity
     * private (the rule profiles use for {@code isOnline}).
     */
    public List<UUID> presenceAudience(UUID userId) {
        boolean shown = profiles.findById(userId).map(Profile::isShowActivity).orElse(false);
        if (!shown) return List.of();
        return matches.findAllForUser(userId).stream().map(m -> other(m, userId)).toList();
    }

    private static UUID other(Match m, UUID userId) {
        return m.getUserLow().equals(userId) ? m.getUserHigh() : m.getUserLow();
    }
}
