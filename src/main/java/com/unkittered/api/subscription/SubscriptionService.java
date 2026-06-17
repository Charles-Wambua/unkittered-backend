package com.unkittered.api.subscription;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Resolves and persists the user's membership tier. */
@Service
public class SubscriptionService {

    private final UserRepository users;
    private final ProfileRepository profiles;

    public SubscriptionService(UserRepository users, ProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
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
