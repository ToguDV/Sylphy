package com.togudv.sylphy.dto;

import java.time.LocalDateTime;

public record ReminderDTO(
        Long id,
        String name,
        String description,
        LocalDateTime creationDate,
        LocalDateTime nextDate,
        RecurrentConfigDTO recurrentConfig,
        String notificationMessage
) {}
