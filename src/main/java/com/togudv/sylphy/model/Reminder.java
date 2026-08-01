package com.togudv.sylphy.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
}
