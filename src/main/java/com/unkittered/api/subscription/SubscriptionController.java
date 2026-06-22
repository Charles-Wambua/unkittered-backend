package com.unkittered.api.subscription;

import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Membership / entitlement endpoints. The platform purchase itself happens
 * on-device via {@code in_app_purchase}; here we verify and persist the tier.
 */
@RestController
@RequestMapping("/v1/subscriptions")
@Tag(name = "Subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    public record VerifyRequest(@NotBlank String productId) {
        String trimmed() { return productId.trim(); }
    }

    public record TierResponse(String tier) { }

    public record BoostResponse(String boostedUntil, int boostsRemaining) { }

    @Operation(summary = "Get the current user's membership tier")
    @GetMapping("/me")
    public TierResponse me() {
        return new TierResponse(subscriptions.tierOf(CurrentUser.id()));
    }

    @Operation(summary = "Activate a profile Boost (Plus & Gold)")
    @PostMapping("/boost")
    public BoostResponse boost() {
        var r = subscriptions.boost(CurrentUser.id());
        return new BoostResponse(r.boostedUntil().toString(), r.boostsRemaining());
    }

    @Operation(summary = "Verify a store purchase and activate the matching tier")
    @PostMapping("/verify")
    public TierResponse verify(@Valid @RequestBody VerifyRequest req) {
        return new TierResponse(subscriptions.verifyAndActivate(CurrentUser.id(), req.trimmed()));
    }

    @Operation(summary = "Cancel the subscription (downgrade to free)")
    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel() {
        subscriptions.cancel(CurrentUser.id());
    }
}
