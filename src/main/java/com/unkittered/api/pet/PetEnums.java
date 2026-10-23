package com.unkittered.api.pet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.unkittered.api.common.ApiException;

import java.util.Arrays;

/** Wire-stable enums for pets. Values match the DB CHECK constraints (V18). */
public final class PetEnums {
    private PetEnums() { }

    public enum Species {
        DOG("dog", "🐕"), CAT("cat", "🐈"), RABBIT("rabbit", "🐰"), BIRD("bird", "🦜"), FISH("fish", "🐠"),
        REPTILE("reptile", "🦎"), HORSE("horse", "🐴"), SMALL_PET("small_pet", "🐹"), OTHER("other", "🐾");

        private final String wire;
        private final String emoji;

        Species(String wire, String emoji) {
            this.wire = wire;
            this.emoji = emoji;
        }

        @JsonValue
        public String wire() { return wire; }

        public String emoji() { return emoji; }

        @JsonCreator
        public static Species fromWire(String raw) {
            return Arrays.stream(values()).filter(s -> s.wire.equalsIgnoreCase(raw)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Unknown species: " + raw));
        }
    }

    /** Who a pet gets along with. */
    public enum Temperament {
        BIG_DOGS("big_dogs"), SMALL_DOGS("small_dogs"), CATS("cats"), NEW_PEOPLE("new_people");

        private final String wire;

        Temperament(String wire) { this.wire = wire; }

        @JsonValue
        public String wire() { return wire; }

        @JsonCreator
        public static Temperament fromWire(String raw) {
            return Arrays.stream(values()).filter(t -> t.wire.equalsIgnoreCase(raw)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Unknown temperament: " + raw));
        }
    }
}
