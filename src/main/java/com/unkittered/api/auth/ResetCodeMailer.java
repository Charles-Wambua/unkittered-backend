package com.unkittered.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Delivers password-reset codes. There is no email provider wired up yet, so
 * this implementation only logs: the code itself is logged ONLY when
 * {@code unkittered.auth.log-reset-codes=true} (local development). Replace
 * with an SMTP / transactional-email sender before production.
 */
@Component
public class ResetCodeMailer {

    private static final Logger log = LoggerFactory.getLogger(ResetCodeMailer.class);

    private final boolean logCodes;

    public ResetCodeMailer(@Value("${unkittered.auth.log-reset-codes:false}") boolean logCodes) {
        this.logCodes = logCodes;
    }

    public void sendResetCode(String email, String code) {
        if (logCodes) {
            log.info("[dev] Password reset code for {}: {}", email, code);
        } else {
            log.warn("Password reset requested but no email sender is configured");
        }
    }
}
