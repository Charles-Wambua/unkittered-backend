package com.unkittered.api.pet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PetRepository extends JpaRepository<Pet, UUID> {

    List<Pet> findByOwnerIdOrderByPositionAsc(UUID ownerId);

    void deleteByOwnerId(UUID ownerId);
}
