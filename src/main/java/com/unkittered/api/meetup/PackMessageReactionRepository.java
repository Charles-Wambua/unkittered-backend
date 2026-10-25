package com.unkittered.api.meetup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PackMessageReactionRepository extends JpaRepository<PackMessageReaction, PackMessageReaction.Key> {
    List<PackMessageReaction> findByMessageIdIn(Collection<UUID> messageIds);

    List<PackMessageReaction> findByMessageId(UUID messageId);
}
