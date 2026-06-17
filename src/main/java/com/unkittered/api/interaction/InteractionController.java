package com.unkittered.api.interaction;

import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@Tag(name = "Interactions")
@SecurityRequirement(name = "bearerAuth")
public class InteractionController {

    private final InteractionService interactions;

    public InteractionController(InteractionService interactions) {
        this.interactions = interactions;
    }

    public record TargetRequest(@NotBlank String profileId) {
        UUID uuid() {
            try {
                return UUID.fromString(profileId);
            } catch (IllegalArgumentException e) {
                throw com.unkittered.api.common.ApiException.badRequest("Invalid profileId");
            }
        }
    }

    public record MatchResult(boolean isMatch) { }

    @Operation(summary = "Like a profile")
    @PostMapping("/likes")
    public MatchResult like(@RequestBody TargetRequest req) {
        boolean match = interactions.like(CurrentUser.id(), req.uuid(), false);
        return new MatchResult(match);
    }

    @Operation(summary = "Super-like a profile")
    @PostMapping("/super-likes")
    public MatchResult superLike(@RequestBody TargetRequest req) {
        boolean match = interactions.like(CurrentUser.id(), req.uuid(), true);
        return new MatchResult(match);
    }

    @Operation(summary = "Pass on a profile")
    @PostMapping("/passes")
    public void pass(@RequestBody TargetRequest req) {
        interactions.pass(CurrentUser.id(), req.uuid());
    }

    @Operation(summary = "Profiles that have liked the current user")
    @GetMapping("/likes/received")
    public Map<String, List<ProfileDto>> likesReceived() {
        return Map.of("profiles", interactions.whoLikedMe(CurrentUser.id()));
    }
}
