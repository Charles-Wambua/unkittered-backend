package com.unkittered.api.profile;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.interaction.LikeRepository;
import com.unkittered.api.interaction.Match;
import com.unkittered.api.interaction.MatchRepository;
import com.unkittered.api.safety.BlockRepository;
import com.unkittered.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Opens one member's profile for another, as a deck card would show it. */
@Service
public class ProfileViewService {

    static final String NOT_FOUND = "We couldn't find that profile.";

    private final ProfileRepository profiles;
    private final UserRepository users;
    private final BlockRepository blocks;
    private final MatchRepository matches;
    private final LikeRepository likes;
    private final DiscoverService discover;

    public ProfileViewService(ProfileRepository profiles, UserRepository users, BlockRepository blocks,
                              MatchRepository matches, LikeRepository likes, DiscoverService discover) {
        this.profiles = profiles;
        this.users = users;
        this.blocks = blocks;
        this.matches = matches;
        this.likes = likes;
        this.discover = discover;
    }

    /**
     * The profile as this viewer sees it. Anyone they shouldn't be able to see
     * looks exactly like someone who doesn't exist: a suspended or deleted
     * account, a block in either direction, or a paused or incognito member
     * they have no tie to.
     */
    @Transactional(readOnly = true)
    public ProfileDto view(UUID viewerId, String rawId) {
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound(NOT_FOUND);
        }
        Profile p = profiles.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (!id.equals(viewerId)) {
            if (!users.existsByIdAndSuspendedFalse(id) || blocks.existsBetween(viewerId, id)) {
                throw ApiException.notFound(NOT_FOUND);
            }
            if ((p.isPaused() || p.isIncognito()) && !visibleWhileHidden(viewerId, p)) {
                throw ApiException.notFound(NOT_FOUND);
            }
        }
        return discover.viewOf(viewerId, p);
    }

    /**
     * Matches can always open each other. An incognito member is also in the
     * deck of the people they wagged at, so those people can open the card too.
     */
    private boolean visibleWhileHidden(UUID viewerId, Profile p) {
        UUID id = p.getUserId();
        if (matches.existsByUserLowAndUserHigh(Match.low(viewerId, id), Match.high(viewerId, id))) return true;
        return !p.isPaused() && likes.existsByLikerIdAndLikeeId(id, viewerId);
    }
}
