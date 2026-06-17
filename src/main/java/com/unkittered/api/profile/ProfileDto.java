package com.unkittered.api.profile;

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
        String cardQuote
) implements Serializable {
}
