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

    /** Conversation summary values used by the message list UI. */
    interface ConversationSummary {
        UUID getMatchId();
        String getLastMessage();
        Instant getLastMessageAt();
        long getUnreadCount();
    }

    @Query(value = """
        SELECT m.id AS match_id,
               last.body AS last_message,
               COALESCE(last.created_at, m.created_at) AS last_message_at,
               COUNT(CASE WHEN msg.sender_id <> :viewerId AND msg.read_at IS NULL THEN 1 END) AS unread_count
        FROM matches m
        LEFT JOIN messages msg ON msg.match_id = m.id
        LEFT JOIN LATERAL (
            SELECT body, created_at
            FROM messages
            WHERE match_id = m.id
            ORDER BY created_at DESC
            LIMIT 1
        ) last ON true
        WHERE m.user_low = :viewerId OR m.user_high = :viewerId
        GROUP BY m.id, last.body, last.created_at
        ORDER BY last_message_at DESC
        """, nativeQuery = true)
    List<ConversationSummary> findConversationSummaries(@Param("viewerId") UUID viewerId);

    /** Remove every message in a conversation (used by unmatch / block). */
    void deleteByMatchId(UUID matchId);

    /** Messages in a conversation the viewer has not yet read (i.e. sent by the other person). */
    long countByMatchIdAndSenderIdNotAndReadAtIsNull(UUID matchId, UUID senderId);

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
