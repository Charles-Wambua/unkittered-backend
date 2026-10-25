package com.unkittered.api.wallet;

import com.fasterxml.jackson.annotation.JsonValue;
import com.unkittered.api.common.ApiException;

import java.util.Arrays;

/** Gifts and Paws packs, with their prices. The server is the price list. */
public final class WalletCatalog {

    private WalletCatalog() { }

    // The share, fees and minimums live in PaymentsProperties (configurable).

    public enum Gift {
        ROSE("rose", "Rose", "🌹", 5),
        PAW("paw", "Paw", "🐾", 10),
        TREAT("treat", "Treat", "🦴", 20),
        COFFEE("coffee", "Coffee", "☕", 35),
        HEART("heart", "Heart", "❤️", 50),
        BOUQUET("bouquet", "Bouquet", "💐", 100),
        SUNSET_DATE("sunset_date", "Sunset date", "🌅", 250),
        CROWN("crown", "Crown", "👑", 500),
        DIAMOND("diamond", "Diamond", "💎", 1000);

        public final String id;
        public final String name;
        public final String emoji;
        public final int paws;

        Gift(String id, String name, String emoji, int paws) {
            this.id = id;
            this.name = name;
            this.emoji = emoji;
            this.paws = paws;
        }

        @JsonValue
        public String id() { return id; }

        public static Gift of(String id) {
            return Arrays.stream(values()).filter(g -> g.id.equals(id)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Unknown gift: " + id));
        }
    }

    /**
     * Paws packs. Bigger packs carry bonus Paws. Prices are per currency in
     * minor units (cents); the stores charge their own local price for the
     * matching product id, which must be set up the same in their consoles.
     */
    public enum PawsPack {
        //                          KES     USD  EUR  GBP
        STARTER("starter", 50, 0,    5000,   49,  49,  45),
        SMALL("small", 120, 10,     10000,   99,  99,  89),
        MEDIUM("medium", 300, 50,   25000,  199, 199, 179),
        LARGE("large", 650, 150,    50000,  399, 399, 349),
        HUGE("huge", 1400, 400,    100000,  799, 799, 699);

        public final String id;
        public final int paws;
        public final int bonus;
        private final java.util.Map<String, Integer> prices;

        PawsPack(String id, int paws, int bonus, int kes, int usd, int eur, int gbp) {
            this.id = id;
            this.paws = paws;
            this.bonus = bonus;
            this.prices = java.util.Map.of("KES", kes, "USD", usd, "EUR", eur, "GBP", gbp);
        }

        public int total() { return paws + bonus; }

        /** Price in minor units; unknown currencies fall back to USD. */
        public int priceMinor(String currency) {
            return prices.getOrDefault(currency, prices.get("USD"));
        }

        /** The whole-shilling price, for M-Pesa. */
        public int priceKes() { return prices.get("KES") / 100; }

        /** Product id in Google Play Console / App Store Connect. */
        public String storeProductId() { return "paws_" + id; }

        public static PawsPack of(String id) {
            return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Unknown Paws pack: " + id));
        }
    }
}
