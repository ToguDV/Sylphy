package com.togudv.sylphy.repository;

import com.togudv.sylphy.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends CrudRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByTimestampAsc(String conversationId);

    List<ChatMessage> findByTimestampBefore(LocalDateTime threshold, Pageable pageable);

    long countByConversationId(String conversationId);
}
