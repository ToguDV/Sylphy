package com.togudv.sylphy.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record CreateReminderDTO(
        @NotBlank
        String name,
        String description,
        @Future
        LocalDateTime nextDate,
        RecurrentConfigDTO recurrentConfig
)
{}
