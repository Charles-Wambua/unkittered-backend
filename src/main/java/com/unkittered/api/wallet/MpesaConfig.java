package com.unkittered.api.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Picks the M-Pesa gateway from {@code unkittered.mpesa.mode}:
 * <ul>
 *   <li>{@code disabled} (default) — top-ups and withdrawals answer 503;</li>
 *   <li>{@code dev} — LOCAL TESTING ONLY: every payment "succeeds" at once;</li>
 *   <li>{@code daraja} — Safaricom Daraja (sandbox or production URL).</li>
 * </ul>
 */
@Configuration
public class MpesaConfig {

    private static final Logger log = LoggerFactory.getLogger(MpesaConfig.class);

    @Bean
    MpesaGateway mpesaGateway(
            @Value("${unkittered.mpesa.mode:disabled}") String mode,
            @Value("${unkittered.mpesa.base-url:https://sandbox.safaricom.co.ke}") String baseUrl,
            @Value("${unkittered.mpesa.consumer-key:}") String consumerKey,
            @Value("${unkittered.mpesa.consumer-secret:}") String consumerSecret,
            @Value("${unkittered.mpesa.shortcode:}") String shortcode,
            @Value("${unkittered.mpesa.passkey:}") String passkey,
            @Value("${unkittered.mpesa.callback-base-url:}") String callbackBaseUrl,
            @Value("${unkittered.mpesa.callback-token:}") String callbackToken,
            @Value("${unkittered.mpesa.b2c-shortcode:}") String b2cShortcode,
            @Value("${unkittered.mpesa.initiator-name:}") String initiatorName,
            @Value("${unkittered.mpesa.security-credential:}") String securityCredential) {
        return switch (mode.toLowerCase()) {
            case "dev" -> {
                log.warn("M-Pesa is in DEV mode: payments succeed without any money moving.");
                yield new MpesaGateway() {
                    public boolean enabled() { return true; }
                    public Request collect(String phone, int amountKes, String reference, String description) {
                        return new Request("dev-" + UUID.randomUUID(), true);
                    }
                    public Request payout(String phone, int amountKes, String reference) {
                        return new Request("dev-" + UUID.randomUUID(), true);
                    }
                };
            }
            case "daraja" -> new DarajaMpesaGateway(baseUrl, consumerKey, consumerSecret, shortcode, passkey,
                    callbackBaseUrl, callbackToken, b2cShortcode, initiatorName, securityCredential);
            default -> new MpesaGateway() {
                public boolean enabled() { return false; }
                public Request collect(String phone, int amountKes, String reference, String description) {
                    throw new IllegalStateException("M-Pesa disabled");
                }
                public Request payout(String phone, int amountKes, String reference) {
                    throw new IllegalStateException("M-Pesa disabled");
                }
            };
        };
    }
}
