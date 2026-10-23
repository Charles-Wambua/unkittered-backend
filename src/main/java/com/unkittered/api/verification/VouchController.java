package com.unkittered.api.verification;

import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** "Get vouched for": share a code, or vouch for a friend with theirs. */
@RestController
@RequestMapping("/v1")
@Tag(name = "Verification")
@SecurityRequirement(name = "bearerAuth")
public class VouchController {

    private final VouchService vouches;

    public VouchController(VouchService vouches) {
        this.vouches = vouches;
    }

    @Operation(summary = "My open vouch code (or a new one), valid for a week")
    @PostMapping("/me/vouch-invites")
    public VouchService.Invite invite() {
        return vouches.invite(CurrentUser.id());
    }

    @Operation(summary = "Whose code is this? Shown before vouching (404 if expired or used)")
    @GetMapping("/vouch-invites/{code}")
    public VouchService.Preview preview(@PathVariable String code) {
        return vouches.preview(CurrentUser.id(), code);
    }

    @Operation(summary = "Vouch for the code's owner (verified members only)")
    @PostMapping("/vouch-invites/{code}/accept")
    public VouchService.Preview accept(@PathVariable String code) {
        return vouches.accept(CurrentUser.id(), code);
    }
}
