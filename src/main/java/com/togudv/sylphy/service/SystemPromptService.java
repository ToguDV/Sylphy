package com.togudv.sylphy.service;

import com.togudv.sylphy.model.SystemPrompt;
import com.togudv.sylphy.repository.SystemPromptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class SystemPromptService {

    private final SystemPromptRepository repository;
    private final String defaultPrompt;

    public SystemPromptService(SystemPromptRepository repository,
                               @Value("${sylphy.system-prompt.default:}") String defaultPrompt) {
        this.repository = repository;
        this.defaultPrompt = defaultPrompt;
    }

    /**
     * Prompt que el asistente debe usar en cada generacion:
     * el configurado en BD si existe y no esta vacio; si no, el default
     * de properties (que puede ser vacio = sin system prompt).
     */
    public String getEffectivePrompt() {
        Optional<SystemPrompt> stored = repository.findById(SystemPrompt.FIXED_ID);
        if (stored.isPresent() && !stored.get().getContent().isBlank()) {
            return stored.get().getContent();
        }
        return defaultPrompt;
    }

    /**
     * Prompt configurado en BD, si existe. {@code updatedAt == null} en la
     * respuesta indica que el prompt activo es el default.
     */
    public Optional<SystemPrompt> getStored() {
        return repository.findById(SystemPrompt.FIXED_ID);
    }

    public SystemPrompt update(String content) {
        SystemPrompt prompt = repository.findById(SystemPrompt.FIXED_ID)
                .orElseGet(() -> new SystemPrompt(SystemPrompt.FIXED_ID, content, LocalDateTime.now()));
        prompt.setContent(content);
        prompt.setUpdatedAt(LocalDateTime.now());
        return repository.save(prompt);
    }

    /**
     * Elimina el prompt configurado para volver al default de properties.
     * Idempotente: no falla si no habia fila.
     */
    public void reset() {
        repository.findById(SystemPrompt.FIXED_ID).ifPresent(repository::delete);
    }
}
