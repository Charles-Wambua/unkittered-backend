package com.unkittered.api.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only queries behind the admin dashboard's review, member and analytics
 * screens. Plain SQL: these are cross-table aggregates that don't map onto one
 * entity. Every entry point checks the caller is an admin.
 */
@Service
@Transactional(readOnly = true)
public class AdminInsightsService {

    private static final int MEMBER_PAGE = 50;

    private final JdbcTemplate jdbc;
    private final AdminService admin;

    public AdminInsightsService(JdbcTemplate jdbc, AdminService admin) {
        this.jdbc = jdbc;
        this.admin = admin;
    }

    /** Sterilisation proofs awaiting review, with who sent them. */
    public List<Map<String, Object>> pendingProofs() {
        admin.requireAdmin();
        return jdbc.queryForList("""
                SELECT sp.user_id::text AS "userId", sp.document_url AS "documentUrl",
                       sp.created_at AS "submittedAt",
                       COALESCE(NULLIF(p.name, ''), u.display_name) AS name, u.email, p.age,
                       p.location, p.image_url AS "imageUrl", p.pledge_path AS "pledgePath",
                       p.sterilisation_procedure AS "procedure", p.is_verified AS verified,
                       u.created_at AS "joinedAt"
                FROM sterilisation_proofs sp
                JOIN users u ON u.id = sp.user_id
                LEFT JOIN profiles p ON p.user_id = sp.user_id
                WHERE sp.status = 'pending' AND sp.document_url <> ''
                ORDER BY sp.created_at
                """);
    }

    /** User and pack reports with names, the reporter's details and repeat counts. */
    public List<Map<String, Object>> reports() {
        admin.requireAdmin();
        return jdbc.queryForList("""
                SELECT r.id::text AS id, 'user' AS type, r.reason, r.details, r.created_at AS "at",
                       r.reporter_id::text AS "reporterId",
                       COALESCE(NULLIF(rp.name, ''), ru.display_name) AS "reporterName",
                       r.reported_id::text AS "targetId",
                       COALESCE(NULLIF(tp.name, ''), tu.display_name) AS "targetName",
                       tp.image_url AS "targetImage", tu.suspended AS "targetSuspended",
                       (SELECT count(*) FROM reports x WHERE x.reported_id = r.reported_id) AS "targetReports"
                FROM reports r
                JOIN users ru ON ru.id = r.reporter_id
                LEFT JOIN profiles rp ON rp.user_id = r.reporter_id
                JOIN users tu ON tu.id = r.reported_id
                LEFT JOIN profiles tp ON tp.user_id = r.reported_id
                UNION ALL
                SELECT mr.id::text, 'meetup', mr.reason, NULL, mr.created_at,
                       mr.reporter_id::text, COALESCE(NULLIF(rp.name, ''), ru.display_name),
                       mr.meetup_id::text, m.title, NULL, false,
                       (SELECT count(*) FROM meetup_reports x WHERE x.meetup_id = mr.meetup_id)
                FROM meetup_reports mr
                JOIN users ru ON ru.id = mr.reporter_id
                LEFT JOIN profiles rp ON rp.user_id = mr.reporter_id
                JOIN meetups m ON m.id = mr.meetup_id
                ORDER BY "at" DESC
                """);
    }

