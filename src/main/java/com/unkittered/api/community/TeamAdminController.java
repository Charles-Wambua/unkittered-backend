package com.unkittered.api.community;

import com.unkittered.api.admin.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The admin dashboard's team tools: inbox, releases and Pet of the week. Admins only. */
@RestController
@RequestMapping("/v1/admin/team")
@Tag(name = "Admin")
@SecurityRequirement(name = "bearerAuth")
public class TeamAdminController {

    private final AdminService admin;
    private final TeamInboxService inbox;
    private final AppReleaseService releases;
    private final PetOfWeekService petOfWeek;

    public TeamAdminController(AdminService admin, TeamInboxService inbox, AppReleaseService releases,
                               PetOfWeekService petOfWeek) {
        this.admin = admin;
        this.inbox = inbox;
        this.releases = releases;
        this.petOfWeek = petOfWeek;
    }

    /** Checked before every handler, so no endpoint here can forget it. */
    @ModelAttribute
    void adminOnly() {
        admin.requireAdmin();
    }

    @Operation(summary = "Members' conversations with the team, unread first")
    @GetMapping("/inbox")
    public List<TeamInboxService.ConversationDto> inbox() {
        return inbox.inbox();
    }

    @Operation(summary = "One conversation (marks the member's messages read)")
    @GetMapping("/inbox/{matchId}")
    public TeamInboxService.ThreadDto thread(@PathVariable UUID matchId) {
        return inbox.thread(matchId);
    }

    public record ReplyRequest(String text) { }

    @Operation(summary = "Reply as the Unkittered team")
    @PostMapping("/inbox/{matchId}/reply")
    public Map<String, Boolean> reply(@PathVariable UUID matchId, @RequestBody ReplyRequest body) {
        inbox.reply(matchId, body == null ? null : body.text());
        return Map.of("ok", true);
    }

    @Operation(summary = "The release the in-app update banner points at")
    @GetMapping("/release")
    public ResponseEntity<AppReleaseService.ReleaseDto> release() {
        return releases.latest().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Publish a release: members on an older build see the update banner")
    @PutMapping("/release")
    public AppReleaseService.ReleaseDto saveRelease(@RequestBody AppReleaseService.ReleaseRequest body) {
        return releases.save(body);
    }

    @Operation(summary = "Which app version each member runs")
    @GetMapping("/versions")
    public AppReleaseService.VersionsDto versions() {
        return releases.versions();
    }

    public record PetOfWeekAdminDto(PetOfWeekService.WinnerDto current, List<PetOfWeekService.TallyDto> thisWeek) { }

    @Operation(summary = "The current Pet of the week and this week's nominations")
    @GetMapping("/pet-of-the-week")
    public PetOfWeekAdminDto petOfTheWeek() {
        return new PetOfWeekAdminDto(petOfWeek.current().orElse(null), petOfWeek.thisWeeksTally());
    }

    @Operation(summary = "Pick this week's winner now (it runs by itself every Monday at 9:00)")
    @PostMapping("/pet-of-the-week/run")
    public PetOfWeekService.WinnerDto runPetOfTheWeek() {
        return petOfWeek.pickNow();
    }
}
