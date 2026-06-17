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

    public AuthController(AuthService authService) {
        this.authService = authService;
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

    @Operation(summary = "Sign in / sign up with a social provider")
    @PostMapping("/oauth")
    public AuthResponse oauth(@Valid @RequestBody OAuthRequest req) {
        return authService.oauth(req);
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
