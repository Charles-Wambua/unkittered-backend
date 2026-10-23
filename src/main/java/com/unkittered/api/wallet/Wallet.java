package com.unkittered.api.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A member's Paws balances. Always read for update before changing. */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private int paws;

    @Column(nullable = false)
    private int earned;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Wallet() { }

    public Wallet(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() { return userId; }
    public int getPaws() { return paws; }
    public int getEarned() { return earned; }

    void addPaws(int delta) {
        if (paws + delta < 0) throw new IllegalStateException("negative paws");
        paws += delta;
        updatedAt = Instant.now();
    }

    void addEarned(int delta) {
        if (earned + delta < 0) throw new IllegalStateException("negative earned");
        earned += delta;
        updatedAt = Instant.now();
    }
}
