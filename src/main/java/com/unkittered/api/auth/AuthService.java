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
    private final PasswordResetService passwordReset;
    private final IdTokenVerifier idTokens;
    private final UserIdentityRepository identities;

    public AuthService(UserRepository users, ProfileRepository profiles,
                       PasswordEncoder encoder, JwtService jwt, PasswordResetService passwordReset,
                       IdTokenVerifier idTokens, UserIdentityRepository identities) {
        this.users = users;
        this.profiles = profiles;
        this.encoder = encoder;
        this.jwt = jwt;
        this.passwordReset = passwordReset;
        this.idTokens = idTokens;
        this.identities = identities;
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

    /** Forgot-password step 2: verify the code, set the password, sign in. */
    public AuthResponse resetPassword(ResetPasswordRequest req) {
        return toResponse(passwordReset.reset(req.email(), req.code(), req.newPassword()));
    }

    /**
     * Google / Apple sign-in. The ID token is verified first; the account is
     * then found by the provider's stable subject id, or linked to an existing
     * account with the same provider-verified email, or created.
     */
    @Transactional
    public AuthResponse oauth(OAuthRequest req) {
        var identity = idTokens.verify(req.provider(), req.idToken());

        var linked = identities.findByProviderAndSubject(identity.provider(), identity.subject());
        if (linked.isPresent()) {
            User user = users.findById(linked.get().getUserId())
                    .orElseThrow(() -> ApiException.unauthorized("Account not found"));
            return toResponse(user);
        }

        String email = identity.email() == null ? null : identity.email().trim().toLowerCase();
        User user = (email != null && identity.emailVerified())
                ? users.findByEmailIgnoreCase(email).orElse(null)
                : null;
        if (user == null) {
            user = new User();
            // Only claim the email if it's verified and nobody else has it.
            if (email != null && identity.emailVerified() && !users.existsByEmailIgnoreCase(email)) {
                user.setEmail(email);
            }
            String name = req.displayName() == null ? "" : req.displayName().trim();
            user.setDisplayName(name);
            user.setOnboardingComplete(false);
            user = users.save(user);

            Profile profile = new Profile();
            profile.setUserId(user.getId());
            profile.setName(name);
            profiles.save(profile);
        }
        identities.save(new UserIdentity(identity.provider(), identity.subject(), user.getId(), email));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        String token = jwt.issue(user.getId(), user.getEmail());
        return new AuthResponse(token, UserDto.from(user));
    }
}
