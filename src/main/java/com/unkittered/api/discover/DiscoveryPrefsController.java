package com.unkittered.api.discover;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.pet.PetEnums;
import com.unkittered.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The signed-in member's discovery filters ("who do you want to see?"). */
@RestController
@RequestMapping("/v1/me/discovery-prefs")
@Tag(name = "Discover")
@SecurityRequirement(name = "bearerAuth")
public class DiscoveryPrefsController {

    private final DiscoveryPrefsRepository prefs;
    private final DiscoverService discover;

    public DiscoveryPrefsController(DiscoveryPrefsRepository prefs, DiscoverService discover) {
        this.prefs = prefs;
        this.discover = discover;
    }

    public record PrefsDto(
            int ageMin,
            int ageMax,
            Integer maxDistanceKm,
            boolean verifiedOnly,
            boolean sterilisedOnly,
            boolean hasPetsOnly,
            List<PetEnums.Species> species,
            List<String> interests) {

        static PrefsDto from(DiscoveryPrefs p) {
            return new PrefsDto(p.getAgeMin(), p.getAgeMax(), p.getMaxDistanceKm(), p.isVerifiedOnly(),
                    p.isSterilisedOnly(), p.isHasPetsOnly(),
                    p.getSpecies().stream().map(PetEnums.Species::fromWire).sorted().toList(),
                    p.getInterests().stream().sorted().toList());
        }
    }

    public record UpdatePrefsRequest(
            @Min(18) @Max(99) int ageMin,
            @Min(18) @Max(99) int ageMax,
            @Min(1) @Max(500) Integer maxDistanceKm,
            boolean verifiedOnly,
            boolean sterilisedOnly,
            boolean hasPetsOnly,
            Set<PetEnums.Species> species,
            @Size(max = 20) Set<@Size(min = 1, max = 40) String> interests) {
    }

    @Operation(summary = "Get the caller's discovery filters")
    @GetMapping
    public PrefsDto get() {
        UUID me = CurrentUser.id();
        return PrefsDto.from(prefs.findById(me).orElseGet(() -> new DiscoveryPrefs(me)));
    }

    @Operation(summary = "Replace the caller's discovery filters")
    @PutMapping
    @Transactional
    public PrefsDto update(@Valid @RequestBody UpdatePrefsRequest req) {
        if (req.ageMin() > req.ageMax()) throw ApiException.badRequest("ageMin must be at most ageMax");
        UUID me = CurrentUser.id();
        DiscoveryPrefs p = prefs.findById(me).orElseGet(() -> new DiscoveryPrefs(me));
        p.setAgeMin(req.ageMin());
        p.setAgeMax(req.ageMax());
        p.setMaxDistanceKm(req.maxDistanceKm());
        p.setVerifiedOnly(req.verifiedOnly());
        p.setSterilisedOnly(req.sterilisedOnly());
        p.setHasPetsOnly(req.hasPetsOnly());
        p.setSpecies(req.species() == null ? new LinkedHashSet<>()
                : req.species().stream().map(PetEnums.Species::wire).collect(Collectors.toCollection(LinkedHashSet::new)));
        p.setInterests(req.interests() == null ? new LinkedHashSet<>()
                : req.interests().stream().map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new)));
        DiscoveryPrefs saved = prefs.save(p);
        discover.evict(me); // the cached deck no longer reflects the filters
        return PrefsDto.from(saved);
    }
}
