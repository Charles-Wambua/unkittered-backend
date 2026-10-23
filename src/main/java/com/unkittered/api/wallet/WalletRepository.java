package com.unkittered.api.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /** Creates the wallet if missing; safe under concurrency. */
    @Modifying
    @Query(value = "INSERT INTO wallets (user_id) VALUES (:id) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensure(@Param("id") UUID id);

    /** Row-locks the wallet for the rest of the transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :id")
    Optional<Wallet> findForUpdate(@Param("id") UUID id);
}
