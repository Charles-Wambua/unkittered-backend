package com.unkittered.api.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {
}
