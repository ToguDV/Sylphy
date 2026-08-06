package com.togudv.sylphy.service.conversation;

import com.togudv.sylphy.model.ChatMessage;
import com.togudv.sylphy.model.MemorySummary;
import com.togudv.sylphy.model.MessageRole;
import com.togudv.sylphy.repository.ChatMessageRepository;
import com.togudv.sylphy.repository.MemorySummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Memoria de conversacion persistida en JPA e integrada con Spring AI via
 * el SPI ChatMemory y MessageChatMemoryAdvisor. El contexto devuelto a la
 * IA combina los resumenes de todos los niveles (acotados por la jerarquia
 * de consolidacion) mas la ventana cruda reciente; asi, todos los canales
 * comparten el mismo hilo y la misma memoria episodica.
 */
@Slf4j
@Service
public class JpaChatMemory implements ChatMemory {

    private final ChatMessageRepository messageRepository;
    private final MemorySummaryRepository summaryRepository;
    private final MemoryConsolidationService consolidationService;
    private final int windowSize;

    public JpaChatMemory(ChatMessageRepository messageRepository,
                         MemorySummaryRepository summaryRepository,
                         MemoryConsolidationService consolidationService,
                         @Value("${sylphy.chat.history.window:40}") int windowSize) {
        this.messageRepository = messageRepository;
        this.summaryRepository = summaryRepository;
        this.consolidationService = consolidationService;
        this.windowSize = windowSize;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<ChatMessage> entities = new ArrayList<>();
        for (Message message : messages) {
            if (message.getMessageType() != org.springframework.ai.chat.messages.MessageType.USER
                    && message.getMessageType() != org.springframework.ai.chat.messages.MessageType.ASSISTANT) {
                continue;
            }
            String text = message.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            MessageRole role = message.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER
                    ? MessageRole.USER : MessageRole.ASSISTANT;
            entities.add(new ChatMessage(null, conversationId, role, text, LocalDateTime.now()));
        }
        if (entities.isEmpty()) {
            return;
        }
        messageRepository.saveAll(entities);
        if (messageRepository.countByConversationId(conversationId) >= windowSize) {
            consolidationService.consolidateWindow(conversationId);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> context = new ArrayList<>();
        summaryRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .forEach(s -> context.add(new SystemMessage(
                        MemoryConsolidationService.levelLabel(s.getLevel(), s.getPeriodKey())
                                + ": " + s.getContent())));
        List<ChatMessage> raw = messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
        int start = Math.max(0, raw.size() - windowSize);
        for (int i = start; i < raw.size(); i++) {
            ChatMessage message = raw.get(i);
            context.add(message.getRole() == MessageRole.USER
                    ? new UserMessage(message.getContent())
                    : new AssistantMessage(message.getContent()));
        }
        return context;
    }

    @Override
    public void clear(String conversationId) {
        messageRepository.deleteAll(messageRepository.findByConversationIdOrderByTimestampAsc(conversationId));
        summaryRepository.deleteAll(summaryRepository.findByConversationIdOrderByCreatedAtAsc(conversationId));
        log.info("conversacion {}: historial y memoria borrados", conversationId);
    }
}
