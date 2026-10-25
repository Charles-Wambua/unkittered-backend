package com.unkittered.api.wallet;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.wallet.WalletCatalog.Gift;
import com.unkittered.api.wallet.WalletCatalog.PawsPack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletRulesTest {

    @Test
    void biggerPacksCarryBonusPaws() {
        assertThat(PawsPack.STARTER.total()).isEqualTo(50);
        assertThat(PawsPack.HUGE.total()).isEqualTo(1800);
        assertThat(PawsPack.HUGE.priceKes()).isEqualTo(1000);
        assertThat(PawsPack.HUGE.priceMinor("USD")).isEqualTo(799);
        assertThat(PawsPack.HUGE.priceMinor("JPY")).isEqualTo(799); // unknown → USD
        assertThat(PawsPack.SMALL.storeProductId()).isEqualTo("paws_small");
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
