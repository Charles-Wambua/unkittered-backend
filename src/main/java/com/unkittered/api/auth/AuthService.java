package com.unkittered.api.auth;

import com.unkittered.api.auth.AuthDtos.*;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.security.JwtService;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, ProfileRepository profiles,
                       PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.profiles = profiles;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with that email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setDisplayName(req.displayName().trim());
        user.setOnboardingComplete(false);
        user = users.save(user);

        // Seed an empty public profile so discover/likes have something to join to.
        Profile profile = new Profile();
        profile.setUserId(user.getId());
        profile.setName(user.getDisplayName());
        profiles.save(profile);

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (user.getPasswordHash() == null
                || !encoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return toResponse(user);
    }

    /**
     * Set a new password for the account with the given email, then sign in.
     * SECURITY: this MVP trusts the request — add email/OTP verification before
     * production so it can't be used to take over accounts.
     */
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest req) {
        String email = req.email().trim().toLowerCase();
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.notFound("No account with that email"));
        user.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(user);
        return toResponse(user);
    }

    /**
     * Social sign-in. With no real identity provider wired up yet, this
     * provisions a fresh passwordless account each time and drops the user into
     * onboarding — exercising the full social flow end-to-end. When a real OIDC
     * provider is integrated, look the user up by verified provider id instead.
     */
    @Transactional
    public AuthResponse oauth(OAuthRequest req) {
        // provider (req.provider()) is informational until a real OIDC flow lands.
        User user = new User();
        user.setDisplayName("");
        user.setOnboardingComplete(false);
        user = users.save(user);

        Profile profile = new Profile();
        profile.setUserId(user.getId());
        profiles.save(profile);

        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        String token = jwt.issue(user.getId(), user.getEmail());
        return new AuthResponse(token, UserDto.from(user));
    }
}
