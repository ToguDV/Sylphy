package com.togudv.sylphy.repository;

import com.togudv.sylphy.model.MemoryLevel;
import com.togudv.sylphy.model.MemorySummary;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface MemorySummaryRepository extends CrudRepository<MemorySummary, Long> {

    List<MemorySummary> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    List<MemorySummary> findByLevel(MemoryLevel level);
}
