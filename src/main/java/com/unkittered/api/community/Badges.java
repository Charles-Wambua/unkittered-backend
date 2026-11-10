package com.unkittered.api.community;

import java.util.List;
import java.util.Optional;

/** The badges Shenron hands out, in the order profiles show them. */
public final class Badges {

    private Badges() { }

    public record Badge(String code, String emoji, String label, String note) { }

    public static final String EARLY_TESTER = "early_tester";
    public static final String VERIFIED = "verified";
    public static final String FIRST_MATCH = "first_match";
    public static final String FIRST_PACK = "first_pack";
    public static final String PET_OF_WEEK = "pet_of_week";

    public static final List<Badge> ALL = List.of(
            new Badge(PET_OF_WEEK, "🏆", "Pet of the week",
                    "Your pet was voted Pet of the week by the community! Bring them a treat from us."),
            new Badge(EARLY_TESTER, "🧪", "Early tester",
                    "You were here before Unkittered hit the Play Store. Thank you for testing with us!"),
            new Badge(VERIFIED, "✅", "Verified",
                    "Your selfie checked out, so you wear the Verified tick now. People wag back more at verified profiles."),
            new Badge(FIRST_MATCH, "💞", "First match",
                    "Your first match! Say hi, and maybe plan a walk with the pets."),
            new Badge(FIRST_PACK, "🐾", "Pack member",
                    "You joined your first Pack. See you out there, sniffing around!"));

    public static Optional<Badge> find(String code) {
        return ALL.stream().filter(b -> b.code().equals(code)).findFirst();
    }

    /** Where a badge sits on a profile. */
    static int rank(String code) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).code().equals(code)) return i;
        return ALL.size();
    }

    /** Shenron's note when a badge is awarded. */
    public static String noteFor(Badge b) {
        return "🏅 New badge: " + b.emoji() + " " + b.label() + "\n\n" + b.note();
    }
}
