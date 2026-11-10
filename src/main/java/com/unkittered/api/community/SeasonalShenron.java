package com.unkittered.api.community;

import com.unkittered.api.chat.OfficialChatService;
import com.unkittered.api.stories.Story;
import com.unkittered.api.stories.StoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Dresses Shenron (the official account's pet) for the season: just after
 * midnight in Nairobi his photo changes, and on the first day of a holiday the
 * team posts a greeting story with the new outfit.
 */
@Service
public class SeasonalShenron {

    private static final Logger log = LoggerFactory.getLogger(SeasonalShenron.class);

    private final JdbcTemplate jdbc;
    private final OfficialChatService official;
    private final StoryRepository stories;
    private final String siteUrl;

    public SeasonalShenron(JdbcTemplate jdbc, OfficialChatService official, StoryRepository stories,
                           @Value("${unkittered.site-url:https://unkittered.directpublish.duckdns.org}") String siteUrl) {
        this.jdbc = jdbc;
        this.official = official;
        this.stories = stories;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        dress(LocalDate.now(PetOfWeekService.NAIROBI));
    }

    @Scheduled(cron = "${unkittered.shenron.cron:0 5 0 * * *}", zone = "Africa/Nairobi")
    public void daily() {
        dress(LocalDate.now(PetOfWeekService.NAIROBI));
    }

    /** Puts on the outfit for {@code day}; returns whether it changed. */
    boolean dress(LocalDate day) {
        try {
            UUID team = official.officialId().orElse(null);
            if (team == null) return false;
            Map<String, Object> pet = jdbc.queryForList(
                    "SELECT id, photo_url FROM pets WHERE owner_id = ? ORDER BY position LIMIT 1", team)
                    .stream().findFirst().orElse(null);
            if (pet == null) return false;
            ShenronSeason season = ShenronSeason.of(day);
            String photo = siteUrl + season.photoPath();
            if (photo.equals(pet.get("photo_url"))) return false;
            jdbc.update("UPDATE pets SET photo_url = ? WHERE id = ?", photo, pet.get("id"));
            log.info("Shenron is dressed for {}", season == ShenronSeason.NONE ? "every day" : season.slug());
            if (season != ShenronSeason.NONE) {
                stories.save(new Story(team, photo, season.greeting()));
            }
            return true;
        } catch (Exception e) {
            log.warn("Shenron's outfit not changed: {}", e.getMessage());
            return false;
        }
    }
}
