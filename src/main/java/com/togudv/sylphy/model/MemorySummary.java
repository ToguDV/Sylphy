package com.togudv.sylphy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.LocalDateTime;

/**
 * Resumen consolidado de la memoria episodica (modelo decremental).
 * Cada nivel resume el inferior; cuando el nivel superior existe, el inferior
 * se elimina. El resumen anual se conserva indefinidamente.
 */
@Entity
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MemorySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private String conversationId;

    @NonNull
    @Enumerated(EnumType.STRING)
    private MemoryLevel level;

    @NonNull
    @Column(length = 4000)
    private String content;

    @NonNull
    private LocalDateTime createdAt;

    /**
     * Clave del periodo cubierto: fecha (DAILY, "2026-08-05"), semana ISO
     * (WEEKLY, "2026-W32"), mes (MONTHLY, "2026-08") o anio (ANNUAL, "2026").
     * Null en los resumenes WINDOW, que se pliegan por fecha de creacion.
     */
    @Column(length = 32)
    private String periodKey;
}
