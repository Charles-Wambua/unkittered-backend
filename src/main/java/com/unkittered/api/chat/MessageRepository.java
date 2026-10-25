package com.unkittered.api.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByMatchIdOrderByCreatedAtAsc(UUID matchId);

    Optional<Message> findFirstByMatchIdOrderByCreatedAtDesc(UUID matchId);

    /** The most recent message of a kind (e.g. the latest plan). */
    Optional<Message> findFirstByMatchIdAndKindOrderByCreatedAtDesc(UUID matchId, String kind);

    List<Message> findByMatchIdAndKindOrderByCreatedAtAsc(UUID matchId, String kind);

    /** Remove every message in a conversation (used by unmatch / block). */
    void deleteByMatchId(UUID matchId);

    /** Messages in a conversation the viewer has not yet read (i.e. sent by the other person). */
    long countByMatchIdAndSenderIdNotAndReadAtIsNull(UUID matchId, UUID senderId);

    long countByMatchIdAndSenderIdNotAndReadAtIsNullAndCreatedAtAfter(UUID matchId, UUID senderId,
                                                                    java.time.Instant after);

    /** Mark every message the viewer received in this conversation as read. */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Message m SET m.readAt = :now
        WHERE m.matchId = :matchId AND m.senderId <> :viewerId AND m.readAt IS NULL
        """)
    void markRead(@Param("matchId") UUID matchId,
                  @Param("viewerId") UUID viewerId,
                  @Param("now") Instant now);
}
