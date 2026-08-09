package com.togudv.sylphy.service;

import com.togudv.sylphy.model.SystemPrompt;
import com.togudv.sylphy.repository.SystemPromptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class SystemPromptService {

    private final SystemPromptRepository repository;
    private final Path systemPromptFile;

    public SystemPromptService(SystemPromptRepository repository,
                               @Value("${sylphy.system-prompt.file:system-prompt.txt}") String systemPromptFile) {
        this.repository = repository;
        this.systemPromptFile = Path.of(systemPromptFile);
    }

    /**
     * Prompt que el asistente debe usar en cada generacion:
     * el configurado en BD si existe y no esta vacio; si no, el default
     * del archivo (que puede estar vacio o no existir = sin system prompt).
     */
    public String getEffectivePrompt() {
        Optional<SystemPrompt> stored = repository.findById(SystemPrompt.FIXED_ID);
        if (stored.isPresent() && !stored.get().getContent().isBlank()) {
            return stored.get().getContent();
        }
        return readFilePrompt();
    }

    private String readFilePrompt() {
        try {
            if (!Files.exists(systemPromptFile)) {
                return "";
            }
            String content = Files.readString(systemPromptFile).trim();
            return content.isBlank() ? "" : content;
        } catch (IOException e) {
            log.warn("No se pudo leer el system prompt del archivo {}: {}", systemPromptFile, e.getMessage());
            return "";
        }
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
     * Elimina el prompt configurado para volver al default del archivo.
     * Idempotente: no falla si no habia fila.
     */
    public void reset() {
        repository.findById(SystemPrompt.FIXED_ID).ifPresent(repository::delete);
    }
}
