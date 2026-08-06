package com.togudv.sylphy.service;

import com.togudv.sylphy.model.Frequency;
import com.togudv.sylphy.model.RecurrentConfig;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.repository.ReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    ReminderRepository repository;
    @Mock
    NextDateCalculator calculator;

    ReminderService service;

    @BeforeEach
    void setUp() {
        service = new ReminderService(repository, calculator);
    }

    @Test
    void create_acceptsValidRecurrentConfig() {
        Reminder r = reminder(RecurrentConfig.of(Frequency.DAILY, 2, null));

        service.create(r);

        verify(repository).save(r);
    }

    @Test
    void create_acceptsMinimalRecurrentConfig() {
        Reminder r = reminder(RecurrentConfig.of(Frequency.HOURLY, null, null));

        service.create(r);

        verify(repository).save(r);
    }

    @Test
    void create_acceptsOccurrences1() {
        Reminder r = reminder(RecurrentConfig.of(Frequency.DAILY, 1, 1));

        service.create(r);

        verify(repository).save(r);
    }

    @Test
    void create_acceptsNonRecurrentReminder() {
        Reminder r = reminder(null);

        service.create(r);

        verify(repository).save(r);
    }

    @Test
    void create_monthly_setsDayOfMonthFromNextDate() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 31, 10, 0);
        Reminder r = new Reminder(null, "a", null, t, t, RecurrentConfig.of(Frequency.MONTHLY, 1, null), null);

        service.create(r);

        assertEquals(31, r.getRecurrentConfig().getDayOfMonth());
        verify(repository).save(r);
    }

    @Test
    void create_daily_doesNotSetDayOfMonth() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 31, 10, 0);
        Reminder r = new Reminder(null, "a", null, t, t, RecurrentConfig.of(Frequency.DAILY, 1, null), null);

        service.create(r);

        assertNull(r.getRecurrentConfig().getDayOfMonth());
        verify(repository).save(r);
    }

    @Test
    void updateById_monthly_resetsDayOfMonthFromNewNextDate() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder existing = new Reminder(1L, "viejo", null, t, t, null, null);
        LocalDateTime newDate = LocalDateTime.of(2026, 1, 31, 10, 0);
        Reminder patch = new Reminder(null, "nuevo", null, t, newDate,
                RecurrentConfig.of(Frequency.MONTHLY, 1, null), null);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Reminder result = service.updateById(1L, patch);

        assertSame(existing, result);
        assertEquals(31, existing.getRecurrentConfig().getDayOfMonth());
    }

    @Test
    void create_rejectsOccurrencesZero() {
        Reminder r = reminder(RecurrentConfig.of(Frequency.DAILY, 1, 0));

        assertThrows(IllegalArgumentException.class, () -> service.create(r));
        verify(repository, never()).save(r);
    }

    @Test
    void create_rejectsNegativeOccurrences() {
        Reminder r = reminder(RecurrentConfig.of(Frequency.DAILY, 1, -3));

        assertThrows(IllegalArgumentException.class, () -> service.create(r));
        verify(repository, never()).save(r);
    }

    @Test
    void create_rejectsZeroInterval() {
        Reminder r = reminder(RecurrentConfig.of(Frequency.DAILY, 0, null));

        assertThrows(IllegalArgumentException.class, () -> service.create(r));
        verify(repository, never()).save(r);
    }

    @Test
    void create_rejectsNegativeInterval() {
        Reminder r = reminder(RecurrentConfig.of(Frequency.DAILY, -1, null));

        assertThrows(IllegalArgumentException.class, () -> service.create(r));
        verify(repository, never()).save(r);
    }

    @Test
    void create_rejectsConfigWithoutFrequency() {
        Reminder r = reminder(RecurrentConfig.of(null, 1, null));

        assertThrows(IllegalArgumentException.class, () -> service.create(r));
        verify(repository, never()).save(r);
    }

    @Test
    void getAll_returnsAll() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder r = new Reminder(1L, "a", null, t, t, null, null);
        when(repository.findAll()).thenReturn(java.util.List.of(r));

        Iterable<Reminder> result = service.getAll();

        assertSame(r, result.iterator().next());
    }

    @Test
    void getById_returnsExisting() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder r = new Reminder(1L, "a", null, t, t, null, null);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(r));

        assertSame(r, service.getById(1L));
    }

    @Test
    void getById_throwsWhenMissing() {
        when(repository.findById(42L)).thenReturn(java.util.Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> service.getById(42L));
    }

    @Test
    void updateById_copiesFieldsAndSaves() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder existing = new Reminder(1L, "viejo", "desc vieja", t, t, null, "msg viejo");
        Reminder patch = new Reminder(null, "nuevo", "desc nueva", t, t.plusDays(1),
                RecurrentConfig.of(Frequency.DAILY, 1, 2), "msg nuevo");
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Reminder result = service.updateById(1L, patch);

        assertSame(existing, result);
        assertEquals("nuevo", existing.getName());
        assertEquals("desc nueva", existing.getDescription());
        assertEquals(t.plusDays(1), existing.getNextDate());
        assertEquals(2, existing.getRecurrentConfig().getOccurrences());
        assertEquals("msg nuevo", existing.getNotificationMessage());
    }

    @Test
    void updateById_rejectsInvalidRecurrence() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder patch = new Reminder(null, "nuevo", null, t, t,
                RecurrentConfig.of(Frequency.DAILY, 0, null), null);

        assertThrows(IllegalArgumentException.class, () -> service.updateById(1L, patch));
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void updateById_throwsWhenMissing() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder patch = new Reminder(null, "nuevo", null, t, t, null, null);
        when(repository.findById(9L)).thenReturn(java.util.Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> service.updateById(9L, patch));
    }

    @Test
    void deleteById_deletes() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder r = new Reminder(3L, "a", null, t, t, null, null);
        when(repository.findById(3L)).thenReturn(java.util.Optional.of(r));

        service.deleteById(3L);

        verify(repository).delete(r);
    }

    @Test
    void deleteById_throwsWhenMissing() {
        when(repository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> service.deleteById(99L));
        verify(repository, never()).delete(any());
    }

    @Test
    void advanceAfterFire_oneShotReminderIsDeleted() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder r = new Reminder(1L, "a", null, t, t, null, null);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(r));

        Reminder result = service.advanceAfterFire(1L);

        assertNull(result);
        verify(repository).delete(r);
    }

    @Test
    void advanceAfterFire_recurrentWithoutOccurrencesSchedulesNext() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder r = new Reminder(1L, "a", null, t, t,
                RecurrentConfig.of(Frequency.DAILY, 1, null), null);
        LocalDateTime next = t.plusDays(1);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(r));
        when(calculator.next(r)).thenReturn(next);
        when(repository.save(r)).thenReturn(r);

        Reminder result = service.advanceAfterFire(1L);

        assertSame(r, result);
        assertEquals(next, r.getNextDate());
        verify(repository).save(r);
    }

    @Test
    void advanceAfterFire_lastOccurrenceDeletesReminder() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder r = new Reminder(1L, "a", null, t, t,
                RecurrentConfig.of(Frequency.DAILY, 1, 1), null);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(r));

        Reminder result = service.advanceAfterFire(1L);

        assertNull(result);
        verify(repository).delete(r);
        verify(calculator, never()).next(any());
    }

    @Test
    void advanceAfterFire_decrementsRemainingOccurrences() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder r = new Reminder(1L, "a", null, t, t,
                RecurrentConfig.of(Frequency.DAILY, 1, 3), null);
        LocalDateTime next = t.plusDays(1);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(r));
        when(calculator.next(r)).thenReturn(next);
        when(repository.save(r)).thenReturn(r);

        Reminder result = service.advanceAfterFire(1L);

        assertSame(r, result);
        assertEquals(2, r.getRecurrentConfig().getOccurrences());
        assertEquals(next, r.getNextDate());
        verify(repository, never()).delete(any());
    }

    @Test
    void advanceAfterFire_throwsWhenMissing() {
        when(repository.findById(77L)).thenReturn(java.util.Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> service.advanceAfterFire(77L));
    }

    private static Reminder reminder(RecurrentConfig cfg) {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new Reminder(null, "test", null, t, t, cfg, null);
    }
}