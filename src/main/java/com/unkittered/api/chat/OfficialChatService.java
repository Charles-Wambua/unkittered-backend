package com.unkittered.api.chat;

import com.unkittered.api.interaction.Match;
import com.unkittered.api.interaction.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The always-on chat between every member and the official Unkittered account.
 *
 * <p>It is stored as a match (so it reuses messages, replies, reactions and
 * pushes) but it isn't one: nobody wagged, it never shows as a new match, and
 * it can't be unmatched or blocked. Members get it when they finish signing
 * up; existing members get theirs once, at startup.
 */
@Service
public class OfficialChatService {

    private static final Logger log = LoggerFactory.getLogger(OfficialChatService.class);

    private final JdbcTemplate jdbc;
    private final MatchRepository matches;
    private final ChatService chat;

    public OfficialChatService(JdbcTemplate jdbc, MatchRepository matches, ChatService chat) {
        this.jdbc = jdbc;
        this.matches = matches;
        this.chat = chat;
    }

    /** The official account's user id, if one has been set up. */
    public Optional<UUID> officialId() {
        return jdbc.queryForList("SELECT user_id FROM profiles WHERE is_official ORDER BY user_id LIMIT 1", UUID.class)
                .stream().findFirst();
    }

    /** True when {@code userId} is the official account. */
    public boolean isOfficial(UUID userId) {
        return officialId().map(userId::equals).orElse(false);
    }

    /** The member's chat with the official account, created if it doesn't exist yet. */
    public Optional<Match> chatFor(UUID memberId) {
        Optional<UUID> official = officialId();
        if (official.isEmpty() || official.get().equals(memberId)) return Optional.empty();
        UUID o = official.get();
        return Optional.of(matches.findByUserLowAndUserHigh(Match.low(o, memberId), Match.high(o, memberId))
                .orElseGet(() -> {
                    try {
                        // No match event: this isn't a match, so no "It's a match!" either.
                        return matches.save(Match.of(o, memberId));
                    } catch (DataIntegrityViolationException raced) {
                        return matches.findByUserLowAndUserHigh(Match.low(o, memberId), Match.high(o, memberId))
                                .orElseThrow();
                    }
                }));
    }

    /** Gives every signed-up member who doesn't have one their official chat. */
    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        try {
            Optional<UUID> official = officialId();
            if (official.isEmpty()) return;
            List<UUID> missing = jdbc.queryForList("""
                    SELECT u.id FROM users u
                    WHERE u.onboarding_complete AND u.id <> ?
                      AND NOT EXISTS (SELECT 1 FROM matches m
                                      WHERE (m.user_low = u.id AND m.user_high = ?)
                                         OR (m.user_low = ? AND m.user_high = u.id))
                    """, UUID.class, official.get(), official.get(), official.get());
            missing.forEach(this::chatFor);
            if (!missing.isEmpty()) log.info("Opened the Unkittered chat for {} member(s)", missing.size());
        } catch (Exception e) {
            log.warn("Official chat backfill failed: {}", e.getMessage());
        }
    }

    /**
     * Welcomes a member who just finished signing up: a hello from the team,
     * and a note from the official pet to theirs. Never fails the sign-up.
     */
    public void welcome(UUID memberId) {
        try {
            Optional<Match> m = chatFor(memberId);
            if (m.isEmpty()) return;
            UUID official = officialId().orElseThrow();
            String name = firstName(memberId);
            chat.postAs(m.get().getId(), official, memberId, RichMessages.Kind.TEXT,
                    welcomeText(name), null, true);
            String theirPet = leadPetName(memberId);
            String ourPet = leadPetName(official);
            if (theirPet != null && ourPet != null) {
                String note = "Welcome to the pack, " + theirPet + "! Sniff around, everyone here is friendly. "
                        + "I'll keep an eye out for good walks near you. 🐾";
                chat.postAs(m.get().getId(), official, memberId, RichMessages.Kind.PET_NOTE, note,
                        Map.of("fromPet", ourPet, "toPet", theirPet, "text", note), false);
            }
        } catch (Exception e) {
            log.warn("Welcome for {} failed: {}", memberId, e.getMessage());
        }
    }

    /**
     * Puts an announcement in each member's official chat, as an inbox they can
     * scroll back through. No push: the announcement sends its own.
     */
    public void announce(List<UUID> memberIds, String title, String body) {
        Optional<UUID> official = officialId();
        if (official.isEmpty()) return;
        String text = title + "\n\n" + body;
        int posted = 0;
        for (UUID member : memberIds) {
            try {
                Optional<Match> m = chatFor(member);
                if (m.isEmpty()) continue;
                chat.postAs(m.get().getId(), official.get(), member, RichMessages.Kind.TEXT, text, null, false);
                posted++;
            } catch (Exception e) {
                log.warn("Announcement to {} not posted in chat: {}", member, e.getMessage());
            }
        }
        log.info("Announcement \"{}\" posted in {} official chat(s)", title, posted);
    }

    static String welcomeText(String firstName) {
        String hi = firstName == null || firstName.isBlank() ? "Welcome to Unkittered!" : "Welcome to Unkittered, " + firstName + "!";
        return hi + " 🐾\n\n"
                + "This is the Unkittered team. Message us here any time with questions, ideas, or anything that feels off.\n\n"
                + "Three quick tips:\n"
                + "1. A clear photo of you (and your pet) gets far more wags.\n"
                + "2. Get the Verified ✓ badge with a quick selfie in Settings.\n"
                + "3. Join a Pack to meet people in person, with your dog or solo.\n\n"
                + "Shenron, Chief Wag Officer";
    }

    private String firstName(UUID userId) {
        return jdbc.queryForList("SELECT split_part(trim(name), ' ', 1) FROM profiles WHERE user_id = ?", String.class, userId)
                .stream().findFirst().orElse(null);
    }

    private String leadPetName(UUID userId) {
        return jdbc.queryForList("SELECT name FROM pets WHERE owner_id = ? ORDER BY position LIMIT 1", String.class, userId)
                .stream().filter(n -> n != null && !n.isBlank()).findFirst().orElse(null);
    }
}
