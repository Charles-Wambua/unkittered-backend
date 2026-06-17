package com.unkittered.api.safety;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Block / unblock / report / unmatch — user safety controls. */
@RestController
@RequestMapping("/v1")
@Tag(name = "Safety")
@SecurityRequirement(name = "bearerAuth")
public class SafetyController {

    private final SafetyService safety;

    public SafetyController(SafetyService safety) {
        this.safety = safety;
    }

    public record BlockRequest(@NotBlank String profileId) { }

    public record ReportRequest(
            @NotBlank String profileId,
            @NotBlank @Size(max = 64) String reason,
            @Size(max = 2000) String details) {
    }

    @Operation(summary = "Block a user")
    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@Valid @RequestBody BlockRequest req) {
        safety.block(CurrentUser.id(), parse(req.profileId()));
    }

    @Operation(summary = "Unblock a user")
    @DeleteMapping("/blocks/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable String profileId) {
        safety.unblock(CurrentUser.id(), parse(profileId));
    }

    @Operation(summary = "List users the current user has blocked")
    @GetMapping("/blocks")
    public Map<String, List<ProfileDto>> blocked() {
        return Map.of("profiles", safety.blockedProfiles(CurrentUser.id()));
    }

    @Operation(summary = "Report a user for moderation")
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@Valid @RequestBody ReportRequest req) {
        safety.report(CurrentUser.id(), parse(req.profileId()), req.reason(), req.details());
    }

    @Operation(summary = "Unmatch (dissolve a match and its conversation)")
    @DeleteMapping("/matches/{matchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmatch(@PathVariable String matchId) {
        safety.unmatch(CurrentUser.id(), parse(matchId));
    }

    private UUID parse(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid id");
        }
    }
}
