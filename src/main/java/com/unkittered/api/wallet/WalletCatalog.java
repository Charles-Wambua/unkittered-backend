package com.unkittered.api.wallet;

import com.fasterxml.jackson.annotation.JsonValue;
import com.unkittered.api.common.ApiException;

import java.util.Arrays;

/** Gifts and Paws packs, with their prices. The server is the price list. */
public final class WalletCatalog {

    private WalletCatalog() { }

    /** Share of a gift's price the recipient keeps as withdrawable earnings. */
    public static final double CREATOR_SHARE = 0.5;

    /** M-Pesa withdrawal fee, taken from the amount withdrawn. */
    public static final double WITHDRAWAL_FEE = 0.15;

    /** Minimum earned Paws per withdrawal. 1 earned Paw = KES 1. */
    public static final int MIN_WITHDRAWAL = 500;

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

        /** What the recipient earns. */
        public int creatorShare() { return (int) Math.floor(paws * CREATOR_SHARE); }

        public static Gift of(String id) {
            return Arrays.stream(values()).filter(g -> g.id.equals(id)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Unknown gift: " + id));
        }
    }

    /** Paws packs bought with M-Pesa. Bigger packs carry bonus Paws. */
    public enum PawsPack {
        STARTER("starter", 50, 0, 50),
        SMALL("small", 120, 10, 100),
        MEDIUM("medium", 300, 50, 250),
        LARGE("large", 650, 150, 500),
        HUGE("huge", 1400, 400, 1000);

        public final String id;
        public final int paws;
        public final int bonus;
        public final int priceKes;

        PawsPack(String id, int paws, int bonus, int priceKes) {
            this.id = id;
            this.paws = paws;
            this.bonus = bonus;
            this.priceKes = priceKes;
        }

        public int total() { return paws + bonus; }

        public static PawsPack of(String id) {
            return Arrays.stream(values()).filter(p -> p.id.equals(id)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Unknown Paws pack: " + id));
        }
    }

    /** Withdrawal maths: fee rounded up, payout what's left. */
    public record Payout(int paws, int feeKes, int payoutKes) {
        public static Payout of(int paws) {
            int fee = (int) Math.ceil(paws * WITHDRAWAL_FEE);
            return new Payout(paws, fee, paws - fee);
        }
    }
}
