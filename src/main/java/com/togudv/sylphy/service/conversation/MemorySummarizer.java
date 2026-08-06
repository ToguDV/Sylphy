package com.togudv.sylphy.service.conversation;

import com.togudv.sylphy.model.MemoryLevel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * Genera los resumenes de la memoria episodica con un ChatClient dedicado,
 * sin herramientas y con temperatura baja. Si el modelo falla o devuelve
 * contenido vacio, devuelve null: el solicitante debe abortar la
 * consolidacion sin borrar nada (la memoria nunca se pierde por un fallo
 * del LLM).
 */
@Service
public class MemorySummarizer {

    private static final String SYSTEM_PROMPT = """
            Eres Sylphy, el asistente personal del usuario. Tu tarea es resumir
            conversaciones y periodos de actividad del usuario en espanol.
            Conserva los datos importantes: fechas, tareas y compromisos,
            recordatorios creados, decisiones, preferencias y contexto personal
            relevante. No inventes informacion: resume solo lo que se te da.""";
    private static final String PROMPT_BASE = "Resume en espanol, en prosa, maximo %d palabras: %s.%n%n%s";

    private final ChatClient chatClient;

    public MemorySummarizer(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(0.3))
                .build();
    }

    public String summarize(MemoryLevel level, String content) {
        String instruction = instructionFor(level);
        String userPrompt = PROMPT_BASE.formatted(wordLimitFor(level), instruction, content);
        String result = chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        if (result == null || result.isBlank()) {
            return null;
        }
        return result.trim();
    }

    private static String instructionFor(MemoryLevel level) {
        return switch (level) {
            case WINDOW -> "lo mas relevante de esta parte de la conversacion";
            case DAILY -> "lo mas importante que sucedio o se hablo este dia";
            case WEEKLY -> "lo mas importante que sucedio o se hablo durante la semana";
            case MONTHLY -> "lo mas importante que sucedio o se hablo durante el mes";
            case ANNUAL -> "lo mas importante que sucedio o se hablo durante el anio";
        };
    }

    private static int wordLimitFor(MemoryLevel level) {
        return switch (level) {
            case WINDOW -> 120;
            case DAILY -> 150;
            case WEEKLY -> 200;
            case MONTHLY -> 300;
            case ANNUAL -> 400;
        };
    }
}
