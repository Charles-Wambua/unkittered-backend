package com.unkittered.api.discover;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DiscoveryPrefsRepository extends JpaRepository<DiscoveryPrefs, UUID> {
}
