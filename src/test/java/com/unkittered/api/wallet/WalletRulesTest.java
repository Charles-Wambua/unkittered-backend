package com.unkittered.api.wallet;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.wallet.WalletCatalog.Gift;
import com.unkittered.api.wallet.WalletCatalog.PawsPack;
import com.unkittered.api.wallet.WalletCatalog.Payout;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletRulesTest {

    @Test
    void recipientsKeepHalfOfAGift() {
        assertThat(Gift.ROSE.creatorShare()).isEqualTo(2); // 5 → floor(2.5)
        assertThat(Gift.BOUQUET.creatorShare()).isEqualTo(50);
        assertThat(Gift.DIAMOND.creatorShare()).isEqualTo(500);
    }

    @Test
    void withdrawalFeeRoundsUpSoTheHouseNeverPaysFractions() {
        var p = Payout.of(500);
        assertThat(p.feeKes()).isEqualTo(75);
        assertThat(p.payoutKes()).isEqualTo(425);
        var odd = Payout.of(501);
        assertThat(odd.feeKes() + odd.payoutKes()).isEqualTo(501);
        assertThat(odd.feeKes()).isEqualTo(76);
    }

    @Test
    void biggerPacksCarryBonusPaws() {
        assertThat(PawsPack.STARTER.total()).isEqualTo(50);
        assertThat(PawsPack.HUGE.total()).isEqualTo(1800);
        assertThat(PawsPack.HUGE.priceKes).isEqualTo(1000);
    }

    @Test
    void phonesNormaliseToTheDarajaFormat() {
        assertThat(MpesaGateway.normalisePhone("0712 345 678")).isEqualTo("254712345678");
        assertThat(MpesaGateway.normalisePhone("+254 110 123 456")).isEqualTo("254110123456");
        assertThat(MpesaGateway.normalisePhone("712345678")).isEqualTo("254712345678");
        assertThatThrownBy(() -> MpesaGateway.normalisePhone("12345")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MpesaGateway.normalisePhone("+1 415 555 0100")).isInstanceOf(ApiException.class);
    }

    @Test
    void unknownCatalogueIdsAreRejected() {
        assertThatThrownBy(() -> Gift.of("yacht")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PawsPack.of("free")).isInstanceOf(ApiException.class);
    }
}
