package com.togudv.sylphy.service;

import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.repository.ReminderRepository;
import com.togudv.sylphy.service.notification.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderRepository repository;
    private final ReminderService reminderService;
    private final List<NotificationDispatcher> dispatchers;

    @Scheduled(fixedDelayString = "${sylphy.scheduler.tick-millis:60000}")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> due = repository.findByNextDateLessThanEqual(now);
        if (due.isEmpty()) {
            return;
        }
        log.info("scheduler: {} recordatorio(s) pendientes", due.size());
        for (Reminder r : due) {
            fireAndAdvance(r);
        }
    }

    private void fireAndAdvance(Reminder r) {
        for (NotificationDispatcher d : dispatchers) {
            try {
                d.dispatch(r);
            } catch (RuntimeException e) {
                log.error("scheduler: error al despachar recordatorio id={}", r.getId(), e);
                return;
            }
        }
        try {
            reminderService.advanceAfterFire(r.getId());
        } catch (IllegalArgumentException e) {
            log.error("scheduler: config invalida en recordatorio id={}, se conserva sin reprogramar",
                    r.getId(), e);
        } catch (NoSuchElementException e) {
            log.warn("scheduler: recordatorio id={} desaparecio tras dispatch", r.getId());
        }
    }
}
