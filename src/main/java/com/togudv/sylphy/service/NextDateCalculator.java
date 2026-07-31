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
            case MONTHLY  -> anchor.plusMonths(interval);
            case YEARLY   -> anchor.plus(interval, ChronoUnit.YEARS);
        };
    }
}
