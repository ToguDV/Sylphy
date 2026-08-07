package com.togudv.sylphy.service.tools;

import com.togudv.sylphy.model.SystemPrompt;
import com.togudv.sylphy.service.AITool;
import com.togudv.sylphy.service.SystemPromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class SystemPromptAITool implements AITool {

    private static final int MAX_LENGTH = 10000;

    private final SystemPromptService systemPromptService;

    public SystemPromptAITool(SystemPromptService systemPromptService) {
        this.systemPromptService = systemPromptService;
    }

    @Override
    public String getName() {
        return "system-prompt";
    }

    @Tool(description = "Muestra el system prompt activo del asistente (sus instrucciones de "
            + "personalidad y comportamiento) y su fuente: configurado en la base de datos "
            + "o el valor por defecto.")
    public String getSystemPrompt() {
        Optional<SystemPrompt> stored = systemPromptService.getStored();
        String effective = systemPromptService.getEffectivePrompt();
        if (effective == null || effective.isBlank()) {
            return "No hay system prompt configurado.";
        }
        String source = stored.isPresent() && !stored.get().getContent().isBlank()
                ? "configurado (actualizado el " + stored.get().getUpdatedAt() + ")"
                : "por defecto";
        return "System prompt activo (fuente: " + source + "):\n" + effective;
    }

    @Tool(description = "Actualiza el system prompt del asistente, es decir, sus instrucciones "
            + "de personalidad y comportamiento. Redacta el nuevo contenido en espanol siguiendo "
            + "lo que pida el usuario. Confirma el cambio al usuario.")
    public String updateSystemPrompt(
            @ToolParam(description = "Nuevo contenido completo del system prompt, en espanol") String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("El system prompt no puede estar vacio");
        }
        if (content.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("El system prompt no puede superar " + MAX_LENGTH + " caracteres");
        }
        systemPromptService.update(content);
        log.info("tool: system prompt actualizado");
        return "System prompt actualizado. A partir de ahora seguire esas instrucciones.";
    }

    @Tool(description = "Restaura el system prompt del asistente a su valor por defecto, "
            + "descartando el configurado previamente.")
    public String resetSystemPrompt() {
        systemPromptService.reset();
        log.info("tool: system prompt restaurado al default");
        return "System prompt restaurado al valor por defecto.";
    }
}
