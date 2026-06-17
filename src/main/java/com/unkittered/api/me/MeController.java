package com.unkittered.api.me;

import com.unkittered.api.auth.AuthDtos.UserDto;
import com.unkittered.api.me.MeDtos.UpdateProfileRequest;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** The signed-in user's own account and dating profile. */
@RestController
@RequestMapping("/v1/me")
@Tag(name = "Me")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final MeService me;

    public MeController(MeService me) {
        this.me = me;
    }

    @Operation(summary = "Get the signed-in account")
    @GetMapping
    public UserDto account() {
        return me.account(CurrentUser.id());
    }

    @Operation(summary = "Get the signed-in user's own profile")
    @GetMapping("/profile")
    public ProfileDto profile() {
        return me.profile(CurrentUser.id());
    }

    @Operation(summary = "Update the signed-in user's own profile")
    @PutMapping("/profile")
    public ProfileDto updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return me.updateProfile(CurrentUser.id(), req);
    }

    @Operation(summary = "Mark onboarding as complete")
    @PostMapping("/onboarding/complete")
    public UserDto completeOnboarding() {
        return me.completeOnboarding(CurrentUser.id());
    }
}
