package com.unkittered.api.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationPrefsRepository extends JpaRepository<NotificationPrefs, UUID> {
}
