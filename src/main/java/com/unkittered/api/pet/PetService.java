package com.unkittered.api.pet;

import com.unkittered.api.common.ApiException;
import com.unkittered.api.pet.PetDtos.PetDto;
import com.unkittered.api.pet.PetDtos.PetInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** A member's animals: the source of truth for everything pet-related. */
@Service
public class PetService {

    private final PetRepository pets;

    public PetService(PetRepository pets) {
        this.pets = pets;
    }

    @Transactional(readOnly = true)
    public List<PetDto> petsOf(UUID ownerId) {
        return pets.findByOwnerIdOrderByPositionAsc(ownerId).stream().map(PetDto::from).toList();
    }

    /**
     * Replaces the member's animals with {@code inputs}, in order. Returns the
     * legacy free-text labels ("🐕 Biscuit") to keep {@code profile_pets} in
     * step for older clients.
     */
    @Transactional
    public List<String> replaceAll(UUID ownerId, List<PetInput> inputs) {
        if (inputs.size() > PetDtos.MAX_PETS) {
            throw ApiException.badRequest("You can add up to " + PetDtos.MAX_PETS + " animals");
        }
        pets.deleteByOwnerId(ownerId);
        pets.flush();

        List<Pet> saved = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            PetInput in = inputs.get(i);
            Pet p = new Pet(ownerId, i);
            p.setName(in.name().trim());
            p.setSpecies(in.species().wire());
            p.setBreedAndAge(in.breedAndAge() == null ? "" : in.breedAndAge().trim());
            p.setEnergy((short) (in.energy() == null ? 3 : in.energy()));
            p.setComesOnDates(in.comesOnDates() == null || in.comesOnDates());
            p.setPhotoUrl(blankToNull(in.photoUrl()));
            p.setTemperaments(in.getsAlongWith() == null ? new LinkedHashSet<>()
                    : in.getsAlongWith().stream().map(PetEnums.Temperament::wire)
                            .collect(Collectors.toCollection(LinkedHashSet::new)));
            saved.add(pets.save(p));
        }
        return saved.stream().map(PetService::legacyLabel).toList();
    }

    /**
     * Converts free-text labels from older clients ("🐕 Rescue dog × 2") into
     * pets, inferring the species from the emoji or wording.
     */
    public static List<PetInput> fromLegacyLabels(List<String> labels) {
        List<PetInput> out = new ArrayList<>();
        for (String raw : labels) {
            String name = raw.replaceFirst("^[^\\p{L}\\p{N}]+", "").trim();
            if (name.isEmpty()) continue;
            String lower = raw.toLowerCase();
            PetEnums.Species species =
                    raw.contains("🐕") || lower.contains("dog") ? PetEnums.Species.DOG
                    : raw.contains("🐈") || lower.contains("cat") ? PetEnums.Species.CAT
                    : raw.contains("🦜") || lower.contains("bird") || lower.contains("parrot") ? PetEnums.Species.BIRD
                    : raw.contains("🦎") || raw.contains("🐍") ? PetEnums.Species.REPTILE
                    : raw.contains("🐰") || raw.contains("🐇") || lower.contains("rabbit") ? PetEnums.Species.RABBIT
                    : raw.contains("🐠") || lower.contains("fish") ? PetEnums.Species.FISH
                    : raw.contains("🐴") || lower.contains("horse") ? PetEnums.Species.HORSE
                    : raw.contains("🐹") || lower.contains("hamster") ? PetEnums.Species.SMALL_PET
                    : PetEnums.Species.OTHER;
            out.add(new PetInput(name.length() > 30 ? name.substring(0, 30) : name,
                    species, "", null, null, null, null));
        }
        return out;
    }

    /** "🐕 Biscuit" — the format older clients and avatars expect. */
    static String legacyLabel(Pet p) {
        return PetEnums.Species.fromWire(p.getSpecies()).emoji() + " " + p.getName();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
