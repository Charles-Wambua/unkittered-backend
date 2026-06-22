package com.unkittered.api.me;

import jakarta.validation.constraints.Min;

import java.util.List;

/** Request payloads for the "current user" self-service endpoints. */
public final class MeDtos {

    private MeDtos() { }

    /**
     * Partial update of the signed-in user's public profile. Every field is
     * optional — only non-null values are applied — so the same payload serves
     * both the onboarding wizard and the Edit Profile screen. Field names mirror
     * the Flutter {@code Profile.toJson} contract.
     */
    public record UpdateProfileRequest(
            String name,
            @Min(0) Integer age,
            String imageUrl,
            String bio,
            List<String> interests,
            String location,
            Boolean isVerified,
            List<String> galleryImages,
            String occupation,
            String education,
            List<String> pets,
            String childFreeStatement,
            List<String> lifestyleTags,
            String cardQuote,
            Boolean showActivity,
            Boolean hideDistance,
            Boolean incognito,
            String connectionMode) {
    }
}
