package com.unkittered.api.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Locked, so a duplicate M-Pesa callback can't apply twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletTransaction> findByProviderRef(String providerRef);
}
