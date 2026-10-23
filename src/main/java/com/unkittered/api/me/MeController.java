package com.unkittered.api.me;

import com.unkittered.api.auth.AuthDtos.UserDto;
import com.unkittered.api.me.MeDtos.DeleteAccountRequest;
import com.unkittered.api.me.MeDtos.UpdateProfileRequest;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    /** Device location, used for distances and the map. */
    public record LocationRequest(
            @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90") double latitude,
            @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180") double longitude) { }

    @io.swagger.v3.oas.annotations.Operation(summary = "Update my location (stored to ~100 m; never shown exactly)")
    @PutMapping("/location")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void updateLocation(@jakarta.validation.Valid @RequestBody LocationRequest req) {
        me.updateLocation(com.unkittered.api.security.CurrentUser.id(), req.latitude(), req.longitude());
    }

    @Operation(summary = "Update the signed-in user's own profile")
    @PutMapping("/profile")
    public ProfileDto updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return me.updateProfile(CurrentUser.id(), req);
    }

    @Operation(summary = "Permanently delete the signed-in account and all its data")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@RequestBody(required = false) DeleteAccountRequest req) {
        me.deleteAccount(CurrentUser.id(), req);
    }

    @Operation(summary = "Mark onboarding as complete")
    @PostMapping("/onboarding/complete")
    public UserDto completeOnboarding() {
        return me.completeOnboarding(CurrentUser.id());
    }
}
