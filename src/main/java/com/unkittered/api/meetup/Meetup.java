package com.unkittered.api.meetup;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A child-free group event. */
@Entity
@Table(name = "meetups")
public class Meetup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String area;

    @Column(nullable = false)
    private String venue = "";

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(nullable = false)
    private int capacity = 0;

    @Column(nullable = false)
    private String description = "";

    /** {@link MeetupEnums.Kind} wire value. */
    @Column(nullable = false)
    private String kind = "cafe";

    /** {@link MeetupEnums.PetPolicy} wire value. */
    @Column(name = "pet_policy", nullable = false)
    private String petPolicy = "pets_welcome";

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 120;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Meetup() { }

    public Meetup(UUID hostId, String title, String category, String area,
                  String venue, Instant startsAt, int capacity, String description) {
        this.hostId = hostId;
        this.title = title;
        this.category = category;
        this.area = area;
        this.venue = venue == null ? "" : venue;
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.description = description == null ? "" : description;
    }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getPetPolicy() { return petPolicy; }
    public void setPetPolicy(String petPolicy) { this.petPolicy = petPolicy; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public UUID getId() { return id; }
    public UUID getHostId() { return hostId; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getArea() { return area; }
    public String getVenue() { return venue; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return startsAt.plus(Duration.ofMinutes(durationMinutes)); }
    public int getCapacity() { return capacity; }
    public String getDescription() { return description; }
}
