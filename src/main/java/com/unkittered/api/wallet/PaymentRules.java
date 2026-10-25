package com.unkittered.api.wallet;

/**
 * The money maths, pure so it's unit-tested. Values are tracked in KES cents;
 * 1 earned Paw = KES 1.
 */
public final class PaymentRules {

    private PaymentRules() { }

    /**
     * What a purchase is worth to us in KES cents: the price converted to
     * KES, minus the provider's / store's cut.
     */
    public static long netKesCents(int amountMinor, double kesPerUnit, double feePercent) {
        // amountMinor is in hundredths of the currency, so it's already "cents".
        return (long) Math.floor(amountMinor * kesPerUnit * (1 - feePercent));
    }

    /** The share of the sender's paid value that a gift of {@code giftPaws} uses. */
    public static long giftValueCents(int giftPaws, int senderPaws, long senderValueCents) {
        if (senderPaws <= 0 || giftPaws <= 0) return 0;
        return senderValueCents * giftPaws / senderPaws;
    }

    /**
     * Earned Paws for the recipient. Net basis: their share of what the gift's
     * Paws were actually paid for. Face basis: their share of the Paws price.
     */
    public static int creatorEarnings(int giftPaws, long giftValueCents, double share, boolean net) {
        if (!net) return (int) Math.floor(giftPaws * share);
        return (int) Math.floor(giftValueCents / 100.0 * share);
    }

    /** Withdrawal fee (percentage rounded up, plus a fixed part) and what's paid out. */
    public record Payout(int paws, int fee, int payout) {
        public static Payout of(int paws, double feePercent, int feeFixed) {
            int fee = Math.min(paws, (int) Math.ceil(paws * feePercent) + Math.max(0, feeFixed));
            return new Payout(paws, fee, paws - fee);
        }
    }
}
