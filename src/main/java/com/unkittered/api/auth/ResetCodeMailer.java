package com.unkittered.api.auth;

import com.unkittered.api.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Delivers password-reset codes by email (see {@link MailService}). Without an
 * SMTP host the code is only logged, and only when
 * {@code unkittered.auth.log-reset-codes=true} (local development).
 */
@Component
public class ResetCodeMailer {

    private static final Logger log = LoggerFactory.getLogger(ResetCodeMailer.class);

    private final MailService mail;
    private final boolean logCodes;

    public ResetCodeMailer(MailService mail,
                           @Value("${unkittered.auth.log-reset-codes:false}") boolean logCodes) {
        this.mail = mail;
        this.logCodes = logCodes;
    }

    public void sendResetCode(String email, String code) {
        if (logCodes) {
            log.info("[dev] Password reset code for {}: {}", email, code);
        }
        if (!mail.isEnabled()) {
            if (!logCodes) log.warn("Password reset requested but email is not configured");
            return;
        }
        mail.sendAsync(email, "Your Unkittered code: " + code, "Reset your password", List.of(
                "Here's the 6-digit code to reset your Unkittered password:",
                code,
                "It works for the next 15 minutes. If you didn't ask for this, you can ignore this email; "
                        + "your password won't change."), null);
    }
}
