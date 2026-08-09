package com.togudv.sylphy.service;

import com.togudv.sylphy.config.ConversationIdProvider;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.service.conversation.JpaChatMemory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * Redacta el texto de la notificacion cuando se dispara un recordatorio.
 * Usa el mismo system prompt efectivo que el chat (personalidad configurada)
 * y el contexto del historial de conversacion compartido, de modo que las
 * notificaciones hereden el tono y las ocurrencias del asistente. Si no hay
 * prompt configurado, cae al prompt interno por defecto.
 */
@Service
public class ReminderMessageComposer {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            Eres Sylphy, un asistente personal. Tu tarea es redactar el mensaje que se \
            enviara al usuario cuando se dispare un recordatorio. Tono: cercano, en \
            segunda persona, conciso (una o dos frases como maximo). Sin markdown, sin \
            comillas, sin emojis. Devuelve unicamente el texto del mensaje, sin \
            introducciones ni explicaciones adicionales.
            """;

    private final ChatClient chatClient;
    private final JpaChatMemory chatMemory;
    private final ConversationIdProvider conversationIdProvider;
    private final SystemPromptService systemPromptService;

    @SuppressFBWarnings(
            value = "EI2",
            justification = "JpaChatMemory is a Spring-managed singleton service; the reference is reference-stable by container contract.")
    public ReminderMessageComposer(ChatClient.Builder chatClientBuilder,
                                   JpaChatMemory chatMemory,
                                   ConversationIdProvider conversationIdProvider,
                                   SystemPromptService systemPromptService) {
        this.chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder().temperature(0.8))
                .build();
        this.chatMemory = chatMemory;
        this.conversationIdProvider = conversationIdProvider;
        this.systemPromptService = systemPromptService;
    }

    public String compose(Reminder reminder) {
        String conversationId = conversationIdProvider.getConversationId();
        return chatClient
                .prompt()
                .system(effectiveSystemPrompt())
                .messages(chatMemory.get(conversationId))
                .user(buildUserPrompt(reminder))
                .call()
                .content();
    }

    private String effectiveSystemPrompt() {
        String effective = systemPromptService.getEffectivePrompt();
        return effective == null || effective.isBlank() ? DEFAULT_SYSTEM_PROMPT : effective;
    }

    private static String buildUserPrompt(Reminder r) {
        StringBuilder sb = new StringBuilder("Recordatorio a redactar:\n");
        sb.append("- Nombre: ").append(nullSafe(r.getName())).append('\n');
        if (notBlank(r.getDescription())) {
            sb.append("- Detalle: ").append(r.getDescription().trim()).append('\n');
        }
        if (r.getCreationDate() != null) {
            sb.append("- Creado: ").append(r.getCreationDate()).append('\n');
        }
        if (r.getNextDate() != null) {
            sb.append("- Se dispara: ").append(r.getNextDate()).append('\n');
        }
        sb.append("\nRedacta el mensaje para el usuario:");
        return sb.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
