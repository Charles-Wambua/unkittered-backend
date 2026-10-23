package com.unkittered.api.auth;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetCodeRepository codes = mock(PasswordResetCodeRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final ResetCodeMailer mailer = mock(ResetCodeMailer.class);
    private final AtomicReference<PasswordResetCode> stored = new AtomicReference<>();
    private User user;

    private PasswordResetService service(Instant at) {
        return new PasswordResetService(users, codes, encoder, mailer, Clock.fixed(at, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("amara@example.com");
        user.setPasswordHash(encoder.encode("old-password"));
        when(users.findByEmailIgnoreCase("amara@example.com")).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
        when(codes.save(any())).thenAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(codes.findById(any())).thenAnswer(inv -> Optional.ofNullable(stored.get()));
    }

    private String requestAndCaptureCode() {
        service(NOW).requestCode(" Amara@Example.com ");
        var code = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendResetCode(anyString(), code.capture());
        return code.getValue();
    }

    @Test
    void sendsASixDigitCodeAndStoresOnlyItsHash() {
        String code = requestAndCaptureCode();
        assertThat(code).matches("\\d{6}");
        assertThat(stored.get().getCodeHash()).isNotEqualTo(code);
        assertThat(encoder.matches(code, stored.get().getCodeHash())).isTrue();
    }

    @Test
    void unknownEmailIsSilent() {
        service(NOW).requestCode("nobody@example.com");
        verifyNoInteractions(mailer);
    }

    @Test
    void correctCodeSetsThePasswordOnce() {
        String code = requestAndCaptureCode();
        User result = service(NOW.plusSeconds(60)).reset("amara@example.com", code, "new-password-1");
        assertThat(encoder.matches("new-password-1", result.getPasswordHash())).isTrue();
        verify(codes).delete(any());
    }

    @Test
    void wrongCodeCountsAnAttemptAndFiveBurnIt() {
        String code = requestAndCaptureCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < PasswordResetService.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service(NOW).reset("amara@example.com", wrong, "new-password-1"))
                    .isInstanceOf(ApiException.class);
        }
        // Even the right code no longer works.
        assertThatThrownBy(() -> service(NOW).reset("amara@example.com", code, "new-password-1"))
                .isInstanceOf(ApiException.class);
        assertThat(encoder.matches("old-password", user.getPasswordHash())).isTrue();
    }

    @Test
    void expiredCodeIsRejected() {
        String code = requestAndCaptureCode();
        Instant later = NOW.plus(PasswordResetService.TTL).plus(Duration.ofSeconds(1));
        assertThatThrownBy(() -> service(later).reset("amara@example.com", code, "new-password-1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void resendIsThrottled() {
        requestAndCaptureCode();
        service(NOW.plusSeconds(10)).requestCode("amara@example.com");
        verify(mailer, times(1)).sendResetCode(anyString(), anyString());
    }
}
