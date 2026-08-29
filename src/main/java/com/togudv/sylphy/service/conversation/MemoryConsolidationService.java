package com.togudv.sylphy.service.conversation;

import com.togudv.sylphy.model.ChatMessage;
import com.togudv.sylphy.model.MemoryLevel;
import com.togudv.sylphy.model.MemorySummary;
import com.togudv.sylphy.model.MessageRole;
import com.togudv.sylphy.repository.ChatMessageRepository;
import com.togudv.sylphy.repository.MemorySummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Orquesta la consolidacion jerarquica de la memoria episodica (modelo
 * decremental): ventana (cada 40 mensajes), diario, semanal, mensual y
 * anual. Cada nivel resume el inferior, guarda el resumen y elimina el
 * material consolidado en una unica transaccion. Si el LLM falla o devuelve
 * vacio, no se borra nada y el disparo siguiente reintenta.
 */
@Slf4j
@Service
public class MemoryConsolidationService {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);
    private static final DateTimeFormatter YEAR_KEY = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);
    private static final int DAILY_BATCH_SIZE = 500;

    private final ChatMessageRepository messageRepository;
    private final MemorySummaryRepository summaryRepository;
    private final MemorySummarizer summarizer;
    private final TransactionTemplate transactionTemplate;

    public MemoryConsolidationService(ChatMessageRepository messageRepository,
                                      MemorySummaryRepository summaryRepository,
                                      MemorySummarizer summarizer,
                                      PlatformTransactionManager transactionManager) {
        this.messageRepository = messageRepository;
        this.summaryRepository = summaryRepository;
        this.summarizer = summarizer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Consolida todos los mensajes crudos de una conversacion en un resumen
     * WINDOW. Disparado por JpaChatMemory cuando la ventana alcanza el limite.
     */
    public void consolidateWindow(String conversationId) {
        List<ChatMessage> messages = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        if (messages.isEmpty()) {
            return;
        }
        String content = summarizer.summarize(MemoryLevel.WINDOW, formatMessages(messages));
        if (content == null || content.isBlank()) {
            log.warn("conversacion {}: resumen WINDOW vacio, se conservan {} mensajes", conversationId, messages.size());
            return;
        }
        persistAndDelete(conversationId, MemoryLevel.WINDOW, null, content, messages, List.of());
        log.info("conversacion {}: {} mensajes consolidados en resumen WINDOW", conversationId, messages.size());
    }

    /** Consolida en resumenes diarios todo lo anterior a hoy (mensajes crudos y resumenes WINDOW). */
    public void consolidateDaily() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, List<ChatMessage>> raws = new LinkedHashMap<>();
        int page = 0;
        List<ChatMessage> batch;
        do {
            batch = messageRepository.findByTimestampBefore(todayStart,
                    PageRequest.of(page, DAILY_BATCH_SIZE, Sort.by("id")));
            groupMessagesByConversation(batch).forEach((conversationId, messages) ->
                    raws.computeIfAbsent(conversationId, k -> new ArrayList<>()).addAll(messages));
            page++;
        } while (batch.size() == DAILY_BATCH_SIZE);
        Map<String, List<MemorySummary>> windows = groupSummariesByConversation(
                summaryRepository.findByLevel(MemoryLevel.WINDOW));
        windows.entrySet().removeIf(e -> e.getValue().stream().noneMatch(s -> s.getCreatedAt().isBefore(todayStart)));

        union(raws, windows).forEach(conversationId -> {
            List<ChatMessage> messages = raws.getOrDefault(conversationId, List.of());
            List<MemorySummary> summaries = windows.getOrDefault(conversationId, List.of());
            if (messages.isEmpty() && summaries.isEmpty()) {
                return;
            }
            LocalDate period = newestDateOf(messages, summaries);
            String content = summarizer.summarize(MemoryLevel.DAILY, formatDailyInput(messages, summaries));
            if (content == null || content.isBlank()) {
                log.warn("conversacion {}: resumen DIARIO vacio, se conserva el material del dia", conversationId);
                return;
            }
            persistAndDelete(conversationId, MemoryLevel.DAILY, period.toString(), content, messages, summaries);
            log.info("conversacion {}: resumen DIARIO creado para {}", conversationId, period);
        });
    }

    /** Consolida los resumenes diarios de semanas anteriores en resumenes semanales. */
    public void consolidateWeekly() {
        LocalDate weekStart = LocalDate.now().with(WeekFields.ISO.dayOfWeek(), 1L);
        Map<String, List<MemorySummary>> dailies = groupSummariesByConversation(
                summaryRepository.findByLevel(MemoryLevel.DAILY));
        foldPeriods(dailies, MemoryLevel.WEEKLY, weekStart,
                MemoryConsolidationService::periodKeyToDate, MemoryConsolidationService::weekKeyOf, "SEMANAL");
    }

    /** Consolida los resumenes semanales de meses anteriores en resumenes mensuales. */
    public void consolidateMonthly() {
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        Map<String, List<MemorySummary>> weeklies = groupSummariesByConversation(
                summaryRepository.findByLevel(MemoryLevel.WEEKLY));
        foldPeriods(weeklies, MemoryLevel.MONTHLY, monthStart,
                MemoryConsolidationService::weekKeyToDate, s -> s.format(MONTH_KEY), "MENSUAL");
    }

    /** Consolida los resumenes mensuales de anios anteriores en resumenes anuales. */
    public void consolidateAnnual() {
        LocalDate yearStart = LocalDate.now().withDayOfYear(1);
        Map<String, List<MemorySummary>> monthlies = groupSummariesByConversation(
                summaryRepository.findByLevel(MemoryLevel.MONTHLY));
        foldPeriods(monthlies, MemoryLevel.ANNUAL, yearStart,
                MemoryConsolidationService::monthKeyToDate, s -> s.format(YEAR_KEY), "ANUAL");
    }

    private void foldPeriods(Map<String, List<MemorySummary>> lower, MemoryLevel level,
                             LocalDate periodStart, Function<String, LocalDate> keyParser,
                             Function<LocalDate, String> keyFormatter, String label) {
        lower.forEach((conversationId, summaries) -> {
            List<MemorySummary> toFold = new ArrayList<>();
            LocalDate newest = null;
            for (MemorySummary summary : summaries) {
                if (summary.getPeriodKey() == null) {
                    continue;
                }
                LocalDate parsed = parsePeriodKey(keyParser, summary.getPeriodKey(), conversationId, label);
                if (parsed != null && parsed.isBefore(periodStart)) {
                    toFold.add(summary);
                    if (newest == null || parsed.isAfter(newest)) {
                        newest = parsed;
                    }
                }
            }
            if (toFold.isEmpty()) {
                return;
            }
            String content = summarizer.summarize(level, formatSummaries(toFold));
            if (content == null || content.isBlank()) {
                log.warn("conversacion {}: resumen {} vacio, se conservan los resumenes inferiores",
                        conversationId, label);
                return;
            }
            persistAndDelete(conversationId, level, keyFormatter.apply(newest), content, List.of(), toFold);
            log.info("conversacion {}: resumen {} creado para {}", conversationId, label, keyFormatter.apply(newest));
        });
    }

    private static LocalDate parsePeriodKey(Function<String, LocalDate> keyParser, String key,
                                            String conversationId, String label) {
        try {
            return keyParser.apply(key);
        } catch (RuntimeException e) {
            log.warn("conversacion {}: periodKey invalido '{}' en consolidacion {}, se omite la entrada",
                    conversationId, key, label);
            return null;
        }
    }

    private void persistAndDelete(String conversationId, MemoryLevel level, String periodKey, String content,
                                  List<ChatMessage> messages, List<MemorySummary> summaries) {
        transactionTemplate.executeWithoutResult(status -> {
            MemorySummary summary = new MemorySummary(null, conversationId, level, content, LocalDateTime.now(), periodKey);
            summaryRepository.save(summary);
            if (!messages.isEmpty()) {
                messageRepository.deleteAll(messages);
            }
            if (!summaries.isEmpty()) {
                summaryRepository.deleteAll(summaries);
            }
        });
    }

    private static String formatMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            String speaker = message.getRole() == MessageRole.USER ? "Usuario" : "Asistente";
            sb.append("- ").append(speaker).append(": ").append(message.getContent()).append('\n');
        }
        return sb.toString();
    }

    private static String formatSummaries(List<MemorySummary> summaries) {
        StringBuilder sb = new StringBuilder();
        for (MemorySummary summary : summaries) {
            sb.append("- ").append(levelLabel(summary.getLevel(), summary.getPeriodKey()))
                    .append(": ").append(summary.getContent()).append('\n');
        }
        return sb.toString();
    }

    private static String formatDailyInput(List<ChatMessage> messages, List<MemorySummary> windows) {
        StringBuilder sb = new StringBuilder(formatSummaries(windows));
        sb.append(formatMessages(messages));
        return sb.toString();
    }

    static String levelLabel(MemoryLevel level, String periodKey) {
        return switch (level) {
            case WINDOW -> "Resumen de conversacion reciente";
            case DAILY -> "Resumen diario (" + periodKey + ")";
            case WEEKLY -> "Resumen semanal (" + periodKey + ")";
            case MONTHLY -> "Resumen mensual (" + periodKey + ")";
            case ANNUAL -> "Resumen anual (" + periodKey + ")";
        };
    }

    private static LocalDate newestDateOf(List<ChatMessage> messages, List<MemorySummary> summaries) {
        LocalDate newest = summaries.stream()
                .map(s -> s.getCreatedAt().toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(LocalDate.MIN);
        for (ChatMessage message : messages) {
            if (message.getTimestamp().toLocalDate().isAfter(newest)) {
                newest = message.getTimestamp().toLocalDate();
            }
        }
        return newest;
    }

    private static String weekKeyOf(LocalDate date) {
        return String.format(Locale.ROOT, "%d-W%02d",
                date.get(WeekFields.ISO.weekBasedYear()),
                date.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }

    private static LocalDate weekKeyToDate(String key) {
        return LocalDate.parse(key + "-1", DateTimeFormatter.ISO_WEEK_DATE);
    }

    private static LocalDate monthKeyToDate(String key) {
        return java.time.YearMonth.parse(key, MONTH_KEY).atDay(1);
    }

    private static LocalDate periodKeyToDate(String key) {
        return LocalDate.parse(key);
    }

    private static <T> Map<String, List<T>> groupByConversation(List<T> items,
                                                                Function<T, String> conversationKey) {
        Map<String, List<T>> grouped = new LinkedHashMap<>();
        for (T item : items) {
            grouped.computeIfAbsent(conversationKey.apply(item), k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private static Map<String, List<ChatMessage>> groupMessagesByConversation(List<ChatMessage> items) {
        return groupByConversation(items, ChatMessage::getConversationId);
    }

    private static Map<String, List<MemorySummary>> groupSummariesByConversation(List<MemorySummary> items) {
        return groupByConversation(items, MemorySummary::getConversationId);
    }

    private static Set<String> union(Map<String, List<ChatMessage>> messages, Map<String, List<MemorySummary>> summaries) {
        Set<String> keys = new LinkedHashSet<>(messages.keySet());
        keys.addAll(summaries.keySet());
        return keys;
    }
}
