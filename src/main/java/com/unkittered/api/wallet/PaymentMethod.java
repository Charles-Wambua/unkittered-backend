package com.unkittered.api.wallet;

import com.fasterxml.jackson.annotation.JsonValue;
import com.unkittered.api.common.ApiException;

import java.util.Arrays;

/**
 * Every way money can come in (top-ups) or go out (payouts). Which ones a
 * member sees depends on {@link PaymentsProperties} and their country.
 */
public enum PaymentMethod {
    MPESA("mpesa", "mpesa", "M-Pesa", "mobile_money", Needs.PHONE, Platform.ANY),
    GOOGLE_PLAY("google_play", "google-play", "Google Play", "store", Needs.STORE_PURCHASE, Platform.ANDROID),
    APP_STORE("app_store", "app-store", "App Store", "store", Needs.STORE_PURCHASE, Platform.IOS),
    CARD("card", "card", "Card", "card", Needs.NOTHING, Platform.ANY),
    PAYPAL("paypal", "paypal", "PayPal", "wallet", Needs.EMAIL, Platform.ANY),
    BANK("bank", "bank", "Bank transfer", "bank", Needs.ACCOUNT, Platform.ANY);

    /** What the member has to provide. */
    public enum Needs { PHONE, EMAIL, ACCOUNT, STORE_PURCHASE, NOTHING }

    public enum Platform { ANY, ANDROID, IOS }

    private final String wire;
    private final String configKey;
    private final String label;
    private final String kind;
    private final Needs needs;
    private final Platform platform;

    PaymentMethod(String wire, String configKey, String label, String kind, Needs needs, Platform platform) {
        this.wire = wire;
        this.configKey = configKey;
        this.label = label;
        this.kind = kind;
        this.needs = needs;
        this.platform = platform;
    }

    @JsonValue
    public String wire() { return wire; }
    public String configKey() { return configKey; }
    public String label() { return label; }
    public String kind() { return kind; }
    public Needs needs() { return needs; }

    /** Store billing only exists on its own platform. */
    public boolean availableOn(String clientPlatform) {
        return switch (platform) {
            case ANY -> true;
            case ANDROID -> "android".equalsIgnoreCase(clientPlatform);
            case IOS -> "ios".equalsIgnoreCase(clientPlatform);
        };
    }

    public static PaymentMethod of(String wire) {
        if (wire == null || wire.isBlank()) return MPESA; // older clients
        return Arrays.stream(values()).filter(m -> m.wire.equalsIgnoreCase(wire)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown payment method: " + wire));
    }
}
