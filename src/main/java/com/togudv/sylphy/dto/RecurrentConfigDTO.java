package com.togudv.sylphy.dto;

import com.togudv.sylphy.model.Frequency;
import jakarta.validation.constraints.Min;

public record RecurrentConfigDTO(
        Frequency frequencyType,
        @Min(1)
        Integer recurrenceInterval,
        @Min(1)
        Integer occurrences
) {}
