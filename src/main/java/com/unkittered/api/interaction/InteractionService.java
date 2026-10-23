package com.unkittered.api.interaction;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.subscription.Allowances;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.messaging.MatchCreatedEvent;
import com.unkittered.api.messaging.MatchEventPublisher;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InteractionService {

    /** Free-tier daily like allowance (mirrors the Flutter Entitlements). */

    private final LikeRepository likes;
    private final PassRepository passes;
    private final MatchRepository matches;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final MatchEventPublisher matchPublisher;
    private final DiscoverService discoverService;
    private final RedisTemplate<String, Object> redis;
    private final RealtimeGateway realtime;

    public InteractionService(LikeRepository likes, PassRepository passes, MatchRepository matches,
                              UserRepository users, ProfileRepository profiles, ProfileMapper mapper,
                              MatchEventPublisher matchPublisher, DiscoverService discoverService,
                              RedisTemplate<String, Object> redis, RealtimeGateway realtime) {
        this.likes = likes;
        this.passes = passes;
        this.matches = matches;
        this.users = users;
        this.profiles = profiles;
        this.mapper = mapper;
        this.matchPublisher = matchPublisher;
        this.discoverService = discoverService;
        this.redis = redis;
        this.realtime = realtime;
    }

    /** The result of a (super)like. */
    public record LikeOutcome(boolean isMatch, boolean alreadyMatched) { }

    /**
     * Records a (super)like. Wagging someone you already liked or matched
     * with (say, from their reel) is a no-op and costs nothing.
     */
    @Transactional
    public LikeOutcome like(UUID likerId, UUID likeeId, boolean superLike) {
        validateTarget(likerId, likeeId);
        if (matches.existsByUserLowAndUserHigh(Match.low(likerId, likeeId), Match.high(likerId, likeeId))) {
            return new LikeOutcome(true, true);
        }

        boolean fresh = !likes.existsByLikerIdAndLikeeId(likerId, likeeId);
        if (fresh) {
            enforceDailyLimit(likerId, superLike);
            likes.save(new Like(likerId, likeeId, superLike));
        }

        boolean isMatch = false;
        // Mutual like? The other person already liked us.
        if (likes.existsByLikerIdAndLikeeId(likeeId, likerId)) {
            isMatch = createMatchIfAbsent(likerId, likeeId, superLike);
        } else if (fresh) {
            // A bare ping so the "N wagged at you" count updates live. It names
            // nobody: who it was stays behind the who-wagged entitlement.
            realtime.sendToUser(likeeId, Map.of("type", "wag"));
        }

        discoverService.evict(likerId);
        return new LikeOutcome(isMatch, false);
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
    /**
     * Who liked the viewer. Members whose tier includes it get real profiles;
     * everyone else gets anonymous teasers, so nothing identifying is ever sent
     * just to be blurred on the phone.
     */
    public List<ProfileDto> whoLikedMe(UUID viewerId) {
        boolean entitled = users.findById(viewerId)
                .map(u -> Allowances.seesWhoLiked(u.getSubscriptionTier()))
                .orElse(false);
        return profiles.findWhoLiked(viewerId).stream()
                .map(p -> entitled ? mapper.toDto(p, null, 10.0) : mapper.teaser(p))
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

    /**
     * Server-side quota enforcement — the authoritative backstop behind the
     * client's gating. Normal likes: free tier is capped daily, paid tiers are
     * unlimited. Super-likes: a weekly allowance that scales with tier.
     */
    private void enforceDailyLimit(UUID likerId, boolean superLike) {
        User user = users.findById(likerId)
                .orElseThrow(() -> ApiException.unauthorized("Account not found"));
        String tier = user.getSubscriptionTier() == null
                ? "free" : user.getSubscriptionTier().toLowerCase();

        if (superLike) {
            enforceSuperLikeWeekly(likerId, tier);
            return;
        }
        if (!"free".equals(tier)) {
            return; // paid tiers have unlimited likes
        }
        String key = Allowances.dailyLikesKey(likerId);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofDays(2));
        }
        if (count != null && count > Allowances.FREE_DAILY_LIKES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily like limit reached. Upgrade to Unkittered Plus for unlimited likes.");
        }
    }

    /** Weekly super-like allowance: free 1, Plus 5, Gold 10. */
    private void enforceSuperLikeWeekly(UUID likerId, String tier) {
        int limit = Allowances.weeklySuperLikes(tier);
        String key = Allowances.weeklySuperLikesKey(likerId);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofDays(8));
        }
        if (count != null && count > limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've used all your Super Likes this week. Upgrade for more.");
        }
    }
}
