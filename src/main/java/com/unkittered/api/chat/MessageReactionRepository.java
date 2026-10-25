package com.unkittered.api.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, MessageReaction.Key> {
    List<MessageReaction> findByMessageIdIn(Collection<UUID> messageIds);

    List<MessageReaction> findByMessageId(UUID messageId);
}
