package com.unkittered.api.subscription;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Resolves and persists the user's membership tier, and applies Boosts. */
@Service
public class SubscriptionService {

    /** How long a single Boost keeps a profile at the top of decks. */
    private static final Duration BOOST_DURATION = Duration.ofMinutes(30);

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final RedisTemplate<String, Object> redis;
    private final boolean trustClientPurchases;

    public SubscriptionService(UserRepository users, ProfileRepository profiles,
                               RedisTemplate<String, Object> redis,
                               @Value("${unkittered.billing.trust-client:false}") boolean trustClientPurchases) {
        this.users = users;
        this.profiles = profiles;
        this.redis = redis;
        this.trustClientPurchases = trustClientPurchases;
    }

    /** Result of a Boost: when it lasts until, and how many remain this month. */
    public record BoostResult(Instant boostedUntil, int boostsRemaining) { }

    /**
     * Activate a Boost for the user. Enforces the monthly allowance per tier
     * (free 0, Plus 1, Gold 5) and stamps {@code profiles.boosted_until}.
     */
    @Transactional
    public BoostResult boost(UUID userId) {
        User user = requireUser(userId);
        String tier = user.getSubscriptionTier() == null
                ? "free" : user.getSubscriptionTier().toLowerCase();
        int limit = Allowances.monthlyBoosts(tier);
        if (limit == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Boosts are a Plus & Gold perk. Upgrade to be the top profile in your area.");
        }
        String key = Allowances.monthlyBoostsKey(userId);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofDays(32));
        }
        if (count != null && count > limit) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "You've used all your Boosts this month.");
        }
        Profile p = profiles.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
        Instant until = Instant.now().plus(BOOST_DURATION);
        p.setBoostedUntil(until);
        profiles.save(p);
        int remaining = count == null ? limit - 1 : Math.max(0, limit - count.intValue());
        return new BoostResult(until, remaining);
    }

    /** What the member has left right now (read-only: spends nothing). */
    public record Allowance(String tier, Integer wagsLeftToday, int petNotesLeftThisWeek,
                            int boostsLeftThisMonth) { }

    @Transactional(readOnly = true)
    public Allowance allowanceOf(UUID userId) {
        String tier = Allowances.normalise(requireUser(userId).getSubscriptionTier());
        Integer daily = Allowances.dailyLikes(tier);
        var values = redis.opsForValue();
        return new Allowance(
                tier,
                daily == null ? null : Allowances.left(daily, values.get(Allowances.dailyLikesKey(userId))),
                Allowances.left(Allowances.weeklySuperLikes(tier),
                        values.get(Allowances.weeklySuperLikesKey(userId))),
                Allowances.left(Allowances.monthlyBoosts(tier),
                        values.get(Allowances.monthlyBoostsKey(userId))));
    }

    @Transactional(readOnly = true)
    public String tierOf(UUID userId) {
        return requireUser(userId).getSubscriptionTier();
    }

    /**
     * "Verifies" a store purchase. With no real receipt-verification provider
     * wired up, the tier is derived from the IAP product id (e.g.
     * {@code unkittered_gold_monthly} → {@code gold}). Swap the body for real
     * App Store / Play receipt validation when going live.
     */
    @Transactional
    public String verifyAndActivate(UUID userId, String productId) {
        // There's no App Store / Play receipt validation yet, so a bare product
        // id proves nothing. Only trust it where explicitly allowed (local dev).
        if (!trustClientPurchases) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "In-app purchases aren't available yet. Please try again later.");
        }
        String tier = tierFromProductId(productId);
        if ("free".equals(tier)) {
            throw ApiException.badRequest("Unrecognised product: " + productId);
        }
        User user = requireUser(userId);
        user.setSubscriptionTier(tier);
        users.save(user);
        return tier;
    }

    @Transactional
    public void cancel(UUID userId) {
        User user = requireUser(userId);
        user.setSubscriptionTier("free");
        users.save(user);
        // Incognito is a Gold perk — drop it when the membership lapses.
        profiles.findById(userId).ifPresent(p -> {
            if (p.isIncognito()) {
                p.setIncognito(false);
                profiles.save(p);
            }
        });
    }

    /** Maps an IAP product id to a tier id. Mirrors the Flutter SubscriptionPlan catalog. */
    private String tierFromProductId(String productId) {
        String id = productId.toLowerCase();
        if (id.contains("gold")) return "gold";
        if (id.contains("plus")) return "plus";
        return "free";
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Account not found"));
    }
}
