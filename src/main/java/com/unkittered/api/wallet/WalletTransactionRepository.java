package com.unkittered.api.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Earnings still inside the holding period. */
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(t.paws), 0) FROM WalletTransaction t"
            + " WHERE t.userId = :userId AND t.kind = 'gift_received' AND t.createdAt > :since")
    long heldEarnings(@org.springframework.data.repository.query.Param("userId") UUID userId,
                      @org.springframework.data.repository.query.Param("since") java.time.Instant since);

    /** The oldest gift still on hold (it's released first). */
    java.util.Optional<WalletTransaction> findFirstByUserIdAndKindAndCreatedAtAfterOrderByCreatedAtAsc(
            UUID userId, String kind, java.time.Instant since);

    /** Locked, so a duplicate M-Pesa callback can't apply twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletTransaction> findByProviderRef(String providerRef);
}
