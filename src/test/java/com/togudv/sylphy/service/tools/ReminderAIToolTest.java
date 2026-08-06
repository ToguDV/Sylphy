package com.togudv.sylphy.service.tools;

import com.togudv.sylphy.model.Frequency;
import com.togudv.sylphy.model.RecurrentConfig;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderAIToolTest {

    @Mock
    ReminderService reminderService;

    ReminderAITool tool;

    @BeforeEach
    void setUp() {
        tool = new ReminderAITool(reminderService);
    }

    @Test
    void buildsRecurrentConfig_whenFrequencyProvided() {
        LocalDateTime remind = LocalDateTime.of(2026, 1, 2, 10, 0);

        tool.createReminder("medicina", "tomar pastilla", remind,
                "Es hora de tomar tu pastilla", Frequency.DAILY, 2, 5);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderService, times(1)).create(captor.capture());
        Reminder saved = captor.getValue();
        RecurrentConfig cfg = saved.getRecurrentConfig();
        assertEquals(Frequency.DAILY, cfg.getFrequencyType());
        assertEquals(2, cfg.getRecurrenceInterval());
        assertEquals(5, cfg.getOccurrences());
        assertEquals(remind, saved.getNextDate());
    }

    @Test
    void creationDate_isAssignedByServer_notByCaller() {
        LocalDateTime remind = LocalDateTime.of(2026, 1, 2, 10, 0);

        tool.createReminder("medicina", null, remind, null, null, null, null);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderService).create(captor.capture());
        Reminder saved = captor.getValue();
        assertNotNull(saved.getCreationDate());
        assertTrue(saved.getCreationDate().isBefore(LocalDateTime.now().plusMinutes(1)));
    }

    @Test
    void leavesConfigNull_whenNoFrequency() {
        LocalDateTime remind = LocalDateTime.of(2026, 1, 2, 10, 0);

        tool.createReminder("reunion", "reunion de equipo", remind,
                "Tienes una reunion", null, null, null);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderService).create(captor.capture());
        assertNull(captor.getValue().getRecurrentConfig());
    }

    @Test
    void ignoresRecurrenceParams_whenFrequencyNull() {
        LocalDateTime remind = LocalDateTime.of(2026, 1, 2, 10, 0);

        tool.createReminder("tarea", null, remind, "Tienes una tarea",
                null, 3, 7);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderService).create(captor.capture());
        assertNull(captor.getValue().getRecurrentConfig());
    }

    @Test
    void defaultsIntervalAndOccurrences_toNullForServiceNormalization() {
        LocalDateTime remind = LocalDateTime.of(2026, 1, 2, 10, 0);

        tool.createReminder("gimnasio", null, remind,
                "Es hora del gimnasio", Frequency.WEEKLY, null, null);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderService).create(captor.capture());
        RecurrentConfig cfg = captor.getValue().getRecurrentConfig();
        assertEquals(Frequency.WEEKLY, cfg.getFrequencyType());
        assertNull(cfg.getRecurrenceInterval());
        assertNull(cfg.getOccurrences());
    }

    @Test
    void returnsConfirmationMessage() {
        LocalDateTime remind = LocalDateTime.of(2026, 1, 1, 10, 0);

        String result = tool.createReminder("leer", null, remind,
                "Es hora de leer", null, null, null);

        assertTrue(result.contains("leer"));
    }

    @Test
    void rejectsBlankName() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertThrows(IllegalArgumentException.class,
                () -> tool.createReminder(" ", null, t, null, null, null, null));
    }

    @Test
    void rejectsNullRemindDate() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.createReminder("tarea", null, null, null, null, null, null));
    }

    @Test
    void getAllReminders_returnsReadableList() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder r = new Reminder(1L, "medicina", null, t, t, null, null);
        when(reminderService.getAll()).thenReturn(java.util.List.of(r));

        String result = tool.getAllReminders();

        assertTrue(result.contains("medicina"));
        assertTrue(result.contains("2026-01-01T10:00"));
    }

    @Test
    void getAllReminders_emptyReturnsMessage() {
        when(reminderService.getAll()).thenReturn(java.util.List.of());

        String result = tool.getAllReminders();

        assertEquals("No hay recordatorios guardados.", result);
    }

    @Test
    void getCurrentDate_returnsIsoDateTime() {
        String result = tool.getCurrentDate();

        LocalDateTime parsed = LocalDateTime.parse(result);
        assertTrue(parsed.isBefore(LocalDateTime.now().plusMinutes(1)));
    }
}
