package com.unkittered.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A verified Google / Apple identity linked to an account. */
@Entity
@Table(name = "user_identities")
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String subject;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserIdentity() { }

    public UserIdentity(String provider, String subject, UUID userId, String email) {
        this.provider = provider;
        this.subject = subject;
        this.userId = userId;
        this.email = email;
    }

    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getSubject() { return subject; }
}
