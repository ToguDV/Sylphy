package com.togudv.sylphy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.LocalDateTime;

/**
 * System prompt activo del asistente (fila unica, id fijo 1).
 * La personalidad e instrucciones del bot se configuran por API o
 * por chat; si no hay fila, se usa el default de properties.
 */
@Entity
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SystemPrompt {

    public static final long FIXED_ID = 1L;

    @Id
    private Long id;

    @NonNull
    @Column(length = 10000)
    private String content;

    @NonNull
    private LocalDateTime updatedAt;
}
