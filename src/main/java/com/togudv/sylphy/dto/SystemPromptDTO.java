package com.togudv.sylphy.dto;

import java.time.LocalDateTime;

/**
 * Estado del system prompt activo. {@code updatedAt} es null cuando
 * no hay prompt configurado y esta en efecto el default de properties.
 */
public record SystemPromptDTO(
        String content,
        LocalDateTime updatedAt
) {}
