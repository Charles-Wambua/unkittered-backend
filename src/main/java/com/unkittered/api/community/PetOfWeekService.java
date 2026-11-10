package com.unkittered.api.community;

import com.unkittered.api.chat.OfficialChatService;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.safety.BlockRepository;
import com.unkittered.api.stories.Story;
import com.unkittered.api.stories.StoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Pet of the week: members nominate one pet a week (changing it replaces it);
 * every Monday at 9:00 Nairobi time the most-nominated pet of the week before
 * wins. The winner's owner gets the badge, and the team posts a story.
 */
@Service
public class PetOfWeekService {

    private static final Logger log = LoggerFactory.getLogger(PetOfWeekService.class);
    static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    /** A winner stays "current" for a week after it's picked. */
    private static final Duration SHOWN_FOR = Duration.ofDays(7);

    private final JdbcTemplate jdbc;
    private final BlockRepository blocks;
    private final OfficialChatService official;
    private final BadgeService badges;
    private final StoryRepository stories;
    private final Clock clock;

    @Autowired
    public PetOfWeekService(JdbcTemplate jdbc, BlockRepository blocks, OfficialChatService official,
                            BadgeService badges, StoryRepository stories) {
        this(jdbc, blocks, official, badges, stories, Clock.systemUTC());
    }

    PetOfWeekService(JdbcTemplate jdbc, BlockRepository blocks, OfficialChatService official,
                     BadgeService badges, StoryRepository stories, Clock clock) {
        this.jdbc = jdbc;
        this.blocks = blocks;
        this.official = official;
        this.badges = badges;
        this.stories = stories;
        this.clock = clock;
    }

    public record WinnerDto(String week, String petId, String petName, String petPhotoUrl,
                            String ownerId, String ownerName, int nominations) { }

    /** What the app shows: the current winner and the viewer's own nomination this week. */
    public record PetOfWeekDto(WinnerDto current, String myNominationPetId, Instant votingClosesAt) { }

    public record TallyDto(String petId, String petName, String ownerName, int nominations) { }

    /** The Monday that starts the week containing {@code day}. */
    static LocalDate weekOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    LocalDate thisWeek() {
        return weekOf(LocalDate.now(clock.withZone(NAIROBI)));
    }

    public PetOfWeekDto forViewer(UUID viewerId) {
        LocalDate week = thisWeek();
        String mine = jdbc.queryForList("SELECT pet_id::text FROM pet_nominations WHERE week = ? AND nominator_id = ?",
                String.class, Date.valueOf(week), viewerId).stream().findFirst().orElse(null);
        Instant closes = week.plusWeeks(1).atTime(9, 0).atZone(NAIROBI).toInstant();
        return new PetOfWeekDto(current().orElse(null), mine, closes);
    }

    /** The latest winner, while it's still this week's news. */
    public Optional<WinnerDto> current() {
        Instant since = clock.instant().minus(SHOWN_FOR);
        return jdbc.query("""
                SELECT week, pet_id, pet_name, pet_photo, owner_id, owner_name, nominations
                FROM pets_of_the_week WHERE picked_at > ? ORDER BY picked_at DESC LIMIT 1
                """, (rs, i) -> new WinnerDto(rs.getDate(1).toLocalDate().toString(),
                str(rs.getObject(2)), rs.getString(3), rs.getString(4), str(rs.getObject(5)),
                rs.getString(6), rs.getInt(7)), java.sql.Timestamp.from(since)).stream().findFirst();
    }

