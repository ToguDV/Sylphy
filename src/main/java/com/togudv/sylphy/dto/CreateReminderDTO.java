package com.togudv.sylphy.dto;

import com.togudv.sylphy.model.RecurrentConfig;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateReminderDTO(
        @NotBlank
        String name,
        String description,
        @Future
        LocalDateTime nextDate,
        RecurrentConfig recurrentConfig
)
{}
