package com.unkittered.api.community;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Badges, Pet of the week and the latest app release, for members. */
@RestController
@RequestMapping("/v1")
@Tag(name = "Community")
public class CommunityController {

    private final BadgeService badges;
    private final PetOfWeekService petOfWeek;
    private final AppReleaseService releases;
    private final JdbcTemplate jdbc;

    public CommunityController(BadgeService badges, PetOfWeekService petOfWeek, AppReleaseService releases,
                               JdbcTemplate jdbc) {
        this.badges = badges;
        this.petOfWeek = petOfWeek;
        this.releases = releases;
        this.jdbc = jdbc;
    }

    @Operation(summary = "A member's badges (\"me\" for your own)")
    @GetMapping("/badges/{userId}")
    public List<BadgeService.BadgeDto> badges(@PathVariable String userId) {
        UUID viewer = CurrentUser.id();
        UUID target = "me".equals(userId) ? viewer : parse(userId);
        if (!target.equals(viewer)) {
            // Badges belong to the profile: hidden with it (suspended, or blocked either way).
            Boolean visible = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM users u WHERE u.id = ? AND NOT u.suspended)
                       AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = ? AND b.blocked_id = ?)
                                                              OR (b.blocker_id = ? AND b.blocked_id = ?))
                    """, Boolean.class, target, viewer, target, target, viewer);
            if (!Boolean.TRUE.equals(visible)) throw ApiException.notFound("Profile not found");
        }
        return badges.badgesOf(target);
    }

    @Operation(summary = "This week's Pet of the week and your nomination")
    @GetMapping("/pet-of-the-week")
    public PetOfWeekService.PetOfWeekDto petOfTheWeek() {
        return petOfWeek.forViewer(CurrentUser.id());
    }

    public record NominateRequest(String petId) { }

    @Operation(summary = "Nominate a pet for this week (replaces your earlier nomination)")
    @PostMapping("/pet-of-the-week/nominate")
    public PetOfWeekService.PetOfWeekDto nominate(@RequestBody NominateRequest body) {
        return petOfWeek.nominate(CurrentUser.id(), parse(body == null ? null : body.petId()));
    }

    @Operation(summary = "Withdraw your nomination this week")
    @DeleteMapping("/pet-of-the-week/nominate")
    public PetOfWeekService.PetOfWeekDto withdraw() {
        return petOfWeek.withdraw(CurrentUser.id());
    }

    @Operation(summary = "The latest app build (public; 204 when none is published)")
    @GetMapping("/app/release")
    public ResponseEntity<AppReleaseService.ReleaseDto> release() {
        return releases.latest().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (Exception e) {
            throw ApiException.notFound("Not found");
        }
    }
}
