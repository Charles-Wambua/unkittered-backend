package com.unkittered.api.wallet;

import com.unkittered.api.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which payment methods a member can use, from their country and platform
 * and the provider switches in {@link PaymentsProperties}.
 */
@Component
@EnableConfigurationProperties(PaymentsProperties.class)
public class PaymentOptions {

    public enum Mode { DISABLED, SANDBOX, LIVE }

    private static final Set<String> EURO = Set.of(
            "AT", "BE", "CY", "DE", "EE", "ES", "FI", "FR", "GR", "HR", "IE", "IT", "LT", "LU", "LV",
            "MT", "NL", "PT", "SI", "SK");

    private final PaymentsProperties props;
    /** The legacy switch, followed when the M-Pesa provider is set to "auto". */
    private final String legacyMpesaMode;

    public PaymentOptions(PaymentsProperties props,
                          @Value("${unkittered.mpesa.mode:disabled}") String legacyMpesaMode) {
        this.props = props;
        this.legacyMpesaMode = legacyMpesaMode;
    }

    public PaymentsProperties rules() { return props; }

    /** Normalised ISO country (defaults to Kenya, the launch market). */
    public static String country(String raw) {
        if (raw == null || raw.isBlank()) return "KE";
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /** The currency prices are shown in for a country. */
    public static String currencyFor(String country) {
        String c = country(country);
        if (c.equals("KE")) return "KES";
        if (c.equals("GB")) return "GBP";
        if (EURO.contains(c)) return "EUR";
        return "USD";
    }

    public Mode mode(PaymentMethod m) {
        PaymentsProperties.Provider p = settings(m);
        String mode = p == null ? "disabled" : p.getMode();
        if (m == PaymentMethod.MPESA && "auto".equalsIgnoreCase(mode)) {
            mode = switch (legacyMpesaMode.toLowerCase(Locale.ROOT)) {
                case "dev" -> "sandbox";
                case "daraja" -> "live";
                default -> "disabled";
            };
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "sandbox" -> Mode.SANDBOX;
            case "live" -> Mode.LIVE;
            default -> Mode.DISABLED;
        };
    }

    public double feePercent(PaymentMethod m) {
        PaymentsProperties.Provider p = settings(m);
        return p == null ? 0 : p.getFeePercent();
    }

    public double kesPer(String currency) {
        Double rate = props.getKesPer().get(currency);
        if (rate == null) throw new IllegalStateException("No KES rate for " + currency);
        return rate;
    }

    /** Ways to buy Paws in the app, in display order. */
    public List<PaymentMethod> topUpMethods(String country, String platform) {
        return Arrays.stream(PaymentMethod.values())
                .filter(m -> offered(m, country) && settings(m).isTopUps() && settings(m).isInApp()
                        && m.availableOn(platform))
                .toList();
    }

    /** Ways to withdraw earnings. */
    public List<PaymentMethod> payoutMethods(String country) {
        return Arrays.stream(PaymentMethod.values())
                .filter(m -> offered(m, country) && settings(m).isPayouts())
                .toList();
    }

    public void requireTopUp(PaymentMethod m, String country, String platform) {
        if (!topUpMethods(country, platform).contains(m)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    m.label() + " isn't available for buying Paws here.");
        }
    }

    public void requirePayout(PaymentMethod m, String country) {
        if (!payoutMethods(country).contains(m)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    m.label() + " isn't available for withdrawals here.");
        }
    }

    private boolean offered(PaymentMethod m, String country) {
        PaymentsProperties.Provider p = settings(m);
        if (p == null || mode(m) == Mode.DISABLED) return false;
        String c = country(country);
        return p.getCountries().stream().map(String::trim)
                .anyMatch(x -> x.equals("*") || x.equalsIgnoreCase(c));
    }

    private PaymentsProperties.Provider settings(PaymentMethod m) {
        return props.getProviders().get(m.configKey());
    }
}
