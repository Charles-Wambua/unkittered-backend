package com.unkittered.api.auth;

import com.unkittered.api.auth.AuthDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordReset;

    public AuthController(AuthService authService, PasswordResetService passwordReset) {
        this.authService = authService;
        this.passwordReset = passwordReset;
    }

    @Operation(summary = "Register a new account")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @Operation(summary = "Log in with email and password")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @Operation(summary = "Sign in / sign up with a verified Google or Apple ID token")
    @PostMapping("/oauth")
    public AuthResponse oauth(@Valid @RequestBody OAuthRequest req) {
        return authService.oauth(req);
    }

    @Operation(summary = "Email a 6-digit reset code (always 202, whether or not the email exists)")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordReset.requestCode(req.email());
    }

    @Operation(summary = "Set a new password using the emailed code, then sign in")
    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        return authService.resetPassword(req);
    }

    @Operation(summary = "Log out (stateless — the client simply discards its token)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // JWTs are stateless, so there is nothing to revoke server-side. The
        // endpoint exists so the client has a definite call to make on sign-out;
        // token blacklisting can hook in here later.
    }
}
