package com.togudv.sylphy.service;

import com.togudv.sylphy.model.Frequency;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.model.RecurrentConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NextDateCalculatorTest {

    private final NextDateCalculator calculator = new NextDateCalculator();

    @Test
    void minutely_interval2() {
        Reminder r = build(LocalDateTime.of(2026, 1, 1, 10, 0), Frequency.MINUTELY, 2);
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 2), calculator.next(r));
    }

    @Test
    void hourly_defaultIntervalIs1() {
        Reminder r = build(LocalDateTime.of(2026, 1, 1, 10, 0), Frequency.HOURLY, null);
        assertEquals(LocalDateTime.of(2026, 1, 1, 11, 0), calculator.next(r));
    }

    @Test
    void daily_interval3() {
        Reminder r = build(LocalDateTime.of(2026, 1, 1, 10, 0), Frequency.DAILY, 3);
        assertEquals(LocalDateTime.of(2026, 1, 4, 10, 0), calculator.next(r));
    }

    @Test
    void weekly_interval1() {
        Reminder r = build(LocalDateTime.of(2026, 1, 5, 10, 0), Frequency.WEEKLY, 1);
        assertEquals(LocalDateTime.of(2026, 1, 12, 10, 0), calculator.next(r));
    }

    @Test
    void weekly_interval2() {
        Reminder r = build(LocalDateTime.of(2026, 1, 5, 10, 0), Frequency.WEEKLY, 2);
        assertEquals(LocalDateTime.of(2026, 1, 19, 10, 0), calculator.next(r));
    }

    @Test
    void monthly_interval1() {
        Reminder r = build(LocalDateTime.of(2026, 1, 15, 10, 0), Frequency.MONTHLY, 1);
        assertEquals(LocalDateTime.of(2026, 2, 15, 10, 0), calculator.next(r));
    }

    @Test
    void monthly_interval2() {
        Reminder r = build(LocalDateTime.of(2026, 1, 15, 10, 0), Frequency.MONTHLY, 2);
        assertEquals(LocalDateTime.of(2026, 3, 15, 10, 0), calculator.next(r));
    }

    @Test
    void monthly_day31_clampsToLastDayOfShorterMonth() {
        Reminder r = build(LocalDateTime.of(2026, 1, 31, 10, 0), Frequency.MONTHLY, 1);
        assertEquals(LocalDateTime.of(2026, 2, 28, 10, 0), calculator.next(r));
    }

    @Test
    void monthly_day31_recoversFullMonthAfterClamp() {
        Reminder r = buildWithDay(LocalDateTime.of(2026, 2, 28, 10, 0), Frequency.MONTHLY, 1, 31);
        assertEquals(LocalDateTime.of(2026, 3, 31, 10, 0), calculator.next(r));
    }

    @Test
    void monthly_day30_clampsInFebruaryAndRecovers() {
        Reminder r = buildWithDay(LocalDateTime.of(2026, 1, 30, 10, 0), Frequency.MONTHLY, 1, 30);
        assertEquals(LocalDateTime.of(2026, 2, 28, 10, 0), calculator.next(r));
        Reminder next = buildWithDay(LocalDateTime.of(2026, 2, 28, 10, 0), Frequency.MONTHLY, 1, 30);
        assertEquals(LocalDateTime.of(2026, 3, 30, 10, 0), calculator.next(next));
    }

    @Test
    void yearly_leapDay_normalisesToFeb28() {
        Reminder r = build(LocalDateTime.of(2024, 2, 29, 10, 0), Frequency.YEARLY, 1);
        assertEquals(LocalDateTime.of(2025, 2, 28, 10, 0), calculator.next(r));
    }

    @Test
    void nullRecurrentConfig_returnsNull_signalsDelete() {
        Reminder r = build(LocalDateTime.of(2026, 1, 1, 10, 0), null, null);
        assertNull(calculator.next(r));
    }

    @Test
    void nullFrequencyType_throws() {
        Reminder r = build(LocalDateTime.of(2026, 1, 1, 10, 0), null, 1);
        assertThrows(IllegalArgumentException.class, () -> calculator.next(r));
    }

    @Test
    void zeroInterval_throws() {
        Reminder r = build(LocalDateTime.of(2026, 1, 1, 10, 0), Frequency.DAILY, 0);
        assertThrows(IllegalArgumentException.class, () -> calculator.next(r));
    }

    private static Reminder build(LocalDateTime nextDate, Frequency frequency, Integer interval) {
        return buildWithDay(nextDate, frequency, interval, null);
    }

    private static Reminder buildWithDay(LocalDateTime nextDate, Frequency frequency, Integer interval,
                                         Integer dayOfMonth) {
        RecurrentConfig cfg = null;
        if (frequency != null || interval != null) {
            cfg = new RecurrentConfig();
            cfg.setFrequencyType(frequency);
            cfg.setRecurrenceInterval(interval);
            cfg.setDayOfMonth(dayOfMonth);
        }
        return new Reminder(null, "test", null, nextDate, nextDate, cfg, null);
    }
}
