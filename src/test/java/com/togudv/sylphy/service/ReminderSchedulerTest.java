package com.togudv.sylphy.service;

import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.repository.ReminderRepository;
import com.togudv.sylphy.service.notification.NotificationDeliveryException;
import com.togudv.sylphy.service.notification.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    @Mock ReminderRepository repository;
    @Mock ReminderService reminderService;
    @Mock NotificationDispatcher dispatcher;
    @Mock NotificationDispatcher secondDispatcher;

    ReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReminderScheduler(repository, reminderService, List.of(dispatcher));
    }

    @Test
    void empty_doesNothing() {
        when(repository.findByNextDateLessThanEqual(any())).thenReturn(List.of());

        scheduler.tick();

        verifyNoInteractions(dispatcher, reminderService);
    }

    @Test
    void dispatchesEveryDueReminder_andAdvances() {
        Reminder r1 = new Reminder(1L, "a", null, LocalDateTime.now(), LocalDateTime.now(), null, null);
        Reminder r2 = new Reminder(2L, "b", null, LocalDateTime.now(), LocalDateTime.now(), null, null);
        when(repository.findByNextDateLessThanEqual(any())).thenReturn(List.of(r1, r2));

        scheduler.tick();

        verify(dispatcher, times(1)).dispatch(r1);
        verify(dispatcher, times(1)).dispatch(r2);
        verify(reminderService).advanceAfterFire(1L);
        verify(reminderService).advanceAfterFire(2L);
    }

    @Test
    void dispatcherFailure_isLoggedAndSkipsAdvance() {
        Reminder r1 = new Reminder(1L, "a", null, LocalDateTime.now(), LocalDateTime.now(), null, null);
        when(repository.findByNextDateLessThanEqual(any())).thenReturn(List.of(r1));
        doThrow(new NotificationDeliveryException("boom", new RuntimeException()))
                .when(dispatcher).dispatch(r1);

        scheduler.tick();

        verify(dispatcher).dispatch(r1);
        verify(reminderService, never()).advanceAfterFire(any());
    }

    @Test
    void advanceFailure_isLoggedAndDeletesBrokenReminder() {
        Reminder r1 = new Reminder(1L, "a", null, LocalDateTime.now(), LocalDateTime.now(), null, null);
        when(repository.findByNextDateLessThanEqual(any())).thenReturn(List.of(r1));
        doThrow(new IllegalArgumentException("bad config"))
                .when(reminderService).advanceAfterFire(1L);

        scheduler.tick();

        verify(dispatcher).dispatch(r1);
        verify(reminderService).advanceAfterFire(1L);
        verify(repository).delete(r1);
    }

    @Test
    void oneDispatcherFailure_doesNotBlockOthers() {
        Reminder r1 = new Reminder(1L, "a", null, LocalDateTime.now(), LocalDateTime.now(), null, null);
        scheduler = new ReminderScheduler(repository, reminderService, List.of(dispatcher, secondDispatcher));
        when(repository.findByNextDateLessThanEqual(any())).thenReturn(List.of(r1));
        doThrow(new NotificationDeliveryException("boom", new RuntimeException()))
                .when(dispatcher).dispatch(r1);

        scheduler.tick();

        verify(dispatcher).dispatch(r1);
        verify(secondDispatcher).dispatch(r1);
        verify(reminderService, never()).advanceAfterFire(any());
    }

    @Test
    void allDispatchersSucceed_advancesReminder() {
        Reminder r1 = new Reminder(1L, "a", null, LocalDateTime.now(), LocalDateTime.now(), null, null);
        scheduler = new ReminderScheduler(repository, reminderService, List.of(dispatcher, secondDispatcher));
        when(repository.findByNextDateLessThanEqual(any())).thenReturn(List.of(r1));

        scheduler.tick();

        verify(dispatcher).dispatch(r1);
        verify(secondDispatcher).dispatch(r1);
        verify(reminderService).advanceAfterFire(1L);
    }

    @Test
    void noSuchElementOnAdvance_isLoggedAndDoesNotPropagate() {
        Reminder r1 = new Reminder(1L, "a", null, LocalDateTime.now(), LocalDateTime.now(), null, null);
        when(repository.findByNextDateLessThanEqual(any())).thenReturn(List.of(r1));
        doThrow(new NoSuchElementException("vanished"))
                .when(reminderService).advanceAfterFire(1L);

        scheduler.tick();

        verify(dispatcher).dispatch(r1);
        verify(reminderService).advanceAfterFire(1L);
    }
}
