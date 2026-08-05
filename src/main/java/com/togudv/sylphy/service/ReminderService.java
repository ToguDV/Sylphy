package com.togudv.sylphy.service;

import com.togudv.sylphy.model.RecurrentConfig;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.repository.ReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
@Transactional
public class ReminderService {

    private final ReminderRepository repository;
    private final NextDateCalculator calculator;

    public ReminderService(ReminderRepository repository, NextDateCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    public void create(Reminder reminder) {
        validateRecurrence(reminder.getRecurrentConfig());
        repository.save(reminder);
    }

    private static void validateRecurrence(RecurrentConfig config) {
        if (config == null) {
            return;
        }
        if (config.getFrequencyType() == null) {
            throw new IllegalArgumentException(
                    "RecurrentConfig.frequencyType es obligatorio en un recordatorio recurrente");
        }
        int interval = config.getRecurrenceInterval() == null ? 1 : config.getRecurrenceInterval();
        if (interval < 1) {
            throw new IllegalArgumentException(
                    "RecurrentConfig.recurrenceInterval debe ser >= 1, recibido: " + interval);
        }
        if (config.getOccurrences() != null && config.getOccurrences() < 1) {
            throw new IllegalArgumentException(
                    "RecurrentConfig.occurrences debe ser >= 1, recibido: " + config.getOccurrences());
        }
    }

    public Iterable<Reminder> getAll() {
        return repository.findAll();
    }

    public Reminder getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Reminder no encontrado: " + id));
    }

    public Reminder updateById(Long id, Reminder updated) {
        validateRecurrence(updated.getRecurrentConfig());
        Reminder existing = getById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setNextDate(updated.getNextDate());
        existing.setRecurrentConfig(updated.getRecurrentConfig());
        existing.setNotificationMessage(updated.getNotificationMessage());
        return repository.save(existing);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public Reminder advanceAfterFire(Long id) {
        Reminder r = getById(id);
        RecurrentConfig cfg = r.getRecurrentConfig();
        if (cfg != null && cfg.getOccurrences() != null) {
            int remaining = cfg.getOccurrences();
            if (remaining <= 1) {
                repository.delete(r);
                return null;
            }
            cfg.setOccurrences(remaining - 1);
        }
        LocalDateTime next = calculator.next(r);
        if (next == null) {
            repository.delete(r);
            return null;
        }
        r.setNextDate(next);
        return repository.save(r);
    }
}
