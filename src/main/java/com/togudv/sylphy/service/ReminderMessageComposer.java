package com.togudv.sylphy.service;

import com.togudv.sylphy.model.Reminder;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
public class ReminderMessageComposer {

    private static final String SYSTEM_PROMPT = """
            Eres Sylphy, un asistente personal. Tu tarea es redactar el mensaje que se \
            enviara al usuario cuando se dispare un recordatorio. Tono: cercano, en \
            segunda persona, conciso (una o dos frases como maximo). Sin markdown, sin \
            comillas, sin emojis. Devuelve unicamente el texto del mensaje, sin \
            introducciones ni explicaciones adicionales.
            """;

    private final ChatClient chatClient;

    public ReminderMessageComposer(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder().temperature(0.7))
                .build();
    }

    public String compose(Reminder reminder) {
        return chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(buildUserPrompt(reminder))
                .call()
                .content();
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
        sb.append("\nRedacta el mensaje:");
        return sb.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
