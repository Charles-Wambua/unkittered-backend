package com.unkittered.api.chat;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server-side chat safety filter — the authoritative mirror of the Flutter
 * {@code ChatModeration}. Two checks:
 *   • Profanity / vulgar language — always rejected.
 *   • Contact info (phone / email) — rejected unless the sender is Gold.
 *
 * The client runs the same rules for instant feedback, but this is the source
 * of truth (a tampered client can't bypass it).
 */
public final class MessageModeration {

    private MessageModeration() { }

    public enum Flag { CLEAN, PROFANITY, CONTACT_INFO }

    private static final Set<String> PROFANITY = Set.of(
            "fuck", "fuk", "fuc", "motherfucker", "fucker",
            "shit", "bullshit", "bitch", "bastard",
            "asshole", "dickhead", "dick", "cock", "pussy",
            "cunt", "slut", "whore", "wanker", "prick",
            "nigger", "nigga", "faggot", "fag", "retard", "rape",
            "cum", "blowjob", "handjob", "nudes", "horny");

    private static final Set<String> NUMBER_WORDS = Set.of(
            "zero", "oh", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "nil", "double", "triple");

    private static final Pattern EMAIL =
            Pattern.compile("[a-z0-9._%+\\-]+\\s*@\\s*[a-z0-9.\\-]+\\s*\\.\\s*[a-z]{2,}");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{7,}");

    public static Flag check(String text, boolean isGold) {
        if (text == null || text.isBlank()) return Flag.CLEAN;
        if (hasProfanity(text)) return Flag.PROFANITY;
        if (!isGold && hasContactInfo(text)) return Flag.CONTACT_INFO;
        return Flag.CLEAN;
    }

    public static String reason(Flag flag) {
        return switch (flag) {
            case PROFANITY -> "Let's keep it kind — that message contains language that isn't allowed.";
            case CONTACT_INFO -> "Sharing contact details is a Gold feature — keep chatting here, or upgrade to share.";
            default -> "";
        };
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static boolean hasProfanity(String text) {
        String norm = normalize(text);
        for (String t : norm.split("[^a-z]+")) {
            if (t.isEmpty()) continue;
            if (PROFANITY.contains(t)) return true;
            String collapsed = t.replaceAll("(.)\\1+", "$1"); // "fuuuck" → "fuck"
            if (PROFANITY.contains(collapsed)) return true;
        }
        return false;
    }

    /** Lowercase + map common leetspeak substitutions to letters. */
    private static String normalize(String text) {
        String s = text.toLowerCase();
        s = s.replace('@', 'a').replace('4', 'a').replace('8', 'b').replace('3', 'e')
             .replace('1', 'i').replace('!', 'i').replace('0', 'o').replace('$', 's')
             .replace('5', 's').replace('7', 't').replace('+', 't');
        return s;
    }

    private static boolean hasContactInfo(String text) {
        String lower = text.toLowerCase();
        if (EMAIL.matcher(lower).find()) return true;

        // Numeric phone: strip separators, look for a run of 7+ digits.
        String digitsOnly = lower.replaceAll("[\\s().\\-_/+]", "");
        if (DIGIT_RUN.matcher(digitsOnly).find()) return true;

        // Spelled-out numbers: 6+ number-words in a row implies a dictated number.
        int run = 0;
        for (String w : lower.split("[^a-z]+")) {
            if (NUMBER_WORDS.contains(w)) {
                if (++run >= 6) return true;
            } else {
                run = 0;
            }
        }
        return false;
    }
}
