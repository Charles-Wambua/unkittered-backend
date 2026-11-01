package com.unkittered.api.security;

import com.unkittered.api.user.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionRulesTest {

    @Test
    void aPublicServerRefusesTheExampleSecret() {
        assertThatThrownBy(() -> JwtService.requireRealSecret(JwtService.DEV_SECRET, "https://api.example.com"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtService.requireRealSecret("CHANGE_ME", "https://api.example.com"))
                .isInstanceOf(IllegalStateException.class);
        JwtService.requireRealSecret(JwtService.DEV_SECRET, "http://localhost:8081");
        JwtService.requireRealSecret(JwtService.DEV_SECRET, "");
        JwtService.requireRealSecret("a-long-random-secret-that-is-at-least-32-bytes", "https://api.example.com");
    }

    @Test
    void aPasswordResetEndsOlderSessions() {
        User u = new User();
        Instant reset = Instant.now();
        u.setTokensValidAfter(reset);
        assertThat(JwtService.stillValid(u, reset.minus(Duration.ofMinutes(5)))).isFalse();
        assertThat(JwtService.stillValid(u, reset.truncatedTo(ChronoUnit.SECONDS))).isTrue();
        assertThat(JwtService.stillValid(u, reset.plusSeconds(10))).isTrue();
    }

    @Test
    void suspendedOrMissingAccountsHaveNoSession() {
        User u = new User();
        assertThat(JwtService.stillValid(u, Instant.now())).isTrue();
        u.setSuspended(true);
        assertThat(JwtService.stillValid(u, Instant.now())).isFalse();
        assertThat(JwtService.stillValid(null, Instant.now())).isFalse();
    }

    @Test
    void dayOldSessionsAreRenewed() {
        assertThat(JwtService.needsRenewal(Instant.now().minus(Duration.ofHours(2)))).isFalse();
        assertThat(JwtService.needsRenewal(Instant.now().minus(Duration.ofHours(25)))).isTrue();
    }
}
