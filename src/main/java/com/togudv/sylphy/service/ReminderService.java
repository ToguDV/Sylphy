package com.togudv.sylphy.service;

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
        repository.save(reminder);
    }

    public Iterable<Reminder> getAll() {
        return repository.findAll();
    }

    public Reminder updateById(Long id, Reminder updated) {
        Reminder existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Reminder no encontrado: " + id));
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setNextDate(updated.getNextDate());
        existing.setRecurrentConfig(updated.getRecurrentConfig());
        return repository.save(existing);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public Reminder advanceAfterFire(Long id) {
        Reminder r = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Reminder no encontrado: " + id));
        LocalDateTime next = calculator.next(r);
        if (next == null) {
            repository.delete(r);
            return null;
        }
        r.setNextDate(next);
        return repository.save(r);
    }
}
