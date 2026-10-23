package com.unkittered.api.profile;

import com.unkittered.api.pet.PetDtos;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Wire representation of a profile. Field names match the Flutter
 * {@code Profile.fromJson} contract exactly.
 */
public record ProfileDto(
        String id,
        String name,
        int age,
        String imageUrl,
        String bio,
        List<String> interests,
        String location,
        double distance,
        boolean isVerified,
        List<String> galleryImages,
        String occupation,
        String education,
        List<String> pets,
        String childFreeStatement,
        List<String> lifestyleTags,
        Integer compatibility,
        boolean isOnline,
        Instant lastSeen,
        boolean showActivity,
        boolean hideDistance,
        boolean incognito,
        String cardQuote,
        String connectionMode,
        boolean openToFriends,
        /** Structured animals — the source of truth; {@code pets} is derived labels. */
        List<PetDtos.PetDto> animals,
        /** {@link PledgePath} wire value, or null. */
        String pledgePath,
        Instant pledgeRenewsAt,
        /** "none" | "child_free_verified" | "sterilised_verified". */
        String badge,
        /** "none" | "declared" (self-reported) | "verified" (medical review). */
        String sterilisationStatus,
        /** Self-declared procedure wire value, or null. */
        String sterilisationProcedure,
        /** Only meaningful on the member's own profile. */
        boolean paused
) implements Serializable {
}