    /** Members matching a name or email search, newest first. */
    public List<Map<String, Object>> members(String query, String filter) {
        admin.requireAdmin();
        String q = query == null ? "" : query.trim().toLowerCase();
        String where = switch (filter == null ? "" : filter) {
            case "suspended" -> "AND u.suspended";
            case "verified" -> "AND p.is_verified";
            case "paying" -> "AND u.subscription_tier <> 'free'";
            case "reported" -> "AND EXISTS (SELECT 1 FROM reports x WHERE x.reported_id = u.id)";
            case "incomplete" -> "AND NOT u.onboarding_complete";
            default -> "";
        };
        return jdbc.queryForList("""
                SELECT u.id::text AS id, COALESCE(NULLIF(p.name, ''), u.display_name) AS name,
                       u.email, p.age, p.location, p.image_url AS "imageUrl",
                       u.subscription_tier AS tier, COALESCE(p.is_verified, false) AS verified,
                       u.suspended, u.onboarding_complete AS onboarded,
                       u.created_at AS "joinedAt", p.last_active_at AS "lastActiveAt",
                       (SELECT count(*) FROM reports x WHERE x.reported_id = u.id) AS reports
                FROM users u
                LEFT JOIN profiles p ON p.user_id = u.id
                WHERE (? = '' OR lower(u.email) LIKE ? OR lower(p.name) LIKE ?
                       OR lower(u.display_name) LIKE ? OR u.id::text = ?)
                """ + where + """
                ORDER BY u.created_at DESC
                LIMIT ?
                """, q, "%" + q + "%", "%" + q + "%", "%" + q + "%", q, MEMBER_PAGE);
    }

