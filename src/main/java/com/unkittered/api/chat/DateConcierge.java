package com.unkittered.api.chat;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Date ideas grounded in what two people actually share: their interests,
 * whether both have animals, and where they are. Pure and deterministic, so
 * it's unit-tested; a model-backed planner can replace it behind the same
 * endpoint later.
 */
public final class DateConcierge {

    private DateConcierge() { }

    public static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    /** What the concierge knows about the two people. */
    public record Input(String theirName, String area, List<String> myInterests, List<String> theirInterests,
                        boolean bothHavePets) { }

    public record Plan(String emoji, String title, String venueType, String area, String when, String why,
                       ZonedDateTime startsAt, int durationMinutes) { }

    private record Template(List<String> keywords, String emoji, String title, String venueType,
                            DayOfWeek day, int hour, int minutes, Function<String, String> why) { }

    private static final List<Template> TEMPLATES = List.of(
            new Template(List.of("dog", "pets", "animals", "rescue", "dog parks", "dog walks"), "🐾",
                    "a dog-friendly park picnic", "a leafy park", DayOfWeek.SATURDAY, 12, 120,
                    n -> "You're both animal people — bring the furbabies and let them break the ice."),
            new Template(List.of("coffee", "café", "cafe", "brunch"), "☕",
                    "flat whites & a slow morning", "a specialty coffee spot", DayOfWeek.SATURDAY, 10, 90,
                    n -> n + " is a coffee person — low-pressure, and easy to turn into a walk after."),
            new Template(List.of("hiking", "outdoors", "trail", "nature", "running", "road trips"), "🥾",
                    "a golden-hour trail walk", "a scenic trail", DayOfWeek.SATURDAY, 16, 120,
                    n -> n + " loves the outdoors — talking side by side means no awkward silences."),
            new Template(List.of("cooking", "food", "foodie", "market", "wine", "dinner"), "🍅",
                    "a weekend-market food crawl", "a weekend market", DayOfWeek.SUNDAY, 11, 120,
                    n -> n + " is into food — grazing stalls gives you plenty to share and react to."),
            new Template(List.of("photography", "art", "creative", "design", "film"), "📷",
                    "a sunset photo walk", "a viewpoint or old quarter", DayOfWeek.FRIDAY, 17, 90,
                    n -> n + " has an eye for photos — a built-in icebreaker, and great light."),
            new Template(List.of("yoga", "pilates", "wellness", "fitness", "gym"), "🧘",
                    "morning yoga in the park", "an outdoor class", DayOfWeek.SATURDAY, 9, 75,
                    n -> n + " is into wellness — relaxed, active, and it naturally ends in brunch."),
            new Template(List.of("reading", "books", "book club", "writing"), "📚",
                    "an indie bookshop crawl + coffee", "a couple of bookshops", DayOfWeek.SUNDAY, 14, 120,
                    n -> n + " is a reader — swapping recommendations is an effortless first chat."),
            new Template(List.of("music", "jazz", "live music", "gig", "dancing"), "🎷",
                    "a live-music night", "a small live-music bar", DayOfWeek.FRIDAY, 20, 150,
                    n -> n + " loves music — the set gives you something to lean over and talk about."),
            new Template(List.of("board games", "gaming", "chess", "games"), "🎲",
                    "a board-game café afternoon", "a board-game café", DayOfWeek.SUNDAY, 15, 120,
                    n -> "A little friendly competition — the easiest way to see someone's playful side."));

    private static final List<Template> FALLBACKS = List.of(
            new Template(List.of(), "🍷", "a cosy wine-bar evening", "a relaxed wine bar", DayOfWeek.THURSDAY, 19, 90,
                    n -> "Low-key and easy to keep short or let run long — no pressure either way."),
            new Template(List.of(), "🖼️", "a gallery wander + coffee", "a gallery or museum", DayOfWeek.SATURDAY, 13, 120,
                    n -> "Plenty to react to, easy to wander, and coffee next door if it clicks."));

    /** Up to [max] plans, best-grounded first. Shared interests win over theirs alone. */
    public static List<Plan> suggest(Input in, ZonedDateTime now, int max) {
        Set<String> shared = lower(in.myInterests());
        shared.retainAll(lower(in.theirInterests()));
        String theirs = String.join(" ", lower(in.theirInterests()));
        String both = String.join(" ", shared);
        String area = in.area() == null || in.area().isBlank() ? "somewhere central" : in.area().trim();

        List<Plan> out = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        // Pass 1: things you share (and the pet picnic if you both have animals).
        for (Template t : TEMPLATES) {
            boolean petPicnic = t.emoji().equals("🐾") && in.bothHavePets();
            if ((petPicnic || matches(t, both)) && used.add(t.title())) out.add(build(t, in.theirName(), area, now));
        }
        // Pass 2: things they love.
        for (Template t : TEMPLATES) {
            if (matches(t, theirs) && used.add(t.title())) out.add(build(t, in.theirName(), area, now));
        }
        for (Template t : FALLBACKS) {
            if (used.add(t.title())) out.add(build(t, in.theirName(), area, now));
        }
        return out.subList(0, Math.min(max, out.size()));
    }

    /** The next [day] at [hour], at least a few hours away, in Nairobi time. */
    static ZonedDateTime next(DayOfWeek day, int hour, ZonedDateTime now) {
        ZonedDateTime local = now.withZoneSameInstant(ZONE);
        ZonedDateTime candidate = local.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        int ahead = (day.getValue() - local.getDayOfWeek().getValue() + 7) % 7;
        candidate = candidate.plusDays(ahead);
        if (candidate.isBefore(local.plusHours(3))) candidate = candidate.plusWeeks(1);
        return candidate;
    }

    private static Plan build(Template t, String name, String area, ZonedDateTime now) {
        ZonedDateTime at = next(t.day(), t.hour(), now);
        String when = at.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + partOfDay(t.hour());
        return new Plan(t.emoji(), t.title(), t.venueType(), area, when, t.why().apply(name), at, t.minutes());
    }

    private static String partOfDay(int hour) {
        if (hour < 12) return "morning";
        if (hour < 14) return "midday";
        if (hour < 17) return "afternoon";
        if (hour < 19) return "at sunset";
        return "evening";
    }

    private static boolean matches(Template t, String haystack) {
        return !haystack.isBlank() && t.keywords().stream().anyMatch(haystack::contains);
    }

    private static Set<String> lower(List<String> items) {
        Set<String> out = new LinkedHashSet<>();
        if (items != null) items.forEach(i -> out.add(i.toLowerCase(Locale.ROOT)));
        return out;
    }
}
