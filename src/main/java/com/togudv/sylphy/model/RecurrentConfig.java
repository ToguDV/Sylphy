package com.togudv.sylphy.model;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Embeddable
@Data
public class RecurrentConfig {
    private Frequency frequencyType;
    private Integer recurrenceInterval; // cada X unidades
    private Integer occurrences;
    /**
     * Dia del mes que el usuario fijo para recurrencias MENSUALES o ANUALES.
     * Lo asigna el servicio a partir del nextDate al crear o actualizar; permite
     * que un recordatorio del dia 31 siga disparandose el 31 tras un mes corto
     * (31 ene -> 28 feb -> 31 mar). null = usar el dia del ancla.
     */
    private Integer dayOfMonth;

    public static RecurrentConfig of(Frequency frequencyType, Integer recurrenceInterval, Integer occurrences) {
        RecurrentConfig cfg = new RecurrentConfig();
        cfg.setFrequencyType(frequencyType);
        cfg.setRecurrenceInterval(recurrenceInterval);
        cfg.setOccurrences(occurrences);
        return cfg;
    }
}
