package com.unkittered.api.safety;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /** Ids this user has blocked. */
    @Query("SELECT b.blockedId FROM Block b WHERE b.blockerId = :blockerId")
    List<UUID> findBlockedIds(@Param("blockerId") UUID blockerId);

    /** Ids of people who have blocked this user. */
    @Query("SELECT b.blockerId FROM Block b WHERE b.blockedId = :blockedId")
    List<UUID> findBlockerIds(@Param("blockedId") UUID blockedId);

    /** Has either of the two blocked the other? */
    @Query("SELECT count(b) > 0 FROM Block b WHERE (b.blockerId = :a AND b.blockedId = :b) "
            + "OR (b.blockerId = :b AND b.blockedId = :a)")
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);
}
