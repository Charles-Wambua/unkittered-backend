package com.unkittered.api.verification;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VouchInviteRepository extends JpaRepository<VouchInvite, String> {

    /** Invites still waiting to be used, newest expiry first (one is handed out again). */
    @Query("""
        SELECT i FROM VouchInvite i
        WHERE i.inviterId = :inviter AND i.usedBy IS NULL AND i.expiresAt > :now
        ORDER BY i.expiresAt DESC
        """)
    List<VouchInvite> findOpen(@Param("inviter") UUID inviter, @Param("now") Instant now);

    /** Locks the row so a code can't be redeemed twice at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM VouchInvite i WHERE i.code = :code")
    Optional<VouchInvite> findForUpdate(@Param("code") String code);
}
