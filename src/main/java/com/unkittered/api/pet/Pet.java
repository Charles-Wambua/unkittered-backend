package com.unkittered.api.pet;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A member's animal. Ordered by {@code position}; the first is the "lead" pet. */
@Entity
@Table(name = "pets")
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String name;

    /** {@link PetEnums.Species} wire value. */
    @Column(nullable = false)
    private String species = "dog";

    @Column(name = "breed_and_age", nullable = false)
    private String breedAndAge = "";

    @Column(nullable = false)
    private short energy = 3;

    @Column(name = "comes_on_dates", nullable = false)
    private boolean comesOnDates = true;

    @Column(name = "photo_url")
    private String photoUrl;

    /** {@link PetEnums.Temperament} wire values. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pet_temperaments", joinColumns = @JoinColumn(name = "pet_id"))
    @Column(name = "value")
    private Set<String> temperaments = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Pet() { }

    public Pet(UUID ownerId, int position) {
        this.ownerId = ownerId;
        this.position = position;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getBreedAndAge() { return breedAndAge; }
    public void setBreedAndAge(String breedAndAge) { this.breedAndAge = breedAndAge; }
    public short getEnergy() { return energy; }
    public void setEnergy(short energy) { this.energy = energy; }
    public boolean isComesOnDates() { return comesOnDates; }
    public void setComesOnDates(boolean comesOnDates) { this.comesOnDates = comesOnDates; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public Set<String> getTemperaments() { return temperaments; }
    public void setTemperaments(Set<String> temperaments) { this.temperaments = temperaments; }
}
