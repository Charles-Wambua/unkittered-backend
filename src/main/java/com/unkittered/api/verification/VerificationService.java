package com.unkittered.api.verification;

import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Submits and reports photo-verification status. */
@Service
public class VerificationService {

    private final VerificationRepository verifications;
    private final ProfileRepository profiles;

    public VerificationService(VerificationRepository verifications, ProfileRepository profiles) {
        this.verifications = verifications;
        this.profiles = profiles;
    }

    /** Store/replace the user's selfie and mark them pending review. */
    @Transactional
    public String submit(UUID userId, String selfieUrl) {
        var existing = verifications.findByUserId(userId).orElse(null);
        if (existing == null) {
            verifications.save(new Verification(userId, selfieUrl));
        } else {
            existing.setSelfieUrl(selfieUrl);
            existing.setStatus("pending");
            existing.setCreatedAt(Instant.now());
            verifications.save(existing);
        }
        return statusFor(userId);
    }

    /**
     * 'verified' once a reviewer flips profiles.is_verified; otherwise the
     * pending/rejected request status, or 'none'.
     */
    @Transactional(readOnly = true)
    public String statusFor(UUID userId) {
        boolean verified = profiles.findById(userId).map(Profile::isVerified).orElse(false);
        if (verified) return "verified";
        return verifications.findByUserId(userId)
                .map(Verification::getStatus)
                .orElse("none");
    }
}
