package com.unkittered.api.chat;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatClearRepository extends JpaRepository<ChatClear, ChatClear.Key> {
}
