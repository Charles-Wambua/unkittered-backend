package com.unkittered.api.wallet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MpesaCallbackTest {

    @Test
    void readsAmountAndPhoneFromAnStkCallback() {
        Map<String, Object> cb = Map.of("CallbackMetadata", Map.of("Item", List.of(
                Map.of("Name", "Amount", "Value", 150.0),
                Map.of("Name", "MpesaReceiptNumber", "Value", "QWE123"),
                Map.of("Name", "PhoneNumber", "Value", 254712345678L))));
        assertThat(MpesaCallbackController.metadata(cb, "Amount")).isEqualTo(150.0);
        assertThat(MpesaCallbackController.metadata(cb, "PhoneNumber")).isEqualTo(254712345678L);
        assertThat(MpesaCallbackController.metadata(cb, "Missing")).isNull();
        assertThat(MpesaCallbackController.metadata(Map.of(), "Amount")).isNull();
    }

    @Test
    void sandboxTopUpsStopOnceRealPayoutsAreOn() {
        assertThat(options("sandbox", "sandbox").realPayoutsOn()).isFalse();
        assertThat(options("sandbox", "live").realPayoutsOn()).isTrue();
        assertThat(options("live", "disabled").realPayoutsOn()).isFalse();
    }

    /** Google Play sells Paws; M-Pesa pays out. */
    private static PaymentOptions options(String playMode, String mpesaMode) {
        PaymentsProperties props = new PaymentsProperties();
        PaymentsProperties.Provider play = new PaymentsProperties.Provider();
        play.setMode(playMode);
        play.setTopUps(true);
        PaymentsProperties.Provider mpesa = new PaymentsProperties.Provider();
        mpesa.setMode(mpesaMode);
        mpesa.setPayouts(true);
        props.getProviders().put(PaymentMethod.GOOGLE_PLAY.configKey(), play);
        props.getProviders().put(PaymentMethod.MPESA.configKey(), mpesa);
        return new PaymentOptions(props, "disabled");
    }
}
