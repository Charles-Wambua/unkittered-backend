package com.unkittered.api.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.unkittered.api.common.ApiException;

import java.util.Arrays;

/** Permanent procedures a member may (voluntarily) declare. Values match V19. */
public enum SterilisationProcedure {
    VASECTOMY("vasectomy"),
    BILATERAL_TUBAL_LIGATION("bilateral_tubal_ligation"),
    SALPINGECTOMY("salpingectomy"),
    HYSTERECTOMY("hysterectomy"),
    OTHER("other");

    private final String wire;

    SterilisationProcedure(String wire) { this.wire = wire; }

    @JsonValue
    public String wire() { return wire; }

    @JsonCreator
    public static SterilisationProcedure fromWire(String raw) {
        return Arrays.stream(values()).filter(p -> p.wire.equalsIgnoreCase(raw)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown procedure: " + raw));
    }
}
