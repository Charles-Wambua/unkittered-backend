package com.unkittered.api.profile;

import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Another member's profile, by id. */
@RestController
@RequestMapping("/v1/profiles")
@Tag(name = "Profiles")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final ProfileViewService views;

    public ProfileController(ProfileViewService views) {
        this.views = views;
    }

    @Operation(summary = "One profile as the current user sees it (same shape as a deck card)",
            description = "404 when the id is unknown, the account is suspended or deleted, either side has "
                    + "blocked the other, or the member is paused or incognito and has no tie to the viewer.")
    @GetMapping("/{id}")
    public ProfileDto profile(@PathVariable String id) {
        return views.view(CurrentUser.id(), id);
    }
}
