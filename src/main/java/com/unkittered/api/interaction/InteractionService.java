package com.unkittered.api.interaction;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.messaging.MatchCreatedEvent;
import com.unkittered.api.messaging.MatchEventPublisher;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class InteractionService {

    /** Free-tier daily like allowance (mirrors the Flutter Entitlements). */
    private static final int FREE_DAILY_LIKES = 15;

    private final LikeRepository likes;
    private final PassRepository passes;
    private final MatchRepository matches;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final MatchEventPublisher matchPublisher;
    private final DiscoverService discoverService;
    private final RedisTemplate<String, Object> redis;

    public InteractionService(LikeRepository likes, PassRepository passes, MatchRepository matches,
                              UserRepository users, ProfileRepository profiles, ProfileMapper mapper,
                              MatchEventPublisher matchPublisher, DiscoverService discoverService,
                              RedisTemplate<String, Object> redis) {
        this.likes = likes;
        this.passes = passes;
        this.matches = matches;
        this.users = users;
        this.profiles = profiles;
        this.mapper = mapper;
        this.matchPublisher = matchPublisher;
        this.discoverService = discoverService;
        this.redis = redis;
    }

    /** Records a (super)like. Returns true if it produced a mutual match. */
    @Transactional
    public boolean like(UUID likerId, UUID likeeId, boolean superLike) {
        validateTarget(likerId, likeeId);
        enforceDailyLimit(likerId, superLike);

        if (!likes.existsByLikerIdAndLikeeId(likerId, likeeId)) {
            likes.save(new Like(likerId, likeeId, superLike));
        }

        boolean isMatch = false;
        // Mutual like? The other person already liked us.
        if (likes.existsByLikerIdAndLikeeId(likeeId, likerId)) {
            isMatch = createMatchIfAbsent(likerId, likeeId, superLike);
        }

        discoverService.evict(likerId);
        return isMatch;
    }

    @Transactional
    public void pass(UUID passerId, UUID passeeId) {
        validateTarget(passerId, passeeId);
        if (!passes.existsByPasserIdAndPasseeId(passerId, passeeId)) {
            passes.save(new Pass(passerId, passeeId));
        }
        discoverService.evict(passerId);
    }

    @Transactional(readOnly = true)
    public List<ProfileDto> whoLikedMe(UUID viewerId) {
        return profiles.findWhoLiked(viewerId).stream()
                .map(p -> mapper.toDto(p, null, 10.0))
                .toList();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private boolean createMatchIfAbsent(UUID a, UUID b, boolean superLike) {
        UUID low = Match.low(a, b);
        UUID high = Match.high(a, b);
        if (matches.existsByUserLowAndUserHigh(low, high)) {
            return true;
        }
        Match match = matches.save(Match.of(a, b));
        discoverService.evict(b);
        matchPublisher.publish(new MatchCreatedEvent(
                match.getId(), a, b, superLike, match.getCreatedAt()));
        return true;
    }

    private void validateTarget(UUID actor, UUID target) {
        if (actor.equals(target)) {
            throw ApiException.badRequest("You cannot interact with your own profile");
        }
        if (!profiles.existsById(target)) {
            throw ApiException.notFound("Profile not found");
        }
    }

    /** Redis-backed per-day counter; only enforced for free-tier users on normal likes. */
    private void enforceDailyLimit(UUID likerId, boolean superLike) {
        if (superLike) return; // super-likes have their own (weekly) allowance — TODO
        User user = users.findById(likerId)
                .orElseThrow(() -> ApiException.unauthorized("Account not found"));
        if (!"free".equalsIgnoreCase(user.getSubscriptionTier())) {
            return; // paid tiers have unlimited likes
        }

        String day = LocalDate.now(ZoneOffset.UTC).toString();
        String key = "likes:daily:" + likerId + ":" + day;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofDays(2));
        }
        if (count != null && count > FREE_DAILY_LIKES) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Daily like limit reached. Upgrade to Unkittered Plus for unlimited likes.");
        }
    }
}
