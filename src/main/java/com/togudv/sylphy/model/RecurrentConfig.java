package com.togudv.sylphy.model;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class RecurrentConfig {
    private Frequency frequencyType;
    private Integer recurrenceInterval; // cada X unidades
    private Integer occurrences;

    public static RecurrentConfig of(Frequency frequencyType, Integer recurrenceInterval, Integer occurrences) {
        RecurrentConfig cfg = new RecurrentConfig();
        cfg.setFrequencyType(frequencyType);
        cfg.setRecurrenceInterval(recurrenceInterval);
        cfg.setOccurrences(occurrences);
        return cfg;
    }
}
