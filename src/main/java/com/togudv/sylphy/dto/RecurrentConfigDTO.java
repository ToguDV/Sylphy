package com.togudv.sylphy.dto;

import com.togudv.sylphy.model.Frequency;

public record RecurrentConfigDTO(
        Frequency frequencyType,
        Integer recurrenceInterval,
        Integer occurrences
) {}
