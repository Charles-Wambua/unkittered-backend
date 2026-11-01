package com.unkittered.api.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Earnings still inside the holding period. */
    @Query("SELECT COALESCE(SUM(t.paws), 0) FROM WalletTransaction t"
            + " WHERE t.userId = :userId AND t.kind = 'gift_received' AND t.createdAt > :since")
    long heldEarnings(@Param("userId") UUID userId, @Param("since") Instant since);

    /** The oldest gift still on hold (it's released first). */
    Optional<WalletTransaction> findFirstByUserIdAndKindAndCreatedAtAfterOrderByCreatedAtAsc(
            UUID userId, String kind, Instant since);

    /** Locked, so a duplicate M-Pesa callback can't apply twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletTransaction> findByProviderRef(String providerRef);

    /**
     * The oldest recent M-Pesa top-up that never got a reference back (our
     * request timed out), for this phone and amount. Locked like the above.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletTransaction> findFirstByKindAndStatusAndMethodAndProviderRefIsNullAndPhoneAndAmountKesAndCreatedAtAfterOrderByCreatedAtAsc(
            String kind, String status, String method, String phone, Integer amountKes, Instant since);

    default Optional<WalletTransaction> findUnmatchedTopUp(String phone, int amountKes, Instant since) {
        return findFirstByKindAndStatusAndMethodAndProviderRefIsNullAndPhoneAndAmountKesAndCreatedAtAfterOrderByCreatedAtAsc(
                WalletTransaction.TOP_UP, WalletTransaction.PENDING, PaymentMethod.MPESA.wire(), phone, amountKes, since);
    }

    List<WalletTransaction> findByKindAndStatusAndCreatedAtBefore(String kind, String status, Instant before);
}
