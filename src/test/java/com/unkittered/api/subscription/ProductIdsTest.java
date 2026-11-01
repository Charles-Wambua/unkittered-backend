package com.unkittered.api.subscription;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductIdsTest {

    @Test
    void onlyTheProductsTheAppSellsGiveATier() {
        assertThat(SubscriptionService.tierFromProductId("unkittered_gold_monthly")).isEqualTo("gold");
        assertThat(SubscriptionService.tierFromProductId("unkittered_plus_annual")).isEqualTo("plus");
        assertThat(SubscriptionService.tierFromProductId("free_goldfish")).isEqualTo("free");
        assertThat(SubscriptionService.tierFromProductId("unkittered_gold_forever")).isEqualTo("free");
        assertThat(SubscriptionService.tierFromProductId(null)).isEqualTo("free");
    }
}
