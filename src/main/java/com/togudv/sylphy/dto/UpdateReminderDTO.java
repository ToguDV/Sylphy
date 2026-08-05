package com.togudv.sylphy.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateReminderDTO(
        @NotBlank
        String name,
        String description,
        @NotNull
        @Future
        LocalDateTime nextDate,
        RecurrentConfigDTO recurrentConfig,
        String notificationMessage
) {}
