package com.unkittered.api.admin;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.mail.MailService;
import com.unkittered.api.push.PushService;
import com.unkittered.api.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Announcements from the admin dashboard ("Merry Christmas", a new feature,
 * planned downtime) to a chosen audience, by push and/or email. Suspended
 * accounts never receive them. A test send goes only to the admin sending it.
 *
 * <p>An announcement can be scheduled for a local date and time: the hourly job
 * delivers it to each member once that time has arrived where they are (from
 * the phone's UTC offset; Kenya time if unknown).
 */
@Service
public class BroadcastService {

    private static final Logger log = LoggerFactory.getLogger(BroadcastService.class);

    /** FCM data {@code type}; the app shows it and opens the home screen. */
    static final String TYPE = "announcement";
    static final String KIND = "broadcast";
    static final String CHANNEL = "unkittered_updates";
    /** The last time zones reach a local time about 26 hours after the first. */
    private static final int LAST_ZONE_HOURS = 26;

    /** Who an announcement can go to: key → SQL condition over {@code users u} / {@code profiles p}. */
    private static final Map<String, String> AUDIENCES = Map.of(
            "all", "true",
            "verified", "p.is_verified",
            "paying", "u.subscription_tier <> 'free'",
            "free", "u.subscription_tier = 'free'",
            "dating", "p.connection_mode IN ('dating', 'both')",
            "friends", "p.connection_mode IN ('friends', 'both')",
            "inactive", "p.last_active_at < now() - interval '14 days'",
            "onboarding", "NOT u.onboarding_complete");

    /** {@code sendAtLocal}: optional ISO local date-time, e.g. {@code 2026-12-25T09:00}. */
    public record Request(String title, String body, String audience, boolean push, boolean email, boolean test,
                          String sendAtLocal) { }

    public record Result(int recipients, boolean push, boolean email, boolean test, boolean scheduled) { }

    private final JdbcTemplate jdbc;
    private final AdminService admin;
    private final PushService pushes;
    private final MailService mail;
    private final BroadcastService self;

    public BroadcastService(JdbcTemplate jdbc, AdminService admin, PushService pushes, MailService mail,
                            @Lazy BroadcastService self) {
        this.jdbc = jdbc;
        this.admin = admin;
        this.pushes = pushes;
        this.mail = mail;
        this.self = self;
    }

    public long audienceSize(String audience) {
        admin.requireAdmin();
        return jdbc.queryForObject("SELECT count(*) FROM users u LEFT JOIN profiles p ON p.user_id = u.id WHERE NOT u.suspended AND ("
                + condition(audience) + ")", Long.class);
    }

    public Map<String, Object> channels() {
        admin.requireAdmin();
        return Map.of("push", pushes.isEnabled(), "email", mail.isEnabled());
    }

    public Result send(Request r) {
        admin.requireAdmin();
        String title = r.title() == null ? "" : r.title().trim();
        String body = r.body() == null ? "" : r.body().trim();
        if (title.isEmpty() || title.length() > 80) throw ApiException.badRequest("Title must be 1–80 characters");
        if (body.isEmpty() || body.length() > 500) throw ApiException.badRequest("Message must be 1–500 characters");
        if (!r.push() && !r.email()) throw ApiException.badRequest("Choose push, email or both");
        if (r.email() && !mail.isEnabled()) throw ApiException.badRequest("Email isn't set up on the server yet");
        condition(r.audience());

        if (!r.test() && r.sendAtLocal() != null && !r.sendAtLocal().isBlank()) {
            LocalDateTime at;
            try {
                at = LocalDateTime.parse(r.sendAtLocal());
            } catch (DateTimeParseException e) {
                throw ApiException.badRequest("Invalid date and time");
            }
            jdbc.update("""
                    INSERT INTO admin_broadcasts (sent_by, title, body, audience, via_push, via_email, recipients,
                                                  status, send_at_local)
                    VALUES (?, ?, ?, ?, ?, ?, 0, 'scheduled', ?)
                    """, CurrentUser.id(), title, body, r.audience(), r.push(), r.email(), Timestamp.valueOf(at));
            log.info("Broadcast \"{}\" scheduled for {} local time (audience={})", title, at, r.audience());
            return new Result(0, r.push(), r.email(), false, true);
        }

        if (r.test()) {
            List<Map<String, Object>> me = jdbc.queryForList("SELECT id, email FROM users WHERE id = ?", CurrentUser.id());
            deliver(me, title, body, r.push(), r.email(), null);
            return new Result(me.size(), r.push(), r.email(), true, false);
        }

        UUID id = jdbc.queryForObject("""
                INSERT INTO admin_broadcasts (sent_by, title, body, audience, via_push, via_email, recipients, status, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, 'sent', now()) RETURNING id
                """, UUID.class, CurrentUser.id(), title, body, r.audience(), r.push(), r.email());
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.id, u.email FROM users u LEFT JOIN profiles p ON p.user_id = u.id "
                + "WHERE NOT u.suspended AND (" + condition(r.audience()) + ")");
        deliver(rows, title, body, r.push(), r.email(), id);
        log.info("Broadcast \"{}\" to {} member(s) (audience={}, push={}, email={})",
                title, rows.size(), r.audience(), r.push(), r.email());
        return new Result(rows.size(), r.push(), r.email(), false, false);
    }

    public void cancel(UUID id) {
        admin.requireAdmin();
        int n = jdbc.update("UPDATE admin_broadcasts SET status = 'cancelled' WHERE id = ? AND status IN ('scheduled', 'sending')", id);
        if (n == 0) throw ApiException.badRequest("Only scheduled announcements can be cancelled");
    }

    public List<Map<String, Object>> history() {
        admin.requireAdmin();
        return jdbc.queryForList("""
                SELECT b.id::text AS id, b.title, b.body, b.audience, b.via_push AS push, b.via_email AS email,
                       b.recipients, b.status, b.send_at_local AS "sendAtLocal", b.created_at AS "createdAt",
                       b.completed_at AS "completedAt", u.email AS "sentBy",
                       (SELECT count(*) FROM notification_log l WHERE l.kind = 'broadcast' AND l.ref = b.id::text
                          AND l.opened_at IS NOT NULL) AS opened
                FROM admin_broadcasts b LEFT JOIN users u ON u.id = b.sent_by
                ORDER BY b.created_at DESC LIMIT 30
                """);
    }

    /** Delivers scheduled announcements to members whose local time has reached the send time. */
    @Scheduled(cron = "${unkittered.broadcasts.cron:0 */10 * * * *}")
    public void deliverScheduled() {
        try {
            List<Map<String, Object>> due = jdbc.queryForList("""
                    SELECT id, title, body, audience, via_push, via_email, send_at_local FROM admin_broadcasts
                    WHERE status IN ('scheduled', 'sending')
                      AND send_at_local <= (now() AT TIME ZONE 'UTC') + interval '14 hours'
                    """);
            for (Map<String, Object> b : due) {
                UUID id = (UUID) b.get("id");
                String audience = (String) b.get("audience");
                List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT u.id, u.email FROM users u LEFT JOIN profiles p ON p.user_id = u.id
                        WHERE NOT u.suspended AND (%s)
                          AND (now() AT TIME ZONE 'UTC') + make_interval(mins => COALESCE(u.utc_offset_minutes, 180)) >= ?
                          AND NOT EXISTS (SELECT 1 FROM notification_log l WHERE l.user_id = u.id AND l.kind = 'broadcast'
                                          AND l.ref = ?)
                        """.formatted(condition(audience)), b.get("send_at_local"), id.toString());
                if (!rows.isEmpty()) {
                    deliver(rows, (String) b.get("title"), (String) b.get("body"),
                            (Boolean) b.get("via_push"), (Boolean) b.get("via_email"), id);
                }
                jdbc.update("""
                        UPDATE admin_broadcasts SET
                          status = CASE WHEN send_at_local + make_interval(hours => ?) <= (now() AT TIME ZONE 'UTC')
                                        THEN 'sent' ELSE 'sending' END,
                          completed_at = CASE WHEN send_at_local + make_interval(hours => ?) <= (now() AT TIME ZONE 'UTC')
                                              THEN now() END
                        WHERE id = ?
                        """, LAST_ZONE_HOURS, LAST_ZONE_HOURS, id);
            }
        } catch (Exception e) {
            log.warn("Scheduled broadcasts failed: {}", e.getMessage());
        }
    }

    /** Push and/or email to members; with an id, logs each delivery so opens can be counted. */
    private void deliver(List<Map<String, Object>> rows, String title, String body, boolean viaPush, boolean viaEmail,
                         UUID broadcastId) {
        List<UUID> ids = rows.stream().map(m -> (UUID) m.get("id")).toList();
        String ref = broadcastId == null ? "test" : broadcastId.toString();
        if (broadcastId != null) {
            jdbc.batchUpdate("INSERT INTO notification_log (user_id, kind, ref, channel) VALUES (?, 'broadcast', ?, ?)",
                    ids.stream().map(u -> new Object[] {u, ref, viaPush ? "push" : "email"}).toList());
            jdbc.update("UPDATE admin_broadcasts SET recipients = recipients + ? WHERE id = ?", ids.size(), broadcastId);
        }
        if (viaPush) {
            pushes.sendToUsers(ids, title, body, Map.of("type", TYPE, "kind", KIND, "ref", ref, "channel", CHANNEL));
        }
        if (viaEmail) {
            List<String> emails = rows.stream().map(m -> (String) m.get("email"))
                    .filter(e -> e != null && !e.isBlank()).toList();
            self.emailAll(emails, title, body);
        }
    }

    /** One email per member, spaced out so a large send doesn't trip SMTP rate limits. */
    @Async
    public void emailAll(List<String> emails, String title, String body) {
        int sent = 0;
        for (String to : emails) {
            if (mail.send(to, title, title, List.of(body.split("\\n\\s*\\n")),
                    new MailService.Action("Open Unkittered", mail.siteUrl()))) {
                sent++;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Broadcast email \"{}\": {} of {} sent", title, sent, emails.size());
    }

    private static String condition(String audience) {
        String c = AUDIENCES.get(audience == null ? "" : audience);
        if (c == null) throw ApiException.badRequest("Unknown audience");
        return c;
    }
}
