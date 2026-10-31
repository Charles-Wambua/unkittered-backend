package com.unkittered.api.reminders;

import com.unkittered.api.mail.MailService;
import com.unkittered.api.push.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hourly re-engagement reminders. Each rule finds members with something real
 * to come back for (an unread message, a match nobody has messaged, wags
 * waiting); nothing is sent when there's nothing true to say.
 *
 * <p>Guard rails: only between 09:00 and 21:00 in the member's own time zone;
 * only to members who allow "Reminders &amp; tips"; at most 2 chat reminders a
 * day; at most 1 other reminder a day and 3 a week; each reminder once per
 * occasion (see {@code notification_log}). Inactive members stop getting pushes
 * after 30 days and get one email a month instead.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    /** Members whose offset we don't know yet are assumed to be in Kenya (UTC+3). */
    static final int DEFAULT_OFFSET_MINUTES = 180;
    static final String PUSH_TYPE = "reminder";
    static final String CHANNEL = "unkittered_updates";

    static final Set<String> CHAT_KINDS = Set.of("unread", "match_nudge", "stalled");
    static final int CHAT_PER_DAY = 2;
    static final int OTHER_PER_DAY = 1;
    static final int OTHER_PER_WEEK = 3;
    static final int MONTHLY_EMAILS_PER_RUN = 40;

    /** SQL: the member's local time now. */
    static final String LOCAL_NOW = "((now() AT TIME ZONE 'UTC') + make_interval(mins => COALESCE(u.utc_offset_minutes, "
            + DEFAULT_OFFSET_MINUTES + ")))";
    /** SQL: a member's first name. */
    private static final String FIRST_NAME =
            "split_part(COALESCE(NULLIF(trim(%1$s.name), ''), NULLIF(trim(%2$s.display_name), ''), 'Someone'), ' ', 1)";
    /** SQL: neither of two users has blocked the other. */
    private static final String NOT_BLOCKED = "NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = %1$s AND b.blocked_id = %2$s) "
            + "OR (b.blocker_id = %2$s AND b.blocked_id = %1$s))";

    /** One reminder ready to go. */
    record Reminder(UUID userId, String kind, String ref, String title, String body, Map<String, String> data) { }

    private final JdbcTemplate jdbc;
    private final PushService push;
    private final MailService mail;

    public ReminderService(JdbcTemplate jdbc, PushService push, MailService mail) {
        this.jdbc = jdbc;
        this.push = push;
        this.mail = mail;
    }

    @Scheduled(cron = "${unkittered.reminders.cron:0 7 * * * *}")
    public void hourly() {
        try {
            int pushes = run();
            int emails = monthlyEmails();
            if (pushes + emails > 0) log.info("Reminders: {} push(es), {} email(s)", pushes, emails);
        } catch (Exception e) {
            log.warn("Reminder run failed: {}", e.getMessage());
        }
    }

    /** Finds and sends this hour's reminders. Returns how many were sent. */
    int run() {
        if (!push.isEnabled()) return 0;
        Set<UUID> reachable = new HashSet<>(jdbc.queryForList("""
                SELECT u.id FROM users u
                LEFT JOIN notification_prefs np ON np.user_id = u.id
                WHERE NOT u.suspended AND COALESCE(np.tips, true)
                  AND EXISTS (SELECT 1 FROM device_tokens d WHERE d.user_id = u.id)
                  AND extract(hour FROM %s) BETWEEN 9 AND 20
                """.formatted(LOCAL_NOW), UUID.class));
        if (reachable.isEmpty()) return 0;

        List<Reminder> candidates = new ArrayList<>();
        candidates.addAll(unreadMessages());
        candidates.addAll(unmessagedMatches());
        candidates.addAll(stalledChats());
        candidates.addAll(onboarding());
        candidates.addAll(wagsWaiting());
        candidates.addAll(newPeopleNearby());
        candidates.addAll(takingABreak());
        candidates.addAll(packsThisWeekend());
        candidates.addAll(noPhoto());
        candidates.addAll(notVerified());
        candidates.addAll(anniversary());

        Map<UUID, int[]> counts = recentCounts();   // [chat today, other today, other this week]
        int sent = 0;
        for (Reminder r : candidates) {
            if (!reachable.contains(r.userId())) continue;
            int[] c = counts.computeIfAbsent(r.userId(), k -> new int[3]);
            boolean chat = CHAT_KINDS.contains(r.kind());
            if (chat ? c[0] >= CHAT_PER_DAY : (c[1] >= OTHER_PER_DAY || c[2] >= OTHER_PER_WEEK)) continue;
            send(r);
            if (chat) c[0]++; else { c[1]++; c[2]++; }
            sent++;
        }
        return sent;
    }

    private void send(Reminder r) {
        jdbc.update("INSERT INTO notification_log (user_id, kind, ref) VALUES (?, ?, ?)", r.userId(), r.kind(), r.ref());
        Map<String, String> data = new HashMap<>(r.data());
        data.put("type", PUSH_TYPE);
        data.put("kind", r.kind());
        data.put("ref", r.ref());
        data.put("channel", CHANNEL);
        push.sendToUser(r.userId(), r.title(), r.body(), data);
    }

    private Map<UUID, int[]> recentCounts() {
        Map<UUID, int[]> counts = new HashMap<>();
        Instant dayAgo = Instant.now().minus(Duration.ofDays(1));
        jdbc.query("""
                SELECT user_id, kind, sent_at FROM notification_log
                WHERE channel = 'push' AND sent_at > now() - interval '7 days' AND kind NOT IN ('broadcast')
                """, rs -> {
            int[] c = counts.computeIfAbsent(rs.getObject("user_id", UUID.class), k -> new int[3]);
            boolean today = rs.getTimestamp("sent_at").toInstant().isAfter(dayAgo);
            if (CHAT_KINDS.contains(rs.getString("kind"))) {
                if (today) c[0]++;
            } else {
                if (today) c[1]++;
                c[2]++;
            }
        });
        return counts;
    }

    // ── Chats ────────────────────────────────────────────────────────────────

    /** A message unread for 12 hours: one nudge per conversation a week. */
    List<Reminder> unreadMessages() {
        return jdbc.query("""
                SELECT DISTINCT ON (x.id, r.uid) r.uid, x.id::text AS ref, %s AS name
                FROM messages m
                JOIN matches x ON x.id = m.match_id
                CROSS JOIN LATERAL (SELECT CASE WHEN x.user_low = m.sender_id THEN x.user_high ELSE x.user_low END AS uid) r
                JOIN users su ON su.id = m.sender_id
                LEFT JOIN profiles sp ON sp.user_id = m.sender_id
                WHERE m.read_at IS NULL AND m.deleted_at IS NULL AND NOT su.suspended
                  AND m.created_at < now() - interval '12 hours' AND m.created_at > now() - interval '3 days'
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = r.uid AND l.kind = 'unread'
                                  AND l.ref = x.id::text AND l.sent_at > now() - interval '7 days')
                  AND %s
                ORDER BY x.id, r.uid, m.created_at
                """.formatted(FIRST_NAME.formatted("sp", "su"), NOT_BLOCKED.formatted("r.uid", "m.sender_id")),
                (rs, i) -> {
                    String name = rs.getString("name");
                    return new Reminder(rs.getObject("uid", UUID.class), "unread", rs.getString("ref"),
                            name + " is waiting to hear from you 🐾",
                            "You have an unread message from " + name + ". Don't leave them hanging!",
                            Map.of("go", "chat", "matchId", rs.getString("ref")));
                });
    }

    /** Matched a day ago and nobody has said hi. */
    List<Reminder> unmessagedMatches() {
        return jdbc.query("""
                SELECT s.uid, x.id::text AS ref, %s AS name
                FROM matches x
                CROSS JOIN LATERAL (VALUES (x.user_low, x.user_high), (x.user_high, x.user_low)) s(uid, other)
                JOIN users ou ON ou.id = s.other
                LEFT JOIN profiles op ON op.user_id = s.other
                WHERE x.created_at < now() - interval '24 hours' AND x.created_at > now() - interval '72 hours'
                  AND NOT ou.suspended
                  AND NOT EXISTS (SELECT 1 FROM messages m WHERE m.match_id = x.id)
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = s.uid AND l.kind = 'match_nudge'
                                  AND l.ref = x.id::text)
                  AND %s
                """.formatted(FIRST_NAME.formatted("op", "ou"), NOT_BLOCKED.formatted("s.uid", "s.other")),
                (rs, i) -> {
                    String name = rs.getString("name");
                    return new Reminder(rs.getObject("uid", UUID.class), "match_nudge", rs.getString("ref"),
                            "You and " + name + " matched yesterday",
                            "Say hi before the moment passes. Their animals are probably waiting too.",
                            Map.of("go", "chat", "matchId", rs.getString("ref")));
                });
    }

    /** They read the other person's last message 3+ days ago and never replied. Once per message. */
    List<Reminder> stalledChats() {
        return jdbc.query("""
                WITH last AS (
                    SELECT DISTINCT ON (match_id) match_id, sender_id, created_at, read_at
                    FROM messages
                    WHERE deleted_at IS NULL AND created_at > now() - interval '8 days'
                    ORDER BY match_id, created_at DESC
                )
                SELECT r.uid, l.match_id::text AS ref, %s AS name
                FROM last l
                JOIN matches x ON x.id = l.match_id
                CROSS JOIN LATERAL (SELECT CASE WHEN x.user_low = l.sender_id THEN x.user_high ELSE x.user_low END AS uid) r
                JOIN users su ON su.id = l.sender_id
                LEFT JOIN profiles sp ON sp.user_id = l.sender_id
                WHERE l.read_at IS NOT NULL AND NOT su.suspended
                  AND l.created_at < now() - interval '3 days' AND l.created_at > now() - interval '7 days'
                  AND EXISTS (SELECT 1 FROM messages m2 WHERE m2.match_id = l.match_id AND m2.sender_id = r.uid)
                  AND NOT EXISTS (SELECT 1 FROM notification_log n WHERE n.user_id = r.uid AND n.kind = 'stalled'
                                  AND n.ref = l.match_id::text AND n.sent_at > l.created_at)
                  AND %s
                """.formatted(FIRST_NAME.formatted("sp", "su"), NOT_BLOCKED.formatted("r.uid", "l.sender_id")),
                (rs, i) -> {
                    String name = rs.getString("name");
                    return new Reminder(rs.getObject("uid", UUID.class), "stalled", rs.getString("ref"),
                            "Still thinking about your reply to " + name + "?",
                            "Your chat with " + name + " has gone quiet. A quick message keeps it going.",
                            Map.of("go", "chat", "matchId", rs.getString("ref")));
                });
    }

    // ── Coming back ──────────────────────────────────────────────────────────

    /** Away 7–14 days with wags they haven't answered. */
    List<Reminder> wagsWaiting() {
        return jdbc.query("""
                SELECT u.id, n.count FROM users u
                JOIN profiles p ON p.user_id = u.id
                CROSS JOIN LATERAL (
                    SELECT count(*) AS count FROM likes l JOIN users lu ON lu.id = l.liker_id
                    WHERE l.likee_id = u.id AND NOT lu.suspended
                      AND NOT EXISTS (SELECT 1 FROM likes back WHERE back.liker_id = u.id AND back.likee_id = l.liker_id)
                      AND NOT EXISTS (SELECT 1 FROM passes ps WHERE ps.passer_id = u.id AND ps.passee_id = l.liker_id)
                ) n
                WHERE u.onboarding_complete AND NOT p.paused AND n.count > 0
                  AND p.last_active_at < now() - interval '7 days' AND p.last_active_at > now() - interval '14 days'
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = 'inactive_7'
                                  AND l.sent_at > p.last_active_at)
                """, (rs, i) -> {
            long n = rs.getLong("count");
            return new Reminder(rs.getObject("id", UUID.class), "inactive_7", "",
                    n == 1 ? "Someone wagged at you 🐾" : n + " people wagged at you 🐾",
                    "They're waiting to see if you'll wag back. Take a look.",
                    Map.of("go", "today"));
        });
    }

    /** Away 3–7 days, and new people really have joined in their city this week. */
    List<Reminder> newPeopleNearby() {
        return jdbc.query("""
                SELECT u.id, initcap(trim(split_part(p.location, ',', 1))) AS city, n.count FROM users u
                JOIN profiles p ON p.user_id = u.id
                CROSS JOIN LATERAL (
                    SELECT count(*) AS count FROM users u2 JOIN profiles p2 ON p2.user_id = u2.id
                    WHERE u2.onboarding_complete AND NOT u2.suspended AND u2.id <> u.id
                      AND u2.created_at > now() - interval '7 days'
                      AND lower(trim(split_part(p2.location, ',', 1))) = lower(trim(split_part(p.location, ',', 1)))
                ) n
                WHERE u.onboarding_complete AND NOT p.paused AND trim(p.location) <> '' AND n.count > 0
                  AND p.last_active_at < now() - interval '3 days' AND p.last_active_at > now() - interval '7 days'
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = 'inactive_3'
                                  AND l.sent_at > p.last_active_at)
                """, (rs, i) -> {
            long n = rs.getLong("count");
            String city = rs.getString("city");
            return new Reminder(rs.getObject("id", UUID.class), "inactive_3", "",
                    n == 1 ? "Someone new joined near " + city : n + " new people near " + city,
                    "Fresh faces (and fresh furbabies) joined this week. Come say hi.",
                    Map.of("go", "today"));
        });
    }

    /** Away 14–30 days: one last nudge, offering a pause instead. */
    List<Reminder> takingABreak() {
        return jdbc.query("""
                SELECT u.id FROM users u JOIN profiles p ON p.user_id = u.id
                WHERE u.onboarding_complete AND NOT p.paused
                  AND p.last_active_at < now() - interval '14 days' AND p.last_active_at > now() - interval '30 days'
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = 'inactive_14'
                                  AND l.sent_at > p.last_active_at)
                """, (rs, i) -> new Reminder(rs.getObject("id", UUID.class), "inactive_14", "",
                "Taking a break?",
                "No problem. You can pause your profile in Settings so you're not shown while you're away.",
                Map.of("go", "settings")));
    }

    /** Thursday evening, local time: packs happening in their city over the weekend. */
    List<Reminder> packsThisWeekend() {
        String week = LocalDate.now().get(IsoFields.WEEK_BASED_YEAR) + "-W" + LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return jdbc.query("""
                SELECT u.id, initcap(trim(split_part(p.location, ',', 1))) AS city, n.count FROM users u
                JOIN profiles p ON p.user_id = u.id
                CROSS JOIN LATERAL (
                    SELECT count(*) AS count FROM meetups m
                    WHERE m.starts_at > now() AND m.starts_at < now() + interval '4 days'
                      AND lower(m.area) LIKE '%%' || lower(trim(split_part(p.location, ',', 1))) || '%%'
                ) n
                WHERE u.onboarding_complete AND NOT p.paused AND trim(p.location) <> '' AND n.count > 0
                  AND extract(dow FROM %s) = 4 AND extract(hour FROM %s) >= 17
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = 'packs_weekly'
                                  AND l.ref = ?)
                """.formatted(LOCAL_NOW, LOCAL_NOW), (rs, i) -> {
            long n = rs.getLong("count");
            return new Reminder(rs.getObject("id", UUID.class), "packs_weekly", week,
                    n == 1 ? "A pack is meeting near you this weekend" : n + " packs near " + rs.getString("city") + " this weekend",
                    "Walks, dinners and game nights with grown-ups who get it. Bring your dog, or come solo.",
                    Map.of("go", "packs"));
        }, week);
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    /** Sign-up not finished: after 1 day, then after 3 days. */
    List<Reminder> onboarding() {
        List<Reminder> out = new ArrayList<>();
        for (int day : new int[] {1, 3}) {
            String kind = "onboard_" + day;
            out.addAll(jdbc.query("""
                    SELECT u.id FROM users u
                    WHERE NOT u.onboarding_complete
                      AND u.created_at < now() - make_interval(days => ?) AND u.created_at > now() - make_interval(days => ? + 1)
                      AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = ?)
                    """, (rs, i) -> new Reminder(rs.getObject("id", UUID.class), kind, "",
                    day == 1 ? "You're nearly in 🐾" : "Your profile is almost ready",
                    day == 1 ? "Finish your profile to start meeting child-free people near you."
                             : "A few more details and you can start wagging. It takes about two minutes.",
                    Map.of("go", "today")), day, day, kind));
        }
        return out;
    }

    List<Reminder> noPhoto() {
        return jdbc.query("""
                SELECT u.id FROM users u JOIN profiles p ON p.user_id = u.id
                WHERE u.onboarding_complete AND p.image_url = '' AND u.created_at < now() - interval '2 days'
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = 'no_photo')
                """, (rs, i) -> new Reminder(rs.getObject("id", UUID.class), "no_photo", "",
                "Add a photo so people can find you",
                "Profiles without a photo hardly ever get wags. One clear photo of you is enough.",
                Map.of("go", "profile")));
    }

    List<Reminder> notVerified() {
        return jdbc.query("""
                SELECT u.id FROM users u JOIN profiles p ON p.user_id = u.id
                WHERE u.onboarding_complete AND NOT p.is_verified AND u.created_at < now() - interval '3 days'
                  AND NOT EXISTS (SELECT 1 FROM verifications v WHERE v.user_id = u.id)
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = 'verify')
                """, (rs, i) -> new Reminder(rs.getObject("id", UUID.class), "verify", "",
                "Get your Verified badge ✓",
                "A quick selfie shows people you're real. It takes less than a minute.",
                Map.of("go", "verify")));
    }

    List<Reminder> anniversary() {
        String year = String.valueOf(LocalDate.now().getYear());
        return jdbc.query("""
                SELECT u.id, extract(year FROM age(now(), u.created_at))::int AS years FROM users u
                WHERE u.onboarding_complete AND u.created_at < now() - interval '360 days'
                  AND to_char(u.created_at, 'MM-DD') = to_char(now(), 'MM-DD')
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = 'anniversary'
                                  AND l.ref = ?)
                """, (rs, i) -> {
            int years = Math.max(1, rs.getInt("years"));
            return new Reminder(rs.getObject("id", UUID.class), "anniversary", year,
                    "Happy Unkittered anniversary 🎉",
                    years == 1 ? "One year in the pack today. Thanks for being part of it."
                               : years + " years in the pack today. Thanks for being part of it.",
                    Map.of("go", "today"));
        }, year);
    }

    // ── Monthly email for members away 30+ days ──────────────────────────────

    int monthlyEmails() {
        if (!mail.isEnabled()) return 0;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id, u.email, %s AS name, initcap(trim(split_part(p.location, ',', 1))) AS city,
                       (SELECT count(*) FROM likes l JOIN users lu ON lu.id = l.liker_id
                        WHERE l.likee_id = u.id AND NOT lu.suspended
                          AND NOT EXISTS (SELECT 1 FROM likes b WHERE b.liker_id = u.id AND b.likee_id = l.liker_id)
                          AND NOT EXISTS (SELECT 1 FROM passes ps WHERE ps.passer_id = u.id AND ps.passee_id = l.liker_id)) AS wags,
                       (SELECT count(*) FROM users u2 JOIN profiles p2 ON p2.user_id = u2.id
                        WHERE u2.onboarding_complete AND u2.created_at > now() - interval '30 days' AND u2.id <> u.id
                          AND trim(p.location) <> ''
                          AND lower(trim(split_part(p2.location, ',', 1))) = lower(trim(split_part(p.location, ',', 1)))) AS newcomers
                FROM users u JOIN profiles p ON p.user_id = u.id
                LEFT JOIN notification_prefs np ON np.user_id = u.id
                WHERE NOT u.suspended AND u.onboarding_complete AND NOT p.paused AND COALESCE(np.tips, true)
                  AND u.email IS NOT NULL AND u.email <> ''
                  AND p.last_active_at < now() - interval '30 days'
                  AND extract(hour FROM %s) BETWEEN 9 AND 20
                  AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = 'inactive_email'
                                  AND l.sent_at > now() - interval '30 days')
                ORDER BY p.last_active_at DESC
                LIMIT ?
                """.formatted(FIRST_NAME.formatted("p", "u"), LOCAL_NOW), MONTHLY_EMAILS_PER_RUN);
        int sent = 0;
        for (Map<String, Object> r : rows) {
            long wags = ((Number) r.get("wags")).longValue();
            long newcomers = ((Number) r.get("newcomers")).longValue();
            String city = (String) r.get("city");
            List<String> lines = new ArrayList<>();
            lines.add("Hi " + r.get("name") + ", it's been a while. Here's what's happened since you last visited:");
            if (wags > 0) lines.add("🐾 " + (wags == 1 ? "1 person has" : wags + " people have") + " wagged at you and are waiting to hear back.");
            if (newcomers > 0 && city != null && !city.isBlank()) {
                lines.add("✨ " + newcomers + " new " + (newcomers == 1 ? "member" : "members") + " joined near " + city + ".");
            }
            if (wags == 0 && newcomers == 0) {
                lines.add("New members join every week, and packs are meeting for walks, dinners and trips.");
            }
            lines.add("If you'd rather not hear from us, turn off \"Reminders & tips\" in Settings, or pause your profile.");
            jdbc.update("INSERT INTO notification_log (user_id, kind, channel) VALUES (?, 'inactive_email', 'email')", r.get("id"));
            if (mail.send((String) r.get("email"), "We've missed you at Unkittered", "We've missed you 🐾", lines,
                    new MailService.Action("Open Unkittered", mail.siteUrl()))) {
                sent++;
            }
        }
        return sent;
    }

    /** Reminder sends and opens over the last 30 days, by kind, for the admin dashboard. */
    public List<Map<String, Object>> stats() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT kind, count(*) AS sent, count(opened_at) AS opened
                FROM notification_log WHERE sent_at > now() - interval '30 days'
                GROUP BY kind ORDER BY count(*) DESC
                """);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("label", LABELS.getOrDefault((String) r.get("kind"), (String) r.get("kind")));
            out.add(m);
        }
        return out;
    }

    static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("unread", "Unread message"),
            Map.entry("match_nudge", "Match, no first message"),
            Map.entry("stalled", "Chat gone quiet"),
            Map.entry("inactive_3", "Away 3 days: new people"),
            Map.entry("inactive_7", "Away 7 days: wags waiting"),
            Map.entry("inactive_14", "Away 14 days: pause offer"),
            Map.entry("inactive_email", "Away 30+ days: email"),
            Map.entry("packs_weekly", "Packs this weekend"),
            Map.entry("onboard_1", "Finish sign-up (day 1)"),
            Map.entry("onboard_3", "Finish sign-up (day 3)"),
            Map.entry("no_photo", "Add a photo"),
            Map.entry("verify", "Get verified"),
            Map.entry("anniversary", "Anniversary"),
            Map.entry("broadcast", "Announcements"));
}
