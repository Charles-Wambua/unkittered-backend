package com.unkittered.api.auth;

import com.unkittered.api.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    /** Social / one-tap sign-in (Google, Apple…). Provider is informational here. */
    public record OAuthRequest(
            @NotBlank String provider) {
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
