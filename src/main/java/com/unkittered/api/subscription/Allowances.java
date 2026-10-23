package com.unkittered.api.subscription;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.UUID;

/**
 * The single source of truth for per-tier allowances and the Redis counters
 * that track them — shared by enforcement (likes, super-likes, boosts) and by
 * {@code GET /v1/subscriptions/me}, so what the app shows always matches what
 * the server enforces.
 */
public final class Allowances {

    private Allowances() { }

    /** Free members' wags per UTC day; paid tiers are unlimited. */
    public static final int FREE_DAILY_LIKES = 15;

    public static String normalise(String tier) {
        return tier == null ? "free" : tier.toLowerCase();
    }

    /** Null means unlimited. */
    public static Integer dailyLikes(String tier) {
        return "free".equals(normalise(tier)) ? FREE_DAILY_LIKES : null;
    }

    /** Pet notes (super-likes) per ISO week. */
    public static int weeklySuperLikes(String tier) {
        return switch (normalise(tier)) {
            case "gold" -> 10;
            case "plus" -> 5;
            default -> 1;
        };
    }

    /** Who can see the people who liked them (a Gold perk). */
    public static boolean seesWhoLiked(String tier) {
        String t = normalise(tier);
        return "gold".equals(t);
    }

    public static int monthlyBoosts(String tier) {
        return switch (normalise(tier)) {
            case "gold" -> 5;
            case "plus" -> 1;
            default -> 0;
        };
    }

    public static String dailyLikesKey(UUID userId) {
        return "likes:daily:" + userId + ":" + LocalDate.now(ZoneOffset.UTC);
    }

    public static String weeklySuperLikesKey(UUID userId) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = now.get(WeekFields.ISO.weekBasedYear());
        return "superlikes:weekly:" + userId + ":" + year + "-" + week;
    }

    public static String monthlyBoostsKey(UUID userId) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return "boosts:monthly:" + userId + ":" + now.getYear() + "-" + now.getMonthValue();
    }

    /** What's left, never below zero. */
    public static int left(int limit, Object used) {
        long count = used instanceof Number n ? n.longValue()
                : used == null ? 0 : Long.parseLong(used.toString());
        return (int) Math.max(0, limit - count);
    }
}
