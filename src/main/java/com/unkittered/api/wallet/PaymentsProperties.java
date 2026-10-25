package com.unkittered.api.wallet;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Money rules and payment providers, all set in the environment (see
 * {@code application.yml} under {@code unkittered.payments}) so they can be
 * tuned without a release.
 */
@ConfigurationProperties(prefix = "unkittered.payments")
public class PaymentsProperties {

    /** Share of a gift's value the recipient keeps as earned Paws. */
    private double creatorShare = 0.5;

    /** Withdrawal fee: this fraction of the amount withdrawn… */
    private double withdrawalFeePercent = 0.15;

    /** …plus this fixed amount, in earned Paws (1 earned Paw = KES 1). */
    private int withdrawalFeeFixed = 0;

    /** Smallest withdrawal, in earned Paws. */
    private int minWithdrawal = 500;

    /** Days a gift's earnings wait before they can be withdrawn (refunds, chargebacks). */
    private int holdDays = 7;

    /**
     * {@code net}: earnings come from what was actually paid for the Paws,
     * after store/provider fees, so payouts can never exceed income.
     * {@code face}: 1 Paw is worth KES 1 whatever was paid (the old rule).
     */
    private String earningsBasis = "net";

    /** KES per unit of each currency, to value purchases in other currencies. */
    private Map<String, Double> kesPer = new HashMap<>(Map.of(
            "KES", 1.0, "USD", 130.0, "EUR", 140.0, "GBP", 165.0));

    /** Keyed by {@link PaymentMethod#configKey()}. */
    private Map<String, Provider> providers = new HashMap<>();

    public static class Provider {
        /** disabled | sandbox (no money moves) | live; M-Pesa also takes auto. */
        private String mode = "disabled";
        /** ISO country codes this provider is offered in; "*" = everywhere. */
        private List<String> countries = List.of("*");
        private boolean topUps;
        private boolean payouts;
        /**
         * Whether it may be offered inside the mobile app for buying Paws.
         * Store rules generally require Google Play / App Store billing for
         * in-app currency, so others belong on a web top-up page.
         */
        private boolean inApp = true;
        /** The provider's (or store's) cut of a purchase, e.g. 0.15. */
        private double feePercent;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public List<String> getCountries() { return countries; }
        public void setCountries(List<String> countries) { this.countries = countries; }
        public boolean isTopUps() { return topUps; }
        public void setTopUps(boolean topUps) { this.topUps = topUps; }
        public boolean isPayouts() { return payouts; }
        public void setPayouts(boolean payouts) { this.payouts = payouts; }
        public boolean isInApp() { return inApp; }
        public void setInApp(boolean inApp) { this.inApp = inApp; }
        public double getFeePercent() { return feePercent; }
        public void setFeePercent(double feePercent) { this.feePercent = feePercent; }
    }

    public double getCreatorShare() { return creatorShare; }
    public void setCreatorShare(double v) { this.creatorShare = v; }
    public double getWithdrawalFeePercent() { return withdrawalFeePercent; }
    public void setWithdrawalFeePercent(double v) { this.withdrawalFeePercent = v; }
    public int getWithdrawalFeeFixed() { return withdrawalFeeFixed; }
    public void setWithdrawalFeeFixed(int v) { this.withdrawalFeeFixed = v; }
    public int getMinWithdrawal() { return minWithdrawal; }
    public void setMinWithdrawal(int v) { this.minWithdrawal = v; }
    public int getHoldDays() { return holdDays; }
    public void setHoldDays(int v) { this.holdDays = v; }
    public String getEarningsBasis() { return earningsBasis; }
    public void setEarningsBasis(String v) { this.earningsBasis = v; }
    public Map<String, Double> getKesPer() { return kesPer; }
    public void setKesPer(Map<String, Double> v) { this.kesPer = v; }
    public Map<String, Provider> getProviders() { return providers; }
    public void setProviders(Map<String, Provider> v) { this.providers = v; }

    public boolean netEarnings() { return !"face".equalsIgnoreCase(earningsBasis); }
}
