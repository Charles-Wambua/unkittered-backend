package com.unkittered.api.interaction;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PassRepository extends JpaRepository<Pass, UUID> {
    boolean existsByPasserIdAndPasseeId(UUID passerId, UUID passeeId);
}
