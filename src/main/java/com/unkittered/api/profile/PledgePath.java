package com.unkittered.api.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.unkittered.api.common.ApiException;

import java.time.Duration;
import java.util.Arrays;

/** Which child-free pledge a member took. Renewed yearly. */
public enum PledgePath {
    CHILD_FREE_BY_CHOICE("child_free_by_choice"),
    STERILISED("sterilised"),
    PET_PARENT("pet_parent");

    /** Members re-confirm the pledge every year. */
    public static final Duration RENEWAL = Duration.ofDays(365);

    private final String wire;

    PledgePath(String wire) { this.wire = wire; }

    @JsonValue
    public String wire() { return wire; }

    @JsonCreator
    public static PledgePath fromWire(String raw) {
        return Arrays.stream(values()).filter(p -> p.wire.equalsIgnoreCase(raw)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown pledge: " + raw));
    }

    /**
     * Headline trust badge, computed server-side so clients can't forge it.
     * The gold sterilised badge needs medical review, not just the pledge.
     */
    public static String badgeFor(boolean identityVerified, boolean sterilisationVerified) {
        if (sterilisationVerified) return "sterilised_verified";
        return identityVerified ? "child_free_verified" : "none";
    }
}
