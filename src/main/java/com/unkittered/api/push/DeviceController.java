package com.unkittered.api.push;

import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Registers the caller's FCM device token so the backend can push to them. */
@RestController
@RequestMapping("/v1/devices")
@Tag(name = "Devices")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {

    private final DeviceTokenService devices;

    public DeviceController(DeviceTokenService devices) {
        this.devices = devices;
    }

    public record RegisterRequest(@NotBlank String token, String platform) { }

    @Operation(summary = "Register/refresh this device's push token")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody RegisterRequest req) {
        devices.register(CurrentUser.id(), req.token().trim(), req.platform());
    }

    @Operation(summary = "Remove a device token (on sign-out)")
    @DeleteMapping("/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable String token) {
        devices.unregister(token);
    }
}
