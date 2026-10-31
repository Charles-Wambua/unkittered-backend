package com.unkittered.api.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keeps uploaded media in line with the privacy policy:
 * <ul>
 *   <li>on startup, moves verification selfies and sterilisation documents
 *       that were stored publicly into the private folder;</li>
 *   <li>nightly, deletes stories older than 24 hours, then deletes every
 *       uploaded file that no row in the database refers to any more (deleted
 *       accounts, replaced photos, deleted stories, reels and messages,
 *       reviewed documents).</li>
 * </ul>
 * The sweep finds references by scanning every text column, so a new table that
 * stores a media URL is covered without changes here.
 */
@Component
@ConditionalOnProperty(name = "unkittered.storage.provider", havingValue = "local", matchIfMissing = true)
public class MediaMaintenance implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaMaintenance.class);

    /** A stored file name inside a URL: 32 hex characters and an extension. */
    private static final Pattern NAME = Pattern.compile("[0-9a-f]{32}\\.[a-z0-9]{2,5}");

    /** Abort a sweep that would delete more than this share of a large store: something is wrong. */
    private static final double MAX_DELETE_SHARE = 0.5;
    private static final int LARGE_STORE = 40;

    private final JdbcTemplate jdbc;
    private final LocalStorageService storage;
    private final Duration grace;

    public MediaMaintenance(JdbcTemplate jdbc, LocalStorageService storage,
                            @Value("${unkittered.media.orphan-grace-hours:48}") long graceHours) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.grace = Duration.ofHours(Math.max(1, graceHours));
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int moved = moveToPrivate("verifications", "selfie_url") + moveToPrivate("sterilisation_proofs", "document_url");
            if (moved > 0) log.info("Moved {} verification file(s) to private storage", moved);
        } catch (Exception e) {
            log.warn("Could not move verification files to private storage: {}", e.getMessage());
        }
    }

    /** Rewrites {@code /files/<name>} in one column to {@code /private/<name>}, moving the file. */
    int moveToPrivate(String table, String column) throws IOException {
        List<String> values = jdbc.queryForList(
                "SELECT " + column + " FROM " + table + " WHERE " + column + " LIKE '%/files/%'", String.class);
        int moved = 0;
        Files.createDirectories(storage.privateRoot());
        for (String url : values) {
            Matcher m = NAME.matcher(url);
            if (!m.find()) continue;
            String name = m.group();
            Path from = storage.root().resolve(name);
            Path to = storage.privateRoot().resolve(name);
            if (Files.exists(from)) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
            jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?",
                    LocalStorageService.PRIVATE_PREFIX + name, url);
            moved++;
        }
        return moved;
    }

    @Scheduled(cron = "${unkittered.media.sweep-cron:0 30 3 * * *}")
    public void nightly() {
        try {
            int stories = jdbc.update("DELETE FROM stories WHERE created_at < now() - interval '24 hours'");
            if (stories > 0) log.info("Deleted {} expired stories", stories);
            sweep();
        } catch (Exception e) {
            log.warn("Media sweep failed, nothing deleted: {}", e.getMessage());
        }
    }

    /** Deletes stored files that nothing refers to. Returns how many were deleted. */
    public int sweep() throws IOException {
        Set<String> referenced = referencedNames();
        List<Path> candidates = new ArrayList<>();
        int total = 0;
        Instant cutoff = Instant.now().minus(grace);
        for (Path dir : List.of(storage.root(), storage.privateRoot())) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : files.filter(Files::isRegularFile).toList()) {
                    String name = f.getFileName().toString();
                    if (!NAME.matcher(name).matches()) continue;
                    total++;
                    if (!referenced.contains(name) && Files.getLastModifiedTime(f).toInstant().isBefore(cutoff)) {
                        candidates.add(f);
                    }
                }
            }
        }
        if (total >= LARGE_STORE && candidates.size() > total * MAX_DELETE_SHARE) {
            log.warn("Media sweep would delete {} of {} files; skipped as a safety check. "
                    + "Check that the database is complete.", candidates.size(), total);
            return 0;
        }
        int deleted = 0;
        for (Path f : candidates) {
            if (Files.deleteIfExists(f)) deleted++;
        }
        log.info("Media sweep: {} file(s), {} referenced, {} unreferenced deleted", total, referenced.size(), deleted);
        return deleted;
    }

    /** Every stored file name mentioned in any text or JSON column of the public schema. */
    Set<String> referencedNames() {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT table_name, column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND data_type IN ('text', 'character varying', 'jsonb', 'json')
                  AND table_name <> 'flyway_schema_history'
                """);
        Set<String> names = new HashSet<>();
        for (Map<String, Object> c : columns) {
            String col = '"' + (String) c.get("column_name") + '"';
            String table = '"' + (String) c.get("table_name") + '"';
            List<String> values = jdbc.queryForList(
                    "SELECT " + col + "::text FROM " + table + " WHERE " + col + "::text ~ '[0-9a-f]{32}\\.'",
                    String.class);
            for (String v : values) {
                Matcher m = NAME.matcher(v);
                while (m.find()) names.add(m.group());
            }
        }
        return names;
    }
}
