package com.togudv.sylphy.model;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class RecurrentConfig {
    private Frequency frequencyType;
    private Integer recurrenceInterval; // cada X unidades
    private Integer occurrences;
}
