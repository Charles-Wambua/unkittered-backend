package com.unkittered.api.wallet;

import com.unkittered.api.wallet.PaymentRules.Payout;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRulesTest {

    @Test
    void purchasesAreValuedInKesAfterTheProvidersCut() {
        // KES 1,000 over M-Pesa (no cut) → 100,000 cents.
        assertThat(PaymentRules.netKesCents(100_000, 1.0, 0)).isEqualTo(100_000);
        // $7.99 on Google Play at KES 130/$ less 15% → KES 882.90.
        assertThat(PaymentRules.netKesCents(799, 130.0, 0.15)).isEqualTo(88_289);
    }

    @Test
    void aGiftSpendsItsShareOfThePaidValue() {
        // 1,800 Paws bought for KES 1,000: a 1,000-Paw Diamond carries KES 555.55.
        assertThat(PaymentRules.giftValueCents(1000, 1800, 100_000)).isEqualTo(55_555);
        assertThat(PaymentRules.giftValueCents(10, 0, 100)).isZero();
    }

    @Test
    void netEarningsCanNeverExceedWhatWasPaid() {
        long value = PaymentRules.giftValueCents(1000, 1800, 100_000);
        int earned = PaymentRules.creatorEarnings(1000, value, 0.5, true);
        assertThat(earned).isEqualTo(277); // half of KES 555.55
        assertThat(earned * 100L).isLessThanOrEqualTo(value);
        // Free Paws (no paid value) earn nothing.
        assertThat(PaymentRules.creatorEarnings(1000, 0, 0.5, true)).isZero();
    }

    @Test
    void faceEarningsKeepTheOldRule() {
        assertThat(PaymentRules.creatorEarnings(5, 0, 0.5, false)).isEqualTo(2);
        assertThat(PaymentRules.creatorEarnings(1000, 0, 0.5, false)).isEqualTo(500);
    }

    @Test
    void withdrawalFeeIsAPercentageRoundedUpPlusAFixedPart() {
        var p = Payout.of(500, 0.15, 0);
        assertThat(p.fee()).isEqualTo(75);
        assertThat(p.payout()).isEqualTo(425);
        var odd = Payout.of(501, 0.15, 0);
        assertThat(odd.fee()).isEqualTo(76);
        assertThat(odd.fee() + odd.payout()).isEqualTo(501);
        var fixed = Payout.of(500, 0.10, 30);
        assertThat(fixed.fee()).isEqualTo(80);
        assertThat(fixed.payout()).isEqualTo(420);
        // The fee never exceeds the amount.
        assertThat(Payout.of(20, 0.10, 30).payout()).isZero();
    }
}
