package com.unkittered.api.discover;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Who a member wants to see in their deck. Absent row = no filters. */
@Entity
@Table(name = "discovery_prefs")
public class DiscoveryPrefs {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "age_min", nullable = false)
    private int ageMin = 18;

    @Column(name = "age_max", nullable = false)
    private int ageMax = 99;

    /** Null = any distance. */
    @Column(name = "max_distance_km")
    private Integer maxDistanceKm;

    @Column(name = "verified_only", nullable = false)
    private boolean verifiedOnly;

    @Column(name = "sterilised_only", nullable = false)
    private boolean sterilisedOnly;

    @Column(name = "has_pets_only", nullable = false)
    private boolean hasPetsOnly;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discovery_pref_species", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "value")
    private Set<String> species = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discovery_pref_interests", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "value")
    private Set<String> interests = new LinkedHashSet<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DiscoveryPrefs() { }

    public DiscoveryPrefs(UUID userId) {
        this.userId = userId;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public UUID getUserId() { return userId; }
    public int getAgeMin() { return ageMin; }
    public void setAgeMin(int ageMin) { this.ageMin = ageMin; }
    public int getAgeMax() { return ageMax; }
    public void setAgeMax(int ageMax) { this.ageMax = ageMax; }
    public Integer getMaxDistanceKm() { return maxDistanceKm; }
    public void setMaxDistanceKm(Integer maxDistanceKm) { this.maxDistanceKm = maxDistanceKm; }
    public boolean isVerifiedOnly() { return verifiedOnly; }
    public void setVerifiedOnly(boolean verifiedOnly) { this.verifiedOnly = verifiedOnly; }
    public boolean isSterilisedOnly() { return sterilisedOnly; }
    public void setSterilisedOnly(boolean sterilisedOnly) { this.sterilisedOnly = sterilisedOnly; }
    public boolean isHasPetsOnly() { return hasPetsOnly; }
    public void setHasPetsOnly(boolean hasPetsOnly) { this.hasPetsOnly = hasPetsOnly; }
    public Set<String> getSpecies() { return species; }
    public void setSpecies(Set<String> species) { this.species = species; }
    public Set<String> getInterests() { return interests; }
    public void setInterests(Set<String> interests) { this.interests = interests; }
}
