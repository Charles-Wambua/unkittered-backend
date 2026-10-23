package com.unkittered.api.verification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A single-use code a member shares so a verified friend can vouch for them. */
@Entity
@Table(name = "vouch_invites")
public class VouchInvite {

    @Id
    private String code;

    @Column(name = "inviter_id", nullable = false)
    private UUID inviterId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_by")
    private UUID usedBy;

    @Column(name = "used_at")
    private Instant usedAt;

    protected VouchInvite() { }

    public VouchInvite(String code, UUID inviterId, Instant now, Instant expiresAt) {
        this.code = code;
        this.inviterId = inviterId;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public String getCode() { return code; }
    public UUID getInviterId() { return inviterId; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean usableAt(Instant now) { return usedBy == null && now.isBefore(expiresAt); }

    public void markUsed(UUID by, Instant at) {
        this.usedBy = by;
        this.usedAt = at;
    }
}
