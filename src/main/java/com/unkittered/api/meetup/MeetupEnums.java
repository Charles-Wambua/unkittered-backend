package com.unkittered.api.meetup;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.unkittered.api.common.ApiException;

import java.util.Arrays;

/** Wire-stable enums for meetups ("Packs"). Values match the V18 CHECKs. */
public final class MeetupEnums {
    private MeetupEnums() { }

    public enum Kind {
        WALK("walk", "Walk"), HIKE("hike", "Hike"), PICNIC("picnic", "Picnic"),
        CAFE("cafe", "Café / drinks"), DINNER("dinner", "Dinner"), TRAVEL("travel", "Trip / travel"),
        GAMES("games", "Games night"), VOLUNTEER("volunteer", "Volunteer");

        private final String wire;
        private final String label;

        Kind(String wire, String label) {
            this.wire = wire;
            this.label = label;
        }

        @JsonValue
        public String wire() { return wire; }

        /** Human label, also written to the legacy free-text {@code category}. */
        public String label() { return label; }

        @JsonCreator
        public static Kind fromWire(String raw) {
            return Arrays.stream(values()).filter(k -> k.wire.equalsIgnoreCase(raw)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Unknown pack kind: " + raw));
        }
    }

    public enum PetPolicy {
        PETS_WELCOME("pets_welcome"), DOGS_ONLY("dogs_only"), HUMANS_ONLY("humans_only");

        private final String wire;

        PetPolicy(String wire) { this.wire = wire; }

        @JsonValue
        public String wire() { return wire; }

        public boolean allowsPets() { return this != HUMANS_ONLY; }

        @JsonCreator
        public static PetPolicy fromWire(String raw) {
            return Arrays.stream(values()).filter(p -> p.wire.equalsIgnoreCase(raw)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Unknown pet policy: " + raw));
        }
    }
}
