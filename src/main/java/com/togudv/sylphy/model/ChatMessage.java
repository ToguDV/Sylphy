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
 * Mensaje crudo de una conversacion (hilo compartido entre canales).
 * Se conserva solo la ventana reciente; los mensajes consolidados en un
 * resumen se eliminan.
 */
@Entity
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private String conversationId;

    @NonNull
    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @NonNull
    @Column(length = 4000)
    private String content;

    @NonNull
    private LocalDateTime timestamp;
}
