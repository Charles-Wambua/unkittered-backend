package com.unkittered.api.discover;

import java.util.Collection;
import java.util.Set;

/**
 * Whether a candidate passes a viewer's discovery filters. Pure, so the rules
 * are unit-tested without a database.
 */
public final class DiscoveryFilter {
    private DiscoveryFilter() { }

    /** The facts about a candidate that filters look at. */
    public record Candidate(
            int age,
            double distanceKm,
            boolean identityVerified,
            boolean sterilised,
            Collection<String> petSpecies,
            Collection<String> interests) {
    }

    public static boolean matches(DiscoveryPrefs p, Candidate c) {
        if (p == null) return true;
        if (c.age() < p.getAgeMin() || c.age() > p.getAgeMax()) return false;
        // Negative distance = the candidate hides it; don't exclude them for that.
        if (p.getMaxDistanceKm() != null && c.distanceKm() >= 0 && c.distanceKm() > p.getMaxDistanceKm()) {
            return false;
        }
        if (p.isVerifiedOnly() && !c.identityVerified()) return false;
        if (p.isSterilisedOnly() && !c.sterilised()) return false;
        if (p.isHasPetsOnly() && c.petSpecies().isEmpty()) return false;
        if (!p.getSpecies().isEmpty() && !intersects(p.getSpecies(), c.petSpecies())) return false;
        return p.getInterests().isEmpty() || intersects(p.getInterests(), c.interests());
    }

    private static boolean intersects(Set<String> wanted, Collection<String> have) {
        for (String h : have) {
            if (wanted.contains(h)) return true;
        }
        return false;
    }
}
