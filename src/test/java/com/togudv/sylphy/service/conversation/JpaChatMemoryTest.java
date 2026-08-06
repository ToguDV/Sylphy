package com.togudv.sylphy.service.conversation;

import com.togudv.sylphy.model.ChatMessage;
import com.togudv.sylphy.model.MemoryLevel;
import com.togudv.sylphy.model.MemorySummary;
import com.togudv.sylphy.model.MessageRole;
import com.togudv.sylphy.repository.ChatMessageRepository;
import com.togudv.sylphy.repository.MemorySummaryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaChatMemoryTest {

    @Mock
    ChatMessageRepository messageRepository;
    @Mock
    MemorySummaryRepository summaryRepository;
    @Mock
    MemoryConsolidationService consolidationService;

    private JpaChatMemory memory(int windowSize) {
        return new JpaChatMemory(messageRepository, summaryRepository, consolidationService, windowSize);
    }

    @Test
    void add_persistsUserAndAssistantMessages() {
        when(messageRepository.countByConversationId("c1")).thenReturn(1L);

        memory(40).add("c1", List.of(new UserMessage("hola"), new AssistantMessage("adios")));

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(captor.capture());
        List<ChatMessage> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals("c1", saved.get(0).getConversationId());
        assertEquals(MessageRole.USER, saved.get(0).getRole());
        assertEquals("hola", saved.get(0).getContent());
        assertEquals(MessageRole.ASSISTANT, saved.get(1).getRole());
        verify(consolidationService, never()).consolidateWindow(any());
    }

    @Test
    void add_ignoresSystemAndBlankMessages() {
        when(messageRepository.countByConversationId("c1")).thenReturn(1L);

        memory(40).add("c1", List.of(new SystemMessage("sistema"), new UserMessage("   "), new UserMessage("ok")));

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("ok", captor.getValue().get(0).getContent());
    }

    @Test
    void add_triggersWindowConsolidationWhenWindowIsFull() {
        when(messageRepository.countByConversationId("c1")).thenReturn(40L);

        memory(40).add("c1", List.of(new UserMessage("hola")));

        verify(consolidationService).consolidateWindow("c1");
    }

    @Test
    void get_returnsSummariesThenRawWindow() {
        MemorySummary daily = new MemorySummary(1L, "c1", MemoryLevel.DAILY, "resumen del dia", LocalDateTime.now(), "2026-08-05");
        when(summaryRepository.findByConversationIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(daily));
        when(messageRepository.findByConversationIdOrderByTimestampAsc("c1")).thenReturn(List.of(
                new ChatMessage(1L, "c1", MessageRole.USER, "primero", LocalDateTime.now().minusMinutes(2)),
                new ChatMessage(2L, "c1", MessageRole.ASSISTANT, "segundo", LocalDateTime.now().minusMinutes(1)),
                new ChatMessage(3L, "c1", MessageRole.USER, "tercero", LocalDateTime.now())));

        List<Message> result = memory(2).get("c1");

        assertEquals(3, result.size());
        assertTrue(result.get(0) instanceof SystemMessage);
        assertEquals("Resumen diario (2026-08-05): resumen del dia", result.get(0).getText());
        assertTrue(result.get(1) instanceof AssistantMessage);
        assertEquals("segundo", result.get(1).getText());
        assertTrue(result.get(2) instanceof UserMessage);
        assertEquals("tercero", result.get(2).getText());
    }

    @Test
    void clear_deletesMessagesAndSummaries() {
        ChatMessage message = new ChatMessage(1L, "c1", MessageRole.USER, "hola", LocalDateTime.now());
        MemorySummary summary = new MemorySummary(1L, "c1", MemoryLevel.WINDOW, "resumen", LocalDateTime.now(), null);
        when(messageRepository.findByConversationIdOrderByTimestampAsc("c1")).thenReturn(List.of(message));
        when(summaryRepository.findByConversationIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(summary));

        memory(40).clear("c1");

        verify(messageRepository).deleteAll(List.of(message));
        verify(summaryRepository).deleteAll(List.of(summary));
    }
}
