package com.unkittered.api.pet;

import com.unkittered.api.profile.PledgePath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PetRulesTest {

    @Test
    void legacyLabelsBecomePetsWithInferredSpecies() {
        var pets = PetService.fromLegacyLabels(List.of("🐕 Rescue dog × 2", "🐈 Three cats", "🦜 Parrot", "  "));
        assertThat(pets).extracting(PetDtos.PetInput::name)
                .containsExactly("Rescue dog × 2", "Three cats", "Parrot");
        assertThat(pets).extracting(PetDtos.PetInput::species)
                .containsExactly(PetEnums.Species.DOG, PetEnums.Species.CAT, PetEnums.Species.BIRD);
    }

    @Test
    void badgeIsComputedFromVerificationAndPledge() {
        assertThat(PledgePath.badgeFor(false, false)).isEqualTo("none");
        assertThat(PledgePath.badgeFor(true, false)).isEqualTo("child_free_verified");
        // The gold badge comes from medical review, independent of the selfie.
        assertThat(PledgePath.badgeFor(false, true)).isEqualTo("sterilised_verified");
        assertThat(PledgePath.badgeFor(true, true)).isEqualTo("sterilised_verified");
    }
}
