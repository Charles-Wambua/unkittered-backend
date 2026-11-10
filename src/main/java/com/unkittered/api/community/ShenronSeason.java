package com.unkittered.api.community;

import java.time.LocalDate;
import java.time.MonthDay;

/**
 * Shenron's outfits through the year. The windows match the app's
 * {@code ShenronSeason} (lib/shared/domain/shenron_season.dart) exactly.
 */
public enum ShenronSeason {
    NONE("", ""),
    NEW_YEAR("new-year", "Happy New Year from Shenron 🎉"),
    VALENTINES("valentines", "Happy Valentine’s from Shenron 💘"),
    MADARAKA("madaraka", "Happy Madaraka Day! 🇰🇪"),
    MASHUJAA("mashujaa", "Happy Mashujaa Day! 🇰🇪"),
    HALLOWEEN("halloween", "Happy Halloween from Shenron 🎃"),
    JAMHURI("jamhuri", "Happy Jamhuri Day! 🇰🇪"),
    CHRISTMAS("christmas", "Merry Christmas from Shenron 🎄");

    private final String slug;
    private final String greeting;

    ShenronSeason(String slug, String greeting) {
        this.slug = slug;
        this.greeting = greeting;
    }

    public String slug() { return slug; }

    public String greeting() { return greeting; }

    /** The photo for this season, under the public site. */
    public String photoPath() {
        return "/site/official/shenron" + (slug.isEmpty() ? "" : "-" + slug) + ".png";
    }

    public static ShenronSeason of(LocalDate date) {
        MonthDay d = MonthDay.from(date);
        if (in(d, 12, 31, 12, 31) || in(d, 1, 1, 1, 2)) return NEW_YEAR;
        if (in(d, 2, 10, 2, 14)) return VALENTINES;
        if (in(d, 5, 30, 6, 1)) return MADARAKA;
        if (in(d, 10, 18, 10, 20)) return MASHUJAA;
        if (in(d, 10, 26, 10, 31)) return HALLOWEEN;
        if (in(d, 12, 10, 12, 12)) return JAMHURI;
        if (in(d, 12, 18, 12, 27)) return CHRISTMAS;
        return NONE;
    }

    private static boolean in(MonthDay d, int m1, int d1, int m2, int d2) {
        return !d.isBefore(MonthDay.of(m1, d1)) && !d.isAfter(MonthDay.of(m2, d2));
    }
}
