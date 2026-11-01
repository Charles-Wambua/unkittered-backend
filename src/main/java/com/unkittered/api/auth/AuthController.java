package com.unkittered.api.auth;

import com.unkittered.api.auth.AuthDtos.*;
import com.unkittered.api.security.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private static final String SLOW_DOWN = "Too many attempts. Please wait a few minutes and try again.";

    private final AuthService authService;
    private final PasswordResetService passwordReset;
    private final RateLimiter limits;

    public AuthController(AuthService authService, PasswordResetService passwordReset, RateLimiter limits) {
        this.authService = authService;
        this.passwordReset = passwordReset;
        this.limits = limits;
    }

    @Operation(summary = "Register a new account")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        limits.check("register:ip:" + RateLimiter.clientIp(http), 10, Duration.ofHours(1), SLOW_DOWN);
        return authService.register(req);
    }

    @Operation(summary = "Log in with email and password")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        // Per account (guessing one person's password) and per address (trying many).
        limits.check("login:email:" + key(req.email()), 10, Duration.ofMinutes(15), SLOW_DOWN);
        limits.check("login:ip:" + RateLimiter.clientIp(http), 30, Duration.ofMinutes(15), SLOW_DOWN);
        return authService.login(req);
    }

    @Operation(summary = "Sign in / sign up with a verified Google or Apple ID token")
    @PostMapping("/oauth")
    public AuthResponse oauth(@Valid @RequestBody OAuthRequest req, HttpServletRequest http) {
        limits.check("oauth:ip:" + RateLimiter.clientIp(http), 30, Duration.ofMinutes(15), SLOW_DOWN);
        return authService.oauth(req);
    }

    @Operation(summary = "Email a 6-digit reset code (always 202, whether or not the email exists)")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        limits.check("reset-request:ip:" + RateLimiter.clientIp(http), 20, Duration.ofHours(1), SLOW_DOWN);
        // A few codes a day per account is plenty; beyond that we quietly send
        // nothing, so the answer still doesn't reveal whether the email exists.
        if (!limits.tryAcquire("reset-request:email:" + key(req.email()), 5, Duration.ofDays(1))) return;
        passwordReset.requestCode(req.email());
    }

    @Operation(summary = "Set a new password using the emailed code, then sign in")
    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        // Each code allows 5 tries; this also caps tries across codes (and
        // parallel requests) at 10 an hour, so a 6-digit code can't be ground out.
        limits.check("reset-verify:email:" + key(req.email()), 10, Duration.ofHours(1), SLOW_DOWN);
        limits.check("reset-verify:ip:" + RateLimiter.clientIp(http), 30, Duration.ofHours(1), SLOW_DOWN);
        return authService.resetPassword(req);
    }

    @Operation(summary = "Log out (the client discards its token)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Tokens are stateless. Signing out on one phone shouldn't sign out the
        // others, so nothing is revoked here; a password reset ends every session.
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
