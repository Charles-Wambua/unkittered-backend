package com.unkittered.api.me;

import com.unkittered.api.auth.AuthDtos.UserDto;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.discover.DiscoverService;
import com.unkittered.api.me.MeDtos.UpdateProfileRequest;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileDto;
import com.unkittered.api.profile.ProfileMapper;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.user.User;
import com.unkittered.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;

/** Self-service operations for the authenticated user (account + own profile). */
@Service
public class MeService {

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileMapper mapper;
    private final DiscoverService discoverService;

    public MeService(UserRepository users, ProfileRepository profiles,
                     ProfileMapper mapper, DiscoverService discoverService) {
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
        if (req.isVerified() != null)         p.setVerified(req.isVerified());
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
        if (req.pets() != null)               p.setPets(new ArrayList<>(req.pets()));
        if (req.lifestyleTags() != null)      p.setLifestyleTags(new ArrayList<>(req.lifestyleTags()));

        profiles.save(p);

        // The profile is now richer — invalidate cached decks so others can match.
        discoverService.evict(userId);
        return mapper.toDto(p, null, 0.0, false);
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
