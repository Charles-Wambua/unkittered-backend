package com.unkittered.api.verification;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Submission and review of sterilisation proof (the gold badge). */
@Service
public class SterilisationService {

    private final SterilisationProofRepository proofs;
    private final ProfileRepository profiles;

    public SterilisationService(SterilisationProofRepository proofs, ProfileRepository profiles) {
        this.proofs = proofs;
        this.profiles = profiles;
    }

    @Transactional
    public String submit(UUID userId, String documentUrl) {
        SterilisationProof proof = proofs.findByUserId(userId).orElse(null);
        if (proof == null) {
            proofs.save(new SterilisationProof(userId, documentUrl));
        } else {
            proof.setDocumentUrl(documentUrl);
            proof.setStatus("pending");
            proof.setCreatedAt(Instant.now());
            proofs.save(proof);
        }
        return statusFor(userId);
    }

    /** "verified" once approved; otherwise "pending", "rejected" or "none". */
    @Transactional(readOnly = true)
    public String statusFor(UUID userId) {
        boolean verified = profiles.findById(userId).map(Profile::isSterilisationVerified).orElse(false);
        if (verified) return "verified";
        return proofs.findByUserId(userId).map(SterilisationProof::getStatus).orElse("none");
    }

    @Transactional(readOnly = true)
    public List<SterilisationProof> pending() {
        return proofs.findByStatusOrderByCreatedAtAsc("pending");
    }

    /** Approve or reject, then drop the document link either way. */
    @Transactional
    public void review(UUID userId, boolean approve) {
        SterilisationProof proof = proofs.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("No sterilisation proof submitted"));
        proof.setStatus(approve ? "approved" : "rejected");
        proof.setReviewedAt(Instant.now());
        proof.setDocumentUrl("");
        proofs.save(proof);
        profiles.findById(userId).ifPresent(p -> {
            p.setSterilisationVerified(approve);
            profiles.save(p);
        });
    }
}
