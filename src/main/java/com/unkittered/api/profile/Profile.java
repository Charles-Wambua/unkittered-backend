package com.unkittered.api.profile;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Public dating profile. Mirrors the Flutter {@code Profile}. 1:1 with {@code User}. */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String name = "";

    @Column(nullable = false)
    private int age = 18;

    @Column(name = "image_url", nullable = false)
    private String imageUrl = "";

    @Column(nullable = false)
    private String bio = "";

    @Column(nullable = false)
    private String location = "";

    private Double latitude;
    private Double longitude;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(nullable = false)
    private String occupation = "";

    @Column(nullable = false)
    private String education = "";

    @Column(name = "child_free_statement", nullable = false)
    private String childFreeStatement = "Intentionally child-free";

    @Column(name = "card_quote", nullable = false)
    private String cardQuote = "";

    @Column(name = "is_online", nullable = false)
    private boolean online = false;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt = Instant.now();

    /** Whether this user shares online / last-active status with others. */
    @Column(name = "show_activity", nullable = false)
    private boolean showActivity = true;

    /** Whether to keep exact distance off the profile shown to others. */
    @Column(name = "hide_distance", nullable = false)
    private boolean hideDistance = false;

    /** Gold privacy: hide from others' discover decks (still visible to people you liked). */
    @Column(name = "incognito", nullable = false)
    private boolean incognito = false;

    /** What this member is here for: "dating" | "friends" | "both". Drives the
     *  reciprocal discover filter (see DiscoverService). */
    @Column(name = "connection_mode", nullable = false)
    private String connectionMode = "dating";

    /** Denormalised (= mode != "dating") so the client can show the 🤝 badge. */
    @Column(name = "open_to_friends", nullable = false)
    private boolean openToFriends = false;

    /** While in the future, this profile is boosted to the top of others' decks. */
    @Column(name = "boosted_until")
    private Instant boostedUntil;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_interests", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "value")
    private List<String> interests = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_gallery", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "value")
    private List<String> galleryImages = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_pets", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "value")
    private List<String> pets = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_lifestyle_tags", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "value")
    private List<String> lifestyleTags = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getChildFreeStatement() { return childFreeStatement; }
    public void setChildFreeStatement(String childFreeStatement) { this.childFreeStatement = childFreeStatement; }
    public String getCardQuote() { return cardQuote; }
    public void setCardQuote(String cardQuote) { this.cardQuote = cardQuote; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public boolean isShowActivity() { return showActivity; }
    public void setShowActivity(boolean showActivity) { this.showActivity = showActivity; }
    public boolean isHideDistance() { return hideDistance; }
    public void setHideDistance(boolean hideDistance) { this.hideDistance = hideDistance; }
    public boolean isIncognito() { return incognito; }
    public void setIncognito(boolean incognito) { this.incognito = incognito; }
    public String getConnectionMode() { return connectionMode; }
    public void setConnectionMode(String connectionMode) { this.connectionMode = connectionMode; }
    public boolean isOpenToFriends() { return openToFriends; }
    public void setOpenToFriends(boolean openToFriends) { this.openToFriends = openToFriends; }
    public Instant getBoostedUntil() { return boostedUntil; }
    public void setBoostedUntil(Instant boostedUntil) { this.boostedUntil = boostedUntil; }
    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }
    public List<String> getGalleryImages() { return galleryImages; }
    public void setGalleryImages(List<String> galleryImages) { this.galleryImages = galleryImages; }
    public List<String> getPets() { return pets; }
    public void setPets(List<String> pets) { this.pets = pets; }
    public List<String> getLifestyleTags() { return lifestyleTags; }
    public void setLifestyleTags(List<String> lifestyleTags) { this.lifestyleTags = lifestyleTags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
