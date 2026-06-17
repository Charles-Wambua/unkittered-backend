package com.unkittered.api.profile;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class ProfileMapper {

    /** A user counts as "online" if active within this window. */
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);

    /**
     * Map for another user's eyes — presence is redacted when the subject has
     * turned activity sharing off.
     *
     * @param compatibility 0-100 score computed for the viewing user, or null
     * @param distanceKm    distance from the viewer in km
     */
    public ProfileDto toDto(Profile p, Integer compatibility, double distanceKm) {
        return toDto(p, compatibility, distanceKm, true);
    }

    /**
     * @param redactHiddenPresence when true and the subject hides their activity,
     *                             online/lastSeen are stripped. Pass false when the
     *                             user is viewing their own profile.
     */
    public ProfileDto toDto(Profile p, Integer compatibility, double distanceKm,
                            boolean redactHiddenPresence) {
        boolean share = p.isShowActivity();
        Instant lastActive = p.getLastActiveAt();
        boolean online = lastActive != null
                && Duration.between(lastActive, Instant.now()).compareTo(ONLINE_WINDOW) < 0;
        Instant lastSeen = lastActive;

        if (redactHiddenPresence && !share) {
            online = false;
            lastSeen = null;
        }

        // Distance is redacted the same way: when the subject hides it (and this
        // isn't their own view), emit a sentinel the client renders as "hidden".
        boolean hideDistance = p.isHideDistance();
        double distance = (redactHiddenPresence && hideDistance) ? -1.0 : distanceKm;

        // Never reveal that someone is browsing incognito to other users.
        boolean incognito = redactHiddenPresence ? false : p.isIncognito();

        return new ProfileDto(
                p.getUserId().toString(),
                p.getName(),
                p.getAge(),
                p.getImageUrl(),
                p.getBio(),
                List.copyOf(p.getInterests()),
                p.getLocation(),
                distance,
                p.isVerified(),
                List.copyOf(p.getGalleryImages()),
                p.getOccupation(),
                p.getEducation(),
                List.copyOf(p.getPets()),
                p.getChildFreeStatement(),
                List.copyOf(p.getLifestyleTags()),
                compatibility,
                online,
                lastSeen,
                share,
                hideDistance,
                incognito,
                p.getCardQuote()
        );
    }
}
