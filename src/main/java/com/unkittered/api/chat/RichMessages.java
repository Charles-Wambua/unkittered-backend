package com.unkittered.api.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.unkittered.api.common.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Validates and normalises typed chat messages. Pure (no Spring, no DB) so the
 * rules are unit-tested directly.
 *
 * <p>Every message keeps a human-readable {@code body} — the admin console,
 * push notifications and older clients only read that — while {@code payload}
 * carries the structured data for kinds other than plain text.
 */
public final class RichMessages {
    private RichMessages() { }

    public enum Kind {
        TEXT("text"), PET_NOTE("pet_note"), PHOTO("photo"), PLAN("plan"), PLAN_RESPONSE("plan_response"),
        /** Created by the server when a gift is sent; clients can't post it. */
        GIFT("gift");

        private final String wire;

        Kind(String wire) { this.wire = wire; }

        @JsonValue
        public String wire() { return wire; }

        @JsonCreator
        public static Kind fromWire(String raw) {
            if (raw == null) return TEXT;
            return Arrays.stream(values()).filter(k -> k.wire.equalsIgnoreCase(raw)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Unknown message kind: " + raw));
        }
    }

    /** A plan that a response may refer to: who proposed it. */
    /** A plan already in the chat: who proposed it and when it starts. */
    public record PlanRef(UUID proposerId, Instant startsAt) { }

    /** The validated message, ready to persist. */
    public record Prepared(Kind kind, String body, Map<String, Object> payload) {
        /** Text the moderation filter must check. */
        List<String> moderatedText() {
            List<String> out = new ArrayList<>();
            out.add(body);
            if (payload != null) {
                for (String k : List.of("title", "place", "caption", "fromPet", "toPet")) {
                    if (payload.get(k) instanceof String s && !s.isBlank()) out.add(s);
                }
            }
            return out;
        }
    }

    static final int MAX_TEXT = 4000;
    static final int MAX_NOTE = 1000;
    private static final Duration MAX_PLAN_AHEAD = Duration.ofDays(365);

    /**
     * @param senderId       the author
     * @param findPlan       looks up a plan message in this conversation by id
     * @param isOwnMediaUrl  true when a URL points at our own storage
     */
    public static Prepared prepare(Kind kind, String text, Map<String, Object> payload, UUID senderId,
                                   Function<String, PlanRef> findPlan,
                                   Function<String, Boolean> isOwnMediaUrl, Instant now) {
        Map<String, Object> in = payload == null ? Map.of() : payload;
        return switch (kind) {
            case TEXT -> {
                String t = required(text, "text", MAX_TEXT);
                yield new Prepared(kind, t, null);
            }
            case PET_NOTE -> {
                String from = required(str(in, "fromPet"), "fromPet", 30);
                String to = required(str(in, "toPet"), "toPet", 30);
                String note = required(str(in, "text", text), "text", MAX_NOTE);
                yield new Prepared(kind, note, ordered("fromPet", from, "toPet", to, "text", note));
            }
            case PHOTO -> {
                String url = required(str(in, "url"), "url", 500);
                if (!isOwnMediaUrl.apply(url)) {
                    throw ApiException.badRequest("Photos must be uploaded through /me/photos first");
                }
                String caption = optional(str(in, "caption"), "caption", 200);
                yield new Prepared(kind, caption.isEmpty() ? "📷 Photo" : caption,
                        ordered("url", url, "caption", caption));
            }
            case PLAN -> {
                String title = required(str(in, "title"), "title", 80);
                String place = required(str(in, "place"), "place", 120);
                Instant startsAt = instant(in.get("startsAt"));
                if (startsAt.isBefore(now.minus(Duration.ofHours(1))) || startsAt.isAfter(now.plus(MAX_PLAN_AHEAD))) {
                    throw ApiException.badRequest("Pick a time within the next year");
                }
                int duration = intIn(in.get("durationMinutes"), 90, 15, 720, "durationMinutes");
                List<String> amenities = amenities(in.get("amenities"));
                Map<String, Object> out = ordered(
                        "id", UUID.randomUUID().toString(), "title", title, "startsAt", startsAt.toString(),
                        "place", place, "durationMinutes", duration, "amenities", amenities);
                yield new Prepared(kind, "📅 " + title + " · " + place, out);
            }
            case GIFT -> throw ApiException.badRequest("Gifts are sent from the gift sheet.");
            case PLAN_RESPONSE -> {
                String planId = required(str(in, "planId"), "planId", 64);
                if (!(in.get("accepted") instanceof Boolean accepted)) {
                    throw ApiException.badRequest("accepted must be true or false");
                }
                PlanRef plan = findPlan.apply(planId);
                if (plan == null) throw ApiException.badRequest("That plan isn't in this conversation");
                if (plan.proposerId().equals(senderId)) {
                    throw ApiException.badRequest("You can't answer your own plan");
                }
                if (plan.startsAt() != null && !now.isBefore(plan.startsAt())) {
                    throw ApiException.badRequest("This plan has already passed. Suggest a new one?");
                }
                yield new Prepared(kind, accepted ? "✅ Plan accepted" : "Maybe another time",
                        ordered("planId", planId, "accepted", accepted));
            }
        };
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String str(Map<String, Object> m, String key) {
        return m.get(key) instanceof String s ? s : null;
    }

    private static String str(Map<String, Object> m, String key, String fallback) {
        String s = str(m, key);
        return s != null ? s : fallback;
    }

    private static String required(String v, String field, int max) {
        if (v == null || v.isBlank()) throw ApiException.badRequest(field + " is required");
        return optional(v, field, max);
    }

    private static String optional(String v, String field, int max) {
        String t = v == null ? "" : v.trim();
        if (t.length() > max) throw ApiException.badRequest(field + " must be at most " + max + " characters");
        return t;
    }

    private static Instant instant(Object raw) {
        if (!(raw instanceof String s)) throw ApiException.badRequest("startsAt is required");
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("startsAt must be an ISO-8601 instant");
        }
    }

    private static int intIn(Object raw, int fallback, int min, int max, String field) {
        if (raw == null) return fallback;
        if (!(raw instanceof Number n)) throw ApiException.badRequest(field + " must be a number");
        int v = n.intValue();
        if (v < min || v > max) throw ApiException.badRequest(field + " must be between " + min + " and " + max);
        return v;
    }

    private static List<String> amenities(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list) || list.size() > 8) {
            throw ApiException.badRequest("amenities must be a list of up to 8 items");
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof String s) || s.isBlank() || s.length() > 30) {
                throw ApiException.badRequest("Each amenity must be 1-30 characters");
            }
            out.add(s.trim());
        }
        return out;
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
