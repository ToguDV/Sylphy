package com.togudv.sylphy.service.conversation;

import com.togudv.sylphy.model.ChatMessage;
import com.togudv.sylphy.model.MemoryLevel;
import com.togudv.sylphy.model.MemorySummary;
import com.togudv.sylphy.model.MessageRole;
import com.togudv.sylphy.repository.ChatMessageRepository;
import com.togudv.sylphy.repository.MemorySummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryConsolidationServiceTest {

    @Mock
    ChatMessageRepository messageRepository;
    @Mock
    MemorySummaryRepository summaryRepository;
    @Mock
    MemorySummarizer summarizer;

    MemoryConsolidationService service;

    @BeforeEach
    void setUp() {
        service = new MemoryConsolidationService(messageRepository, summaryRepository, summarizer, noOpTransactionManager());
    }

    private static PlatformTransactionManager noOpTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static ChatMessage message(String conversationId, String content, MessageRole role, LocalDateTime at) {
        return new ChatMessage(null, conversationId, role, content, at);
    }

    private static MemorySummary summary(String conversationId, MemoryLevel level, String periodKey,
                                         LocalDateTime createdAt) {
        return new MemorySummary(null, conversationId, level, "resumen", createdAt, periodKey);
    }

    @Test
    void consolidateWindow_summarizesAndDeletesMessages() {
        ChatMessage first = message("c1", "hola", MessageRole.USER, LocalDateTime.now().minusMinutes(2));
        ChatMessage second = message("c1", "adios", MessageRole.ASSISTANT, LocalDateTime.now());
        when(messageRepository.findByConversationIdOrderByTimestampAsc("c1")).thenReturn(List.of(first, second));
        when(summarizer.summarize(any(), any())).thenReturn("resumen de la ventana");

        service.consolidateWindow("c1");

        ArgumentCaptor<MemorySummary> captor = ArgumentCaptor.forClass(MemorySummary.class);
        verify(summaryRepository).save(captor.capture());
        assertEquals(MemoryLevel.WINDOW, captor.getValue().getLevel());
        assertEquals("resumen de la ventana", captor.getValue().getContent());
        assertNull(captor.getValue().getPeriodKey());
        verify(messageRepository).deleteAll(List.of(first, second));
    }

    @Test
    void consolidateWindow_doesNothingWithoutMessages() {
        when(messageRepository.findByConversationIdOrderByTimestampAsc("c1")).thenReturn(List.of());

        service.consolidateWindow("c1");

        verify(summarizer, never()).summarize(any(), any());
    }

    @Test
    void consolidateWindow_keepsMessagesWhenSummaryIsBlank() {
        ChatMessage first = message("c1", "hola", MessageRole.USER, LocalDateTime.now());
        when(messageRepository.findByConversationIdOrderByTimestampAsc("c1")).thenReturn(List.of(first));
        when(summarizer.summarize(any(), any())).thenReturn("   ");

        service.consolidateWindow("c1");

        verify(summaryRepository, never()).save(any());
        verify(messageRepository, never()).deleteAll(any());
    }

    @Test
    void consolidateDaily_foldsOldRawAndWindowSummaries() {
        LocalDateTime yesterday = LocalDate.now().minusDays(1).atStartOfDay();
        ChatMessage raw = message("c1", "mensaje de ayer", MessageRole.USER, yesterday.plusHours(10));
        MemorySummary window = summary("c1", MemoryLevel.WINDOW, null, yesterday.plusHours(12));
        when(messageRepository.findByTimestampBefore(eq(LocalDate.now().atStartOfDay()), any())).thenReturn(List.of(raw));
        when(summaryRepository.findByLevel(MemoryLevel.WINDOW)).thenReturn(List.of(window));
        when(summarizer.summarize(any(), any())).thenReturn("resumen del dia");

        service.consolidateDaily();

        ArgumentCaptor<MemorySummary> captor = ArgumentCaptor.forClass(MemorySummary.class);
        verify(summaryRepository).save(captor.capture());
        assertEquals(MemoryLevel.DAILY, captor.getValue().getLevel());
        assertEquals(yesterday.toLocalDate().toString(), captor.getValue().getPeriodKey());
        verify(messageRepository).deleteAll(List.of(raw));
        verify(summaryRepository).deleteAll(List.of(window));
    }

    @Test
    void consolidateDaily_ignoresTodaysWindowSummaries() {
        MemorySummary todayWindow = summary("c1", MemoryLevel.WINDOW, null, LocalDateTime.now());
        when(messageRepository.findByTimestampBefore(eq(LocalDate.now().atStartOfDay()), any())).thenReturn(List.of());
        when(summaryRepository.findByLevel(MemoryLevel.WINDOW)).thenReturn(List.of(todayWindow));

        service.consolidateDaily();

        verify(summarizer, never()).summarize(any(), any());
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void consolidateWeekly_skipsCorruptPeriodKeyAndFoldsTheRest() {
        LocalDate weekStart = LocalDate.now().with(WeekFields.ISO.dayOfWeek(), 1L);
        MemorySummary oldDaily = summary("c1", MemoryLevel.DAILY, weekStart.minusDays(3).toString(), LocalDateTime.now());
        MemorySummary corruptDaily = summary("c1", MemoryLevel.DAILY, "basura-no-es-fecha", LocalDateTime.now());
        when(summaryRepository.findByLevel(MemoryLevel.DAILY)).thenReturn(List.of(oldDaily, corruptDaily));
        when(summarizer.summarize(any(), any())).thenReturn("resumen de la semana");

        service.consolidateWeekly();

        ArgumentCaptor<MemorySummary> captor = ArgumentCaptor.forClass(MemorySummary.class);
        verify(summaryRepository).save(captor.capture());
        assertEquals(MemoryLevel.WEEKLY, captor.getValue().getLevel());
        verify(summaryRepository).deleteAll(List.of(oldDaily));
    }

    @Test
    void consolidateWeekly_allKeysCorrupt_doesNothing() {
        MemorySummary corruptDaily = summary("c1", MemoryLevel.DAILY, "basura-no-es-fecha", LocalDateTime.now());
        when(summaryRepository.findByLevel(MemoryLevel.DAILY)).thenReturn(List.of(corruptDaily));

        service.consolidateWeekly();

        verify(summarizer, never()).summarize(any(), any());
        verify(summaryRepository, never()).save(any());
        verify(summaryRepository, never()).deleteAll(any());
    }

    @Test
    void consolidateWeekly_foldsOnlyPreviousWeeksDailies() {
        LocalDate weekStart = LocalDate.now().with(WeekFields.ISO.dayOfWeek(), 1L);
        MemorySummary oldDaily = summary("c1", MemoryLevel.DAILY, weekStart.minusDays(3).toString(), LocalDateTime.now());
        MemorySummary currentDaily = summary("c1", MemoryLevel.DAILY, weekStart.plusDays(1).toString(), LocalDateTime.now());
        when(summaryRepository.findByLevel(MemoryLevel.DAILY)).thenReturn(List.of(oldDaily, currentDaily));
        when(summarizer.summarize(any(), any())).thenReturn("resumen de la semana");

        service.consolidateWeekly();

        ArgumentCaptor<MemorySummary> captor = ArgumentCaptor.forClass(MemorySummary.class);
        verify(summaryRepository).save(captor.capture());
        assertEquals(MemoryLevel.WEEKLY, captor.getValue().getLevel());
        assertEquals(weekKeyOf(weekStart.minusDays(3)), captor.getValue().getPeriodKey());
        verify(summaryRepository).deleteAll(List.of(oldDaily));
    }

    @Test
    void consolidateMonthly_foldsOnlyPreviousMonthsWeeklies() {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate lastMonth = monthStart.minusMonths(1);
        MemorySummary oldWeekly = summary("c1", MemoryLevel.WEEKLY, weekKeyOf(lastMonth.withDayOfMonth(15)), LocalDateTime.now());
        MemorySummary currentWeekly = summary("c1", MemoryLevel.WEEKLY, weekKeyOf(monthStart.plusDays(7)), LocalDateTime.now());
        when(summaryRepository.findByLevel(MemoryLevel.WEEKLY)).thenReturn(List.of(oldWeekly, currentWeekly));
        when(summarizer.summarize(any(), any())).thenReturn("resumen del mes");

        service.consolidateMonthly();

        ArgumentCaptor<MemorySummary> captor = ArgumentCaptor.forClass(MemorySummary.class);
        verify(summaryRepository).save(captor.capture());
        assertEquals(MemoryLevel.MONTHLY, captor.getValue().getLevel());
        assertEquals(lastMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")),
                captor.getValue().getPeriodKey());
        verify(summaryRepository).deleteAll(List.of(oldWeekly));
    }

    @Test
    void consolidateAnnual_foldsOnlyPreviousYearsMonthlies() {
        LocalDate yearStart = LocalDate.now().withDayOfYear(1);
        LocalDate lastYear = yearStart.minusYears(1);
        MemorySummary oldMonthly = summary("c1", MemoryLevel.MONTHLY,
                lastYear.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")), LocalDateTime.now());
        MemorySummary currentMonthly = summary("c1", MemoryLevel.MONTHLY,
                yearStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")), LocalDateTime.now());
        when(summaryRepository.findByLevel(MemoryLevel.MONTHLY)).thenReturn(List.of(oldMonthly, currentMonthly));
        when(summarizer.summarize(any(), any())).thenReturn("resumen del anio");

        service.consolidateAnnual();

        ArgumentCaptor<MemorySummary> captor = ArgumentCaptor.forClass(MemorySummary.class);
        verify(summaryRepository).save(captor.capture());
        assertEquals(MemoryLevel.ANNUAL, captor.getValue().getLevel());
        assertEquals(lastYear.format(java.time.format.DateTimeFormatter.ofPattern("yyyy")),
                captor.getValue().getPeriodKey());
        verify(summaryRepository).deleteAll(List.of(oldMonthly));
    }

    private static String weekKeyOf(LocalDate date) {
        return String.format("%d-W%02d",
                date.get(WeekFields.ISO.weekBasedYear()),
                date.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }
}
