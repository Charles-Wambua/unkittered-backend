package com.unkittered.api.me;

import com.unkittered.api.pet.PetDtos;
import com.unkittered.api.pet.PetDtos.PetInput;
import com.unkittered.api.profile.PledgePath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request payloads for the "current user" self-service endpoints. */
public final class MeDtos {

    private MeDtos() { }

    /**
     * Partial update of the signed-in user's public profile. Every field is
     * optional — only non-null values are applied — so the same payload serves
     * both the onboarding wizard and the Edit Profile screen. Field names mirror
     * the Flutter {@code Profile.toJson} contract.
     *
     * <p>Verification status is deliberately absent: it is granted only by an
     * admin reviewing evidence, never by the member. {@code pets} (free-text
     * labels) is still accepted from older clients; {@code animals} wins when
     * both are sent.
     */
    public record UpdateProfileRequest(
            String name,
            @Min(0) Integer age,
            String imageUrl,
            String bio,
            List<String> interests,
            String location,
            List<String> galleryImages,
            String occupation,
            String education,
            List<String> pets,
            String childFreeStatement,
            List<String> lifestyleTags,
            String cardQuote,
            Boolean showActivity,
            Boolean hideDistance,
            Boolean incognito,
            String connectionMode,
            /** Full replacement of the member's animals, in display order. */
            @Size(max = PetDtos.MAX_PETS) List<@Valid PetInput> animals,
            PledgePath pledgePath,
            /** Procedure wire value; an empty string clears it. */
            String sterilisationProcedure,
            /** Hide (true) or show (false) the profile in every deck. */
            Boolean paused) {
    }

    /** Body for {@code DELETE /v1/me}. Password is required for email accounts. */
    public record DeleteAccountRequest(String password, @Size(max = 500) String reason) {
    }
}
