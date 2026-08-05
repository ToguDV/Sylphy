package com.togudv.sylphy.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NonNull
    @NotBlank
    private String name;
    private String description;
    @NonNull
    private LocalDateTime creationDate;
    @NonNull
    private LocalDateTime nextDate;
    @Embedded
    private RecurrentConfig recurrentConfig;
    @Column(length = 1000)
    private String notificationMessage;

    @SuppressFBWarnings(
            value = {"EI2", "NP"},
            justification = "EI2: Reminder es la raiz del agregado; RecurrentConfig es @Embedded y se muta solo dentro del agregado. "
                    + "NP: el ctor acepta creationDate null solo para el path de update del mapper; ReminderService nunca persiste ese valor, copia solo los campos editables sobre la entidad existente.")
    public Reminder(Long id, String name, String description, @Nullable LocalDateTime creationDate,
                    LocalDateTime nextDate, RecurrentConfig recurrentConfig, String notificationMessage) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.creationDate = creationDate;
        this.nextDate = nextDate;
        this.recurrentConfig = recurrentConfig;
        this.notificationMessage = notificationMessage;
    }
}
