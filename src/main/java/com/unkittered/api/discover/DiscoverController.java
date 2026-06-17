package com.unkittered.api.discover;

import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/discover")
@Tag(name = "Discover")
@SecurityRequirement(name = "bearerAuth")
public class DiscoverController {

    private final DiscoverService discoverService;

    public DiscoverController(DiscoverService discoverService) {
        this.discoverService = discoverService;
    }

    @Operation(summary = "Get the discover deck for the current user")
    @GetMapping
    public Map<String, List<ProfileDto>> discover() {
        return Map.of("profiles", discoverService.deckFor(CurrentUser.id()));
    }
}
