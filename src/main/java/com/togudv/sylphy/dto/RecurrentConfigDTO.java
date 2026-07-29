package com.togudv.sylphy.dto;

import com.togudv.sylphy.model.Frequency;

import java.time.DayOfWeek;
import java.util.Set;

public record RecurrentConfigDTO(
        Set<Frequency> frequencyType,
        Integer recurrenceInterval,
        Set<DayOfWeek> daysOfWeek,
        Set<Integer> daysOfMonth,
        Integer occurrences
) {
    public RecurrentConfigDTO {
        frequencyType = frequencyType == null ? null : Set.copyOf(frequencyType);
        daysOfWeek = daysOfWeek == null ? null : Set.copyOf(daysOfWeek);
        daysOfMonth = daysOfMonth == null ? null : Set.copyOf(daysOfMonth);
    }
}
