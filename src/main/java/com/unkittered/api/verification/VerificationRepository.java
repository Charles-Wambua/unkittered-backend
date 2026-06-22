package com.unkittered.api.verification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationRepository extends JpaRepository<Verification, UUID> {
    Optional<Verification> findByUserId(UUID userId);
    List<Verification> findByStatusOrderByCreatedAtAsc(String status);
    long countByStatus(String status);
}
