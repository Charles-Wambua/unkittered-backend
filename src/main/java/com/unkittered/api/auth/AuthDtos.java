package com.unkittered.api.auth;

import com.unkittered.api.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request/response payloads for the auth endpoints. */
public final class AuthDtos {

    private AuthDtos() { }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
            @NotBlank String displayName) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    /**
     * Google / Apple sign-in. {@code idToken} is the provider's signed ID token;
     * the server verifies it. {@code displayName} is only used for a new
     * account (Apple shares the name once, on first sign-in, outside the token).
     */
    public record OAuthRequest(
            @NotBlank @Pattern(regexp = "google|apple", message = "must be google or apple") String provider,
            @NotBlank String idToken,
            @Size(max = 120) String displayName) {
    }

    /** Step 1 of forgot-password: email me a code. */
    public record ForgotPasswordRequest(@Email @NotBlank String email) {
    }

    /** Step 2: the emailed 6-digit code plus the new password. */
    public record ResetPasswordRequest(
            @Email @NotBlank String email,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "must be the 6-digit code") String code,
            @NotBlank @Size(min = 8, message = "must be at least 8 characters")
            String newPassword) {
    }

    /** Mirrors the Flutter AuthUser.fromJson contract. */
    public record UserDto(
            String id,
            String email,
            String phone,
            String displayName,
            boolean onboardingComplete) {

        public static UserDto from(User u) {
            return new UserDto(
                    u.getId().toString(),
                    u.getEmail(),
                    u.getPhone(),
                    u.getDisplayName(),
                    u.isOnboardingComplete());
        }
    }

    /** Response for register/login: {@code { token, user }}. */
    public record AuthResponse(String token, UserDto user) {
    }
}
