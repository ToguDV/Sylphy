package com.togudv.sylphy.repository;

import com.togudv.sylphy.model.Reminder;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderRepository extends CrudRepository<Reminder, Long> {
    List<Reminder> findByNextDateLessThanEqual(LocalDateTime threshold);
}
