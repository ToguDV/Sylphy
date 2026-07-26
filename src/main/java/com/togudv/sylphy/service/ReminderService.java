package com.togudv.sylphy.service;

import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.repository.ReminderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReminderService {

    @Autowired
    private ReminderRepository repository;

    public void create(Reminder reminder) {
        repository.save(reminder);
    }

    public Iterable<Reminder> getAll() {
        return repository.findAll();
    }

    public void updateById(Long id, ) {
        Long id = reminder.getId();
        repository.findById(id);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
