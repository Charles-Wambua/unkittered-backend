package com.unkittered.api.community;

import com.unkittered.api.chat.ChatService;
import com.unkittered.api.chat.OfficialChatService;
import com.unkittered.api.chat.RichMessages;
import com.unkittered.api.interaction.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Badges Shenron hands out. A sweep every few minutes finds members who've
 * earned one (verified, first match, first pack…) and awards it with a note in
 * their team chat. The very first sweep, on a database with no badges yet,
 * only tells early testers: everything else people did before badges existed
 * is awarded quietly.
 */
@Service
public class BadgeService {

    private static final Logger log = LoggerFactory.getLogger(BadgeService.class);

    /** Members who joined before this day are early testers. */
    private final LocalDate earlyTesterUntil;
    private final JdbcTemplate jdbc;
    private final OfficialChatService official;
    private final ChatService chat;

    public BadgeService(JdbcTemplate jdbc, OfficialChatService official, ChatService chat,
                        @Value("${unkittered.badges.early-tester-until:2027-03-01}") LocalDate earlyTesterUntil) {
        this.jdbc = jdbc;
        this.official = official;
        this.chat = chat;
        this.earlyTesterUntil = earlyTesterUntil;
    }

    public record BadgeDto(String code, String emoji, String label, Instant awardedAt) { }

    /** A member's badges, in profile order. */
    public List<BadgeDto> badgesOf(UUID userId) {
        List<BadgeDto> out = new ArrayList<>();
        jdbc.query("SELECT code, awarded_at FROM member_badges WHERE user_id = ?", rs -> {
            String code = rs.getString(1);
            Instant at = rs.getTimestamp(2).toInstant();
            Badges.find(code).ifPresent(b -> out.add(new BadgeDto(b.code(), b.emoji(), b.label(), at)));
        }, userId);
        out.sort(Comparator.comparingInt(d -> Badges.rank(d.code())));
        return out;
    }

    /**
     * Awards a badge once. When it's new and {@code tell} is set, Shenron
     * leaves a note in the member's team chat ({@code push} adds a notification).
     * Returns whether it was new.
     */
    public boolean award(UUID userId, String code, boolean tell, boolean push) {
        if (official.isOfficial(userId)) return false;
        int added = jdbc.update("INSERT INTO member_badges (user_id, code) VALUES (?, ?) ON CONFLICT DO NOTHING",
                userId, code);
        if (added == 0) return false;
        if (tell) Badges.find(code).ifPresent(b -> tell(userId, b, push));
        return true;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void firstSweep() {
        Integer any = jdbc.queryForObject("SELECT count(*) FROM (SELECT 1 FROM member_badges LIMIT 1) x", Integer.class);
        sweep(any == null || any == 0);
    }

    @Scheduled(cron = "${unkittered.badges.cron:0 */5 * * * *}")
    public void scheduledSweep() {
        sweep(false);
    }

    /** Awards every badge members have earned but don't have yet. */
    void sweep(boolean firstRun) {
        try {
            Optional<UUID> team = official.officialId();
            UUID teamId = team.orElse(new UUID(0, 0));
            int awarded = 0;
            for (var entry : candidates(teamId).entrySet()) {
                String code = entry.getKey();
                for (UUID member : entry.getValue()) {
                    // Before badges existed, only the early-tester thank-you is worth a message (and no buzz).
                    boolean tell = !firstRun || Badges.EARLY_TESTER.equals(code);
                    if (award(member, code, tell && team.isPresent(), !firstRun)) awarded++;
                }
            }
            if (awarded > 0) log.info("Awarded {} badge(s){}", awarded, firstRun ? " (first run)" : "");
        } catch (Exception e) {
            log.warn("Badge sweep failed: {}", e.getMessage());
        }
    }

    /** Members who've earned each badge and don't have it yet. */
    private Map<String, List<UUID>> candidates(UUID teamId) {
        String member = """
                SELECT u.id FROM users u
                WHERE u.onboarding_complete AND NOT u.suspended AND u.id <> ?
                  AND NOT EXISTS (SELECT 1 FROM member_badges b WHERE b.user_id = u.id AND b.code = ?)
                """;
        Map<String, List<UUID>> out = new LinkedHashMap<>();
        Timestamp cutoff = Timestamp.from(earlyTesterUntil.atStartOfDay(ZoneId.of("Africa/Nairobi")).toInstant());
        out.put(Badges.EARLY_TESTER, jdbc.queryForList(member + " AND u.created_at < ?", UUID.class,
                teamId, Badges.EARLY_TESTER, cutoff));
        out.put(Badges.VERIFIED, jdbc.queryForList(member
                + " AND EXISTS (SELECT 1 FROM profiles p WHERE p.user_id = u.id AND p.is_verified)", UUID.class,
                teamId, Badges.VERIFIED));
        out.put(Badges.FIRST_MATCH, jdbc.queryForList(member + """
                 AND EXISTS (SELECT 1 FROM matches m
                             WHERE (m.user_low = u.id AND m.user_high <> ?) OR (m.user_high = u.id AND m.user_low <> ?))
                """, UUID.class, teamId, Badges.FIRST_MATCH, teamId, teamId));
        out.put(Badges.FIRST_PACK, jdbc.queryForList(member + """
                 AND (EXISTS (SELECT 1 FROM meetup_rsvps r WHERE r.user_id = u.id)
                      OR EXISTS (SELECT 1 FROM meetups mt WHERE mt.host_id = u.id))
                """, UUID.class, teamId, Badges.FIRST_PACK));
        return out;
    }

    /** Shenron's note: a pet note to their pet when they have one, else a message. */
    void tell(UUID memberId, Badges.Badge badge, boolean push) {
        try {
            Optional<Match> m = official.chatFor(memberId);
            Optional<UUID> teamId = official.officialId();
            if (m.isEmpty() || teamId.isEmpty()) return;
            String text = Badges.noteFor(badge);
            String ourPet = leadPet(teamId.get());
            String theirPet = leadPet(memberId);
            if (ourPet != null && theirPet != null) {
                chat.postAs(m.get().getId(), teamId.get(), memberId, RichMessages.Kind.PET_NOTE, text,
                        Map.of("fromPet", ourPet, "toPet", theirPet, "text", text), push);
            } else {
                chat.postAs(m.get().getId(), teamId.get(), memberId, RichMessages.Kind.TEXT, text, null, push);
            }
        } catch (Exception e) {
            log.warn("Badge note for {} not sent: {}", memberId, e.getMessage());
        }
    }

    private String leadPet(UUID userId) {
        return jdbc.queryForList("SELECT name FROM pets WHERE owner_id = ? ORDER BY position LIMIT 1", String.class, userId)
                .stream().filter(n -> n != null && !n.isBlank()).findFirst().orElse(null);
    }
}
