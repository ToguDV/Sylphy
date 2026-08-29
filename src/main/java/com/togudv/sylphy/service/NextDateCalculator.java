package com.togudv.sylphy.service;

import com.togudv.sylphy.model.Frequency;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.model.RecurrentConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class NextDateCalculator {

    public LocalDateTime next(Reminder reminder) {
        RecurrentConfig cfg = reminder.getRecurrentConfig();
        if (cfg == null) {
            return null;
        }
        Frequency freq = cfg.getFrequencyType();
        if (freq == null) {
            throw new IllegalArgumentException(
                    "RecurrentConfig.frequencyType is required for a recurring reminder");
        }
        int interval = cfg.getRecurrenceInterval() == null ? 1 : cfg.getRecurrenceInterval();
        if (interval < 1) {
            throw new IllegalArgumentException(
                    "RecurrentConfig.recurrenceInterval must be >= 1, got: " + interval);
        }
        LocalDateTime anchor = reminder.getNextDate();
        return switch (freq) {
            case MINUTELY -> anchor.plus(interval, ChronoUnit.MINUTES);
            case HOURLY   -> anchor.plus(interval, ChronoUnit.HOURS);
            case DAILY    -> anchor.plus(interval, ChronoUnit.DAYS);
            case WEEKLY   -> anchor.plusWeeks(interval);
            case MONTHLY  -> clampDayOfMonth(anchor, anchor.plusMonths(interval), cfg);
            case YEARLY   -> clampDayOfMonth(anchor, anchor.plus(interval, ChronoUnit.YEARS), cfg);
        };
    }

    /**
     * Ajusta el dia del mes destino al ultimo dia disponible cuando el mes
     * destino tiene menos dias que el dia fijado por el usuario (31 ene -> 28
     * feb -> 31 mar). Se usa el dia recordado en la configuracion, no el dia
     * del ancla, para que un recordatorio del dia 31 no derive al dia 28 tras
     * el primer mes corto.
     */
    private static LocalDateTime clampDayOfMonth(LocalDateTime anchor, LocalDateTime shifted, RecurrentConfig cfg) {
        Integer intendedDay = cfg.getDayOfMonth();
        int day = intendedDay == null ? anchor.getDayOfMonth() : intendedDay;
        int lastDay = shifted.toLocalDate().lengthOfMonth();
        return shifted.withDayOfMonth(Math.min(day, lastDay));
    }
}