    /** Nominates a pet for this week; a member's earlier nomination this week is replaced. */
    @Transactional
    public PetOfWeekDto nominate(UUID nominatorId, UUID petId) {
        UUID owner = jdbc.queryForList("""
                SELECT pt.owner_id FROM pets pt JOIN users u ON u.id = pt.owner_id
                WHERE pt.id = ? AND NOT u.suspended
                """, UUID.class, petId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Pet not found"));
        if (owner.equals(nominatorId)) throw ApiException.badRequest("You can't nominate your own pet. Ask a friend!");
        if (official.isOfficial(owner)) throw ApiException.badRequest("Shenron is flattered, but he's not in the running.");
        if (blocks.existsBetween(nominatorId, owner)) throw ApiException.notFound("Pet not found");
        jdbc.update("""
                INSERT INTO pet_nominations (week, nominator_id, pet_id) VALUES (?, ?, ?)
                ON CONFLICT (week, nominator_id) DO UPDATE SET pet_id = EXCLUDED.pet_id, created_at = now()
                """, Date.valueOf(thisWeek()), nominatorId, petId);
        return forViewer(nominatorId);
    }

    @Transactional
    public PetOfWeekDto withdraw(UUID nominatorId) {
        jdbc.update("DELETE FROM pet_nominations WHERE week = ? AND nominator_id = ?", Date.valueOf(thisWeek()), nominatorId);
        return forViewer(nominatorId);
    }

    /** This week's nominations so far, most first. */
    public List<TallyDto> tally(LocalDate week) {
        return jdbc.query("""
                SELECT n.pet_id, pt.name, pr.name, count(*) AS c, min(n.created_at) AS first
                FROM pet_nominations n
                JOIN pets pt ON pt.id = n.pet_id
                JOIN users u ON u.id = pt.owner_id AND NOT u.suspended
                JOIN profiles pr ON pr.user_id = pt.owner_id
                WHERE n.week = ?
                GROUP BY n.pet_id, pt.name, pr.name
                ORDER BY c DESC, first ASC
                """, (rs, i) -> new TallyDto(str(rs.getObject(1)), rs.getString(2), rs.getString(3), rs.getInt(4)),
                Date.valueOf(week));
    }

    public List<TallyDto> thisWeeksTally() {
        return tally(thisWeek());
    }

    /** Every Monday at 9:00: last week's winner. */
    @Scheduled(cron = "${unkittered.pet-of-the-week.cron:0 0 9 * * MON}", zone = "Africa/Nairobi")
    public void weekly() {
        try {
            pick(thisWeek().minusWeeks(1));
        } catch (Exception e) {
            log.warn("Pet of the week not picked: {}", e.getMessage());
        }
    }

    /** Picks this week's winner now (the admin's "pick early"). */
    public WinnerDto pickNow() {
        return pick(thisWeek()).orElseThrow(() -> ApiException.badRequest(
                "No nominations yet this week, or this week's winner was already picked."));
    }

    /** Picks a week's winner once. Empty when nobody was nominated or it was already picked. */
    @Transactional
    public Optional<WinnerDto> pick(LocalDate week) {
        List<TallyDto> tally = tally(week);
        if (tally.isEmpty()) return Optional.empty();
        TallyDto top = tally.get(0);
        UUID petId = UUID.fromString(top.petId());
        Map<String, Object> pet = jdbc.queryForMap("SELECT owner_id, name, photo_url FROM pets WHERE id = ?", petId);
        UUID owner = (UUID) pet.get("owner_id");
        int added = jdbc.update("""
                INSERT INTO pets_of_the_week (week, pet_id, owner_id, pet_name, pet_photo, owner_name, nominations)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (week) DO NOTHING
                """, Date.valueOf(week), petId, owner, top.petName(), pet.get("photo_url"), top.ownerName(), top.nominations());
        if (added == 0) return Optional.empty();
        log.info("Pet of the week {}: {} ({} nomination(s))", week, top.petName(), top.nominations());

        // The owner's badge (or, for a repeat winner, just the congratulations).
        if (!badges.award(owner, Badges.PET_OF_WEEK, true, true)) {
            Badges.find(Badges.PET_OF_WEEK).ifPresent(b -> badges.tell(owner, b, true));
        }
        // The team's story, which every member sees first in Stories.
        official.officialId().ifPresent(team -> stories.save(new Story(team, (String) pet.get("photo_url"),
                storyText(top.petName(), firstName(top.ownerName()), top.nominations()))));
        return current();
    }

    static String storyText(String pet, String owner, int nominations) {
        return "🏆 Pet of the week: " + pet + "!\n"
                + (nominations == 1 ? "Nominated by a fellow member." : "Nominated by " + nominations + " members.")
                + (owner == null || owner.isBlank() ? "" : " Congrats, " + owner + "!");
    }

    private static String firstName(String name) {
        return name == null ? null : name.trim().split("\\s+")[0];
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