    /** Everything an admin needs to judge one member. */
    public Map<String, Object> member(UUID id) {
        admin.requireAdmin();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id::text AS id, COALESCE(NULLIF(p.name, ''), u.display_name) AS name,
                       u.email, u.subscription_tier AS tier, u.suspended, u.is_admin AS admin,
                       u.onboarding_complete AS onboarded, u.created_at AS "joinedAt",
                       p.age, p.location, p.bio, p.occupation, p.image_url AS "imageUrl",
                       p.is_verified AS verified, p.last_active_at AS "lastActiveAt",
                       p.connection_mode AS "connectionMode", p.pledge_path AS "pledgePath",
                       p.pledged_at AS "pledgedAt", p.sterilisation_procedure AS "procedure",
                       p.sterilisation_verified AS "sterilisationVerified", p.paused
                FROM users u
                LEFT JOIN profiles p ON p.user_id = u.id
                WHERE u.id = ?
                """, id);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>(rows.get(0));
        m.put("gallery", jdbc.queryForList(
                "SELECT value FROM profile_gallery WHERE user_id = ?", String.class, id));
        m.put("pets", jdbc.queryForList("""
                SELECT name, species, breed_and_age AS "breedAndAge", photo_url AS "photoUrl"
                FROM pets WHERE owner_id = ? ORDER BY created_at
                """, id));
        m.put("counts", jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM matches WHERE user_low = ? OR user_high = ?) AS matches,
                       (SELECT count(*) FROM likes WHERE liker_id = ?) AS "wagsSent",
                       (SELECT count(*) FROM likes WHERE likee_id = ?) AS "wagsReceived",
                       (SELECT count(*) FROM messages WHERE sender_id = ?) AS messages,
                       (SELECT count(*) FROM meetups WHERE host_id = ?) AS "packsHosted",
                       (SELECT count(*) FROM blocks WHERE blocked_id = ?) AS "blockedBy",
                       (SELECT count(*) FROM reports WHERE reporter_id = ?) AS "reportsMade"
                """, id, id, id, id, id, id, id, id));
        m.put("reportsAgainst", jdbc.queryForList("""
                SELECT r.reason, r.details, r.created_at AS "at",
                       COALESCE(NULLIF(rp.name, ''), ru.display_name) AS "reporterName"
                FROM reports r
                JOIN users ru ON ru.id = r.reporter_id
                LEFT JOIN profiles rp ON rp.user_id = r.reporter_id
                WHERE r.reported_id = ?
                ORDER BY r.created_at DESC LIMIT 20
                """, id));
        return m;
    }

    /** Activity, funnel, audience and money figures for the analytics screen. */
    public Map<String, Object> analytics() {
        admin.requireAdmin();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("activity", jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE last_active_at > now() - interval '1 day')  AS dau,
                       count(*) FILTER (WHERE last_active_at > now() - interval '7 days') AS wau,
                       count(*) FILTER (WHERE last_active_at > now() - interval '30 days') AS mau
                FROM profiles
                """));
        a.put("funnel", jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM users) AS "signedUp",
                       (SELECT count(*) FROM users WHERE onboarding_complete) AS onboarded,
                       (SELECT count(*) FROM profiles WHERE image_url <> '') AS "addedPhoto",
                       (SELECT count(*) FROM profiles WHERE is_verified) AS verified,
                       (SELECT count(DISTINCT x) FROM (SELECT user_low AS x FROM matches
                                                      UNION SELECT user_high FROM matches) t) AS matched,
                       (SELECT count(DISTINCT sender_id) FROM messages) AS messaged,
                       (SELECT count(*) FROM users WHERE subscription_tier <> 'free') AS paying
                """));
        a.put("last7Days", jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM users WHERE created_at > now() - interval '7 days') AS signups,
                       (SELECT count(*) FROM likes WHERE created_at > now() - interval '7 days') AS wags,
                       (SELECT count(*) FROM matches WHERE created_at > now() - interval '7 days') AS matches,
                       (SELECT count(*) FROM messages WHERE created_at > now() - interval '7 days') AS messages,
                       (SELECT count(*) FROM meetups WHERE created_at > now() - interval '7 days') AS packs,
                       (SELECT count(*) FROM reports WHERE created_at > now() - interval '7 days') AS reports
                """));
        a.put("dailySignups", jdbc.queryForList("""
                SELECT to_char(d, 'YYYY-MM-DD') AS day,
                       (SELECT count(*) FROM users u WHERE u.created_at >= d AND u.created_at < d + interval '1 day') AS count
                FROM generate_series(date_trunc('day', now()) - interval '29 days', date_trunc('day', now()), interval '1 day') d
                ORDER BY d
                """));
        a.put("pledgePaths", breakdown("SELECT COALESCE(pledge_path, 'not set') AS label, count(*) AS count FROM profiles GROUP BY 1 ORDER BY 2 DESC"));
        a.put("connectionModes", breakdown("SELECT connection_mode AS label, count(*) AS count FROM profiles GROUP BY 1 ORDER BY 2 DESC"));
        a.put("species", breakdown("SELECT species AS label, count(*) AS count FROM pets GROUP BY 1 ORDER BY 2 DESC LIMIT 8"));
        a.put("tiers", breakdown("SELECT subscription_tier AS label, count(*) AS count FROM users GROUP BY 1 ORDER BY 2 DESC"));
        a.put("ageBands", breakdown("""
                SELECT CASE WHEN age < 25 THEN '18-24' WHEN age < 30 THEN '25-29' WHEN age < 35 THEN '30-34'
                            WHEN age < 40 THEN '35-39' WHEN age < 50 THEN '40-49' ELSE '50+' END AS label,
                       count(*) AS count
                FROM profiles GROUP BY 1 ORDER BY 1
                """));
        a.put("topLocations", breakdown("""
                SELECT initcap(trim(split_part(location, ',', 1))) AS label, count(*) AS count
                FROM profiles WHERE trim(location) <> ''
                GROUP BY 1 ORDER BY 2 DESC LIMIT 10
                """));
        a.put("wallet", jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE kind = 'top_up' AND status = 'completed') AS "topUps",
                       COALESCE(sum(amount_kes) FILTER (WHERE kind = 'top_up' AND status = 'completed'), 0) AS "topUpKes",
                       count(*) FILTER (WHERE kind = 'gift_sent' AND status = 'completed') AS "giftsSent",
                       COALESCE(sum(-paws) FILTER (WHERE kind = 'gift_sent' AND status = 'completed'), 0) AS "giftPaws",
                       count(*) FILTER (WHERE kind = 'withdrawal' AND status = 'pending') AS "pendingWithdrawals",
                       COALESCE(sum(-paws) FILTER (WHERE kind = 'withdrawal' AND status = 'pending'), 0) AS "pendingWithdrawalPaws",
                       count(*) FILTER (WHERE status = 'failed') AS "failedPayments"
                FROM wallet_transactions
                WHERE created_at > now() - interval '30 days'
                """));
        return a;
    }

    private List<Map<String, Object>> breakdown(String sql) {
        return jdbc.queryForList(sql);
    }
}
