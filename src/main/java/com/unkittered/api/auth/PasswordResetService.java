package com.unkittered.api.auth;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Forgot-password flow: email → one-time 6-digit code → new password.
 *
 * <ul>
 *   <li>Requests always "succeed", so the endpoint can't be used to discover
 *       which emails have accounts.</li>
 *   <li>Only a hash of the code is stored; it expires after 15 minutes and is
 *       burnt after 5 wrong guesses.</li>
 *   <li>A new code can't be requested more than once a minute.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(15);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private static final String BAD_CODE = "That code isn't right or has expired. Request a new one.";

    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final PasswordEncoder encoder;
    private final ResetCodeMailer mailer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetCodeRepository codes,
                                PasswordEncoder encoder, ResetCodeMailer mailer, Clock clock) {
        this.users = users;
        this.codes = codes;
        this.encoder = encoder;
        this.mailer = mailer;
        this.clock = clock;
    }

    @Transactional
    public void requestCode(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) return;

        Instant now = clock.instant();
        var existing = codes.findById(user.getId());
        if (existing.isPresent() && existing.get().getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            return;
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.save(new PasswordResetCode(user.getId(), encoder.encode(code), now.plus(TTL), now));
        mailer.sendResetCode(email, code);
    }

    /** Checks the code and sets the new password. Returns the account. */
    @Transactional(noRollbackFor = ApiException.class)
    public User reset(String rawEmail, String code, String newPassword) {
        String email = rawEmail.trim().toLowerCase();
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.badRequest(BAD_CODE));
        PasswordResetCode pending = codes.findById(user.getId())
                .orElseThrow(() -> ApiException.badRequest(BAD_CODE));

        if (pending.getExpiresAt().isBefore(clock.instant()) || pending.getAttempts() >= MAX_ATTEMPTS) {
            codes.delete(pending);
            throw ApiException.badRequest(BAD_CODE);
        }
        if (!encoder.matches(code, pending.getCodeHash())) {
            pending.recordFailedAttempt();
            codes.save(pending);
            throw ApiException.badRequest(BAD_CODE);
        }

        user.setPasswordHash(encoder.encode(newPassword));
        users.save(user);
        codes.delete(pending);
        return user;
    }
}
