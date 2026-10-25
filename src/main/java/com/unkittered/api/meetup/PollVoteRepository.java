package com.unkittered.api.meetup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PollVoteRepository extends JpaRepository<PollVote, PollVote.Key> {
    List<PollVote> findByMessageIdIn(Collection<UUID> messageIds);

    List<PollVote> findByMessageId(UUID messageId);
}
