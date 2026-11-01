package com.unkittered.api.discover;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceBandsTest {

    @Test
    void distancesAreBandsNotExactFigures() {
        assertThat(DiscoverService.coarseKm(0.2)).isEqualTo(0.5);
        assertThat(DiscoverService.coarseKm(0.99)).isEqualTo(0.5);
        assertThat(DiscoverService.coarseKm(1.0)).isEqualTo(1.0);
        assertThat(DiscoverService.coarseKm(3.2)).isEqualTo(4.0);
        assertThat(DiscoverService.coarseKm(3.9)).isEqualTo(4.0);
        assertThat(DiscoverService.coarseKm(12.1)).isEqualTo(15.0);
        assertThat(DiscoverService.coarseKm(51)).isEqualTo(60.0);
    }

    @Test
    void anUnknownLocationGivesNoDistanceRatherThanAMadeUpOne() {
        assertThat(DiscoverService.distanceKm(new com.unkittered.api.profile.Profile(), new com.unkittered.api.profile.Profile()))
                .isEqualTo(DiscoverService.UNKNOWN_DISTANCE);
        assertThat(DiscoverService.distanceKm(null, new com.unkittered.api.profile.Profile())).isNegative();
    }
}
