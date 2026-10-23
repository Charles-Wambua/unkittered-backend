package com.unkittered.api.pet;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/** Wire payloads for pets. */
public final class PetDtos {
    private PetDtos() { }

    /** Max animals per member. */
    public static final int MAX_PETS = 6;

    public record PetDto(
            String id,
            String name,
            PetEnums.Species species,
            String breedAndAge,
            int energy,
            List<PetEnums.Temperament> getsAlongWith,
            boolean comesOnDates,
            String photoUrl) implements Serializable {

        public static PetDto from(Pet p) {
            return new PetDto(
                    p.getId().toString(),
                    p.getName(),
                    PetEnums.Species.fromWire(p.getSpecies()),
                    p.getBreedAndAge(),
                    p.getEnergy(),
                    p.getTemperaments().stream().map(PetEnums.Temperament::fromWire).sorted().toList(),
                    p.isComesOnDates(),
                    p.getPhotoUrl());
        }
    }

    /** One animal in a full-list replacement. */
    public record PetInput(
            @NotBlank @Size(max = 30) String name,
            @NotNull PetEnums.Species species,
            @Size(max = 60) String breedAndAge,
            @Min(1) @Max(5) Integer energy,
            Set<PetEnums.Temperament> getsAlongWith,
            Boolean comesOnDates,
            @Size(max = 500) String photoUrl) {
    }
}
