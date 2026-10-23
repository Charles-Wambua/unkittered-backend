package com.unkittered.api.discover;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MapFuzzTest {

    @Test
    void positionsAreBlurredButStableForAPerson() {
        UUID id = UUID.randomUUID();
        double lat = -1.29207;
        double fuzzed = DiscoverService.fuzz(lat, id, 1);
        // Same answer every time: no averaging attack over repeated looks.
        assertThat(DiscoverService.fuzz(lat, id, 1)).isEqualTo(fuzzed);
        // Within the person's ~2 km grid cell, and not their exact spot.
        double cell = Math.floor(lat / DiscoverService.GRID) * DiscoverService.GRID;
        assertThat(fuzzed).isBetween(cell, cell + DiscoverService.GRID);
        assertThat(fuzzed).isNotEqualTo(lat);
    }

    @Test
    void neighboursInTheSameCellDoNotStackOnOneSpot() {
        double lat = -1.2921;
        double a = DiscoverService.fuzz(lat, UUID.fromString("00000000-0000-0000-0000-000000000001"), 1);
        double b = DiscoverService.fuzz(lat, UUID.fromString("00000000-0000-0000-0000-0000000000ff"), 1);
        assertThat(a).isNotEqualTo(b);
    }
}
