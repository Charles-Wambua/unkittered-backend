package com.unkittered.api.security;

import com.unkittered.api.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    /** The placeholder secret that ships in the repo; never acceptable on a real server. */
    static final String DEV_SECRET = "dev-only-change-me-please-32bytes-minimum-secret";

    /** A session older than this gets a fresh token on its next request (see {@link #needsRenewal}). */
    static final Duration RENEW_AFTER = Duration.ofDays(1);

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(
            @Value("${unkittered.jwt.secret}") String secret,
            @Value("${unkittered.jwt.access-ttl-minutes}") long ttlMinutes,
            @Value("${unkittered.storage.public-base-url:}") String publicBaseUrl) {
        requireRealSecret(secret, publicBaseUrl);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * Anyone who knows the signing secret can sign in as anyone. The default is
     * in the source code, so a server reachable from outside refuses to start
     * with it (local development on localhost still can).
     */
    static void requireRealSecret(String secret, String publicBaseUrl) {
        boolean placeholder = secret == null || secret.isBlank() || secret.equals(DEV_SECRET)
                || secret.equalsIgnoreCase("CHANGE_ME");
        String base = publicBaseUrl == null ? "" : publicBaseUrl.toLowerCase();
        boolean local = base.isBlank() || base.contains("localhost") || base.contains("127.0.0.1")
                || base.contains("10.0.2.2");
        if (placeholder && !local) {
            throw new IllegalStateException("UNKITTERED_JWT_SECRET is not set (or is the example value). "
                    + "Set a long random secret before running on a public server.");
        }
    }

    /** A verified token: whose it is and when it was issued. */
    public record Session(UUID userId, Instant issuedAt) { }

    public String issue(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /** @return the session in the token, or throws if invalid/expired. */
    public Session parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Date iat = claims.getIssuedAt();
        return new Session(UUID.fromString(claims.getSubject()), iat == null ? Instant.EPOCH : iat.toInstant());
    }

    /** @return the user id encoded in the token, or throws if invalid/expired. */
    public UUID parseUserId(String token) {
        return parse(token).userId();
    }

    /**
     * Can this session still be used? Not if the account is gone or suspended,
     * or the token was issued before {@code tokensValidAfter} (a password reset
     * ends every session that existed before it).
     */
    public static boolean stillValid(User user, Instant issuedAt) {
        if (user == null || user.isSuspended()) return false;
        Instant cutoff = user.getTokensValidAfter();
        // Tokens carry whole seconds, so compare at that precision.
        return cutoff == null || !issuedAt.isBefore(cutoff.truncatedTo(ChronoUnit.SECONDS));
    }

    /** Members who use the app keep a fresh token, so they're never signed out mid-use. */
    public static boolean needsRenewal(Instant issuedAt) {
        return issuedAt.isBefore(Instant.now().minus(RENEW_AFTER));
    }
}
