package com.unkittered.api.community;

import com.unkittered.api.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The latest app build (for the in-app "Update available" banner) and which
 * version each member runs: the app sends it with every request, and it's
 * written at most once an hour per member, or when it changes.
 */
@Service
public class AppReleaseService {

    private static final Pattern VERSION = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){1,3}(\\+\\d{1,6})?");
    private static final Duration REFRESH = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    /** What was last written per member, so most requests write nothing. */
    private final Map<UUID, Seen> seen = new ConcurrentHashMap<>();

    private record Seen(String version, Instant at) { }

    public AppReleaseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ReleaseDto(String version, int build, String url, String notes, Instant updatedAt) { }

    public record ReleaseRequest(String version, Integer build, String url, String notes) { }

    public record MemberVersionDto(String id, String name, String email, String version, Instant seenAt) { }

    public record VersionCountDto(String version, long members) { }

    public record VersionsDto(List<VersionCountDto> byVersion, List<MemberVersionDto> members) { }

    public Optional<ReleaseDto> latest() {
        return jdbc.query("SELECT version, build, url, notes, updated_at FROM app_release WHERE id = 1",
                (rs, i) -> new ReleaseDto(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant())).stream().findFirst();
    }

    public ReleaseDto save(ReleaseRequest r) {
        String version = r.version() == null ? "" : r.version().trim();
        String url = r.url() == null ? "" : r.url().trim();
        if (!VERSION.matcher(version).matches()) throw ApiException.badRequest("Version looks like 1.0.4");
        if (r.build() == null || r.build() < 1) throw ApiException.badRequest("Build number must be 1 or more");
        if (!url.startsWith("https://") || url.length() > 500) throw ApiException.badRequest("The download link must start with https://");
        String notes = r.notes() == null ? "" : r.notes().trim();
        if (notes.length() > 1000) throw ApiException.badRequest("Keep the notes under 1000 characters");
        jdbc.update("""
                INSERT INTO app_release (id, version, build, url, notes, updated_at) VALUES (1, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET version = EXCLUDED.version, build = EXCLUDED.build,
                    url = EXCLUDED.url, notes = EXCLUDED.notes, updated_at = now()
                """, version, r.build(), url, notes);
        return latest().orElseThrow();
    }

    /** Records the version a member's app reported ("1.0.4+5"); junk is ignored. */
    public void reported(UUID userId, String header) {
        if (header == null) return;
        String version = header.trim();
        if (!VERSION.matcher(version).matches()) return;
        Instant now = Instant.now();
        Seen last = seen.get(userId);
        if (last != null && last.version().equals(version) && last.at().plus(REFRESH).isAfter(now)) return;
        seen.put(userId, new Seen(version, now));
        jdbc.update("UPDATE users SET app_version = ?, app_version_seen_at = ? WHERE id = ?",
                version, Timestamp.from(now), userId);
    }

    public VersionsDto versions() {
        List<MemberVersionDto> members = jdbc.query("""
                SELECT u.id, coalesce(p.name, ''), u.email, u.app_version, u.app_version_seen_at
                FROM users u LEFT JOIN profiles p ON p.user_id = u.id
                WHERE u.onboarding_complete AND NOT coalesce(p.is_official, false)
                ORDER BY u.app_version_seen_at DESC NULLS LAST, p.name
                """, (rs, i) -> new MemberVersionDto(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), Optional.ofNullable(rs.getTimestamp(5)).map(Timestamp::toInstant).orElse(null)));
        Map<String, Long> counts = new LinkedHashMap<>();
        members.forEach(m -> counts.merge(m.version() == null ? "" : m.version(), 1L, Long::sum));
        List<VersionCountDto> byVersion = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new VersionCountDto(e.getKey().isEmpty() ? null : e.getKey(), e.getValue()))
                .toList();
        return new VersionsDto(byVersion, members);
    }
}
