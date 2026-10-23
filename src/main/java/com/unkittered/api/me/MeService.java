package com.unkittered.api.me;

import com.unkittered.api.auth.AuthDtos.UserDto;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.me.MeDtos.DeleteAccountRequest;
import com.unkittered.api.me.MeDtos.UpdateProfileRequest;
import com.unkittered.api.pet.PetService;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.profile.SterilisationProcedure;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/** Self-service operations for the authenticated user (account + own profile). */
@Service
public class MeService {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final DiscoverService discoverService;
    private final PetService pets;
    private final PasswordEncoder passwords;
    private static final Logger log = LoggerFactory.getLogger(MeService.class);

    public MeService(UserRepository users, ProfileRepository profiles,
                     ProfileMapper mapper, DiscoverService discoverService, PetService pets,
                     PasswordEncoder passwords) {
        this.passwords = passwords;
        this.pets = pets;
        this.users = users;
        this.profiles = profiles;
        this.mapper = mapper;
        this.discoverService = discoverService;
    }

    @Transactional(readOnly = true)
    public UserDto account(UUID userId) {
        return UserDto.from(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public ProfileDto profile(UUID userId) {
        // Viewing your own profile — never redact your own presence.
        return mapper.toDto(requireProfile(userId), null, 0.0, false);
    }

    /** Stores the device location rounded to ~100 m; exact positions are never kept. */
    @Transactional
    public void updateLocation(UUID userId, double latitude, double longitude) {
        profiles.findById(userId).ifPresent(p -> {
            p.setLatitude(Math.round(latitude * 1000) / 1000.0);
            p.setLongitude(Math.round(longitude * 1000) / 1000.0);
            profiles.save(p);
        });
        discoverService.evict(userId);
    }

    @Transactional
    public ProfileDto updateProfile(UUID userId, UpdateProfileRequest req) {
        Profile p = requireProfile(userId);

        if (req.name() != null) {
            p.setName(req.name().trim());
            // Keep the account display name in step with the profile name.
            User user = requireUser(userId);
            user.setDisplayName(req.name().trim());
        }
        if (req.age() != null)                p.setAge(req.age());
        if (req.imageUrl() != null)           p.setImageUrl(req.imageUrl());
        if (req.bio() != null)                p.setBio(req.bio());
        if (req.location() != null)           p.setLocation(req.location());
        if (req.occupation() != null)         p.setOccupation(req.occupation());
        if (req.education() != null)          p.setEducation(req.education());
        if (req.childFreeStatement() != null) p.setChildFreeStatement(req.childFreeStatement());
        if (req.cardQuote() != null)          p.setCardQuote(req.cardQuote());
        if (req.showActivity() != null)       p.setShowActivity(req.showActivity());
        if (req.hideDistance() != null)       p.setHideDistance(req.hideDistance());
        if (req.incognito() != null) {
            // Incognito is a Gold perk — a non-Gold user can never enable it.
            boolean gold = "gold".equalsIgnoreCase(requireUser(userId).getSubscriptionTier());
            p.setIncognito(req.incognito() && gold);
        }
        if (req.connectionMode() != null) {
            String m = req.connectionMode().trim().toLowerCase();
            if (m.equals("dating") || m.equals("friends") || m.equals("both")) {
                p.setConnectionMode(m);
                p.setOpenToFriends(!m.equals("dating"));  // keep the badge flag in sync
            }
        }
        if (req.interests() != null)          p.setInterests(new ArrayList<>(req.interests()));
        if (req.galleryImages() != null)      p.setGalleryImages(new ArrayList<>(req.galleryImages()));
        if (req.animals() != null) {
            p.setPets(new ArrayList<>(pets.replaceAll(userId, req.animals())));
        } else if (req.pets() != null) {
            p.setPets(new ArrayList<>(pets.replaceAll(userId, PetService.fromLegacyLabels(req.pets()))));
        }
        if (req.sterilisationProcedure() != null) {
            String raw = req.sterilisationProcedure().trim();
            p.setSterilisationProcedure(raw.isEmpty() ? null : SterilisationProcedure.fromWire(raw).wire());
        }
        if (req.paused() != null) p.setPaused(req.paused());
        if (req.pledgePath() != null) {
            // Taking (or re-taking) the pledge restarts the yearly renewal clock.
            p.setPledgePath(req.pledgePath().wire());
            p.setPledgedAt(Instant.now());
        }
        if (req.lifestyleTags() != null)      p.setLifestyleTags(new ArrayList<>(req.lifestyleTags()));

        profiles.save(p);

        // The profile is now richer — invalidate cached decks so others can match.
        discoverService.evict(userId);
        return mapper.toDto(p, null, 0.0, false);
    }

    /**
     * Permanently deletes the account. Every table cascades from {@code users},
     * so profile, pets, photos references, messages, matches, likes, RSVPs and
     * device tokens go with it.
     */
    @Transactional
    public void deleteAccount(UUID userId, DeleteAccountRequest req) {
        User user = requireUser(userId);
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            String password = req == null ? null : req.password();
            if (password == null || !passwords.matches(password, user.getPasswordHash())) {
                // 403, not 401: the token is fine, and a 401 would sign the client out.
                throw ApiException.forbidden("That password isn't right.");
            }
        }
        log.info("Account deleted: {} (reason: {})", userId,
                req == null || req.reason() == null ? "none given" : req.reason());
        discoverService.evict(userId);
        users.delete(user);
    }

    @Transactional
    public UserDto completeOnboarding(UUID userId) {
        User user = requireUser(userId);
        user.setOnboardingComplete(true);
        users.save(user);
        return UserDto.from(user);
    }

    private User requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Account not found"));
    }

    private Profile requireProfile(UUID userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
    }
}
