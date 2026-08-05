package com.togudv.sylphy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.togudv.sylphy.mapper.ReminderMapper;
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
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("removal")
class ReminderControllerTest {

    @Mock
    ReminderService reminderService;

    MockMvc mockMvc;
    ReminderMapper mapper = Mappers.getMapper(ReminderMapper.class);

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ReminderController controller = new ReminderController(reminderService, mapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getAll_returnsList() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(reminderService.getAll()).thenReturn(List.of(
                new Reminder(1L, "medicina", null, t, t, null, null)));

        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("medicina"));
    }

    @Test
    void getById_returnsReminder() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(reminderService.getById(7L)).thenReturn(
                new Reminder(7L, "reunion", "reunion de equipo", t, t,
                        RecurrentConfig.of(Frequency.DAILY, 1, 3), "Tienes reunion"));

        mockMvc.perform(get("/api/reminders/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("reunion"))
                .andExpect(jsonPath("$.recurrentConfig.frequencyType").value("DAILY"))
                .andExpect(jsonPath("$.recurrentConfig.occurrences").value(3));
    }

    @Test
    void getById_returnsProblemDetail404() throws Exception {
        when(reminderService.getById(99L))
                .thenThrow(new NoSuchElementException("Reminder no encontrado: 99"));

        mockMvc.perform(get("/api/reminders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    @Test
    void create_returns201AndSavesEntity() throws Exception {
        String body = """
                {
                  "name": "medicina",
                  "description": "tomar pastilla",
                  "nextDate": "2027-02-01T09:00:00",
                  "recurrentConfig": {"frequencyType": "DAILY", "recurrenceInterval": 2, "occurrences": 5},
                  "notificationMessage": "Es hora de tu pastilla"
                }
                """;

        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("medicina"));

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderService).create(captor.capture());
        Reminder saved = captor.getValue();
        assertNull(saved.getId());
        assertNotNull(saved.getCreationDate());
        assertEquals(Frequency.DAILY, saved.getRecurrentConfig().getFrequencyType());
    }

    @Test
    void create_rejectsBlankNameWithProblemDetail400() throws Exception {
        String body = """
                {
                  "name": " ",
                  "nextDate": "2027-02-01T09:00:00"
                }
                """;

        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.properties.errors[0]").exists());
    }

    @Test
    void create_rejectsPastNextDateWithProblemDetail400() throws Exception {
        String body = """
                {
                  "name": "tarea",
                  "nextDate": "2020-01-01T09:00:00"
                }
                """;

        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void create_rejectsUnreadableBodyWithProblemDetail400() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Solicitud invalida"));
    }

    @Test
    void update_returnsUpdatedReminder() throws Exception {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(reminderService.updateById(eq(5L), any(Reminder.class))).thenReturn(
                new Reminder(5L, "gimnasio", null, t, t,
                        RecurrentConfig.of(Frequency.WEEKLY, 1, null), null));
        String body = """
                {
                  "name": "gimnasio",
                  "nextDate": "2027-03-01T09:00:00",
                  "recurrentConfig": {"frequencyType": "WEEKLY"}
                }
                """;

        mockMvc.perform(put("/api/reminders/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("gimnasio"));
    }

    @Test
    void update_returnsProblemDetail404() throws Exception {
        when(reminderService.updateById(eq(99L), any(Reminder.class)))
                .thenThrow(new NoSuchElementException("Reminder no encontrado: 99"));
        String body = """
                {
                  "name": "gimnasio",
                  "nextDate": "2027-03-01T09:00:00"
                }
                """;

        mockMvc.perform(put("/api/reminders/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/reminders/3"))
                .andExpect(status().isNoContent());

        verify(reminderService).deleteById(3L);
    }

    @Test
    void delete_missingIdReturnsProblemDetail404() throws Exception {
        doThrow(new NoSuchElementException("Reminder no encontrado: 99"))
                .when(reminderService).deleteById(99L);

        mockMvc.perform(delete("/api/reminders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    @Test
    void illegalArgumentFromService_becomesProblemDetail400() throws Exception {
        when(reminderService.updateById(eq(5L), any(Reminder.class)))
                .thenThrow(new IllegalArgumentException("RecurrentConfig.frequencyType es obligatorio en un recordatorio recurrente"));
        String body = """
                {
                  "name": "gimnasio",
                  "nextDate": "2027-03-01T09:00:00",
                  "recurrentConfig": {"recurrenceInterval": 1}
                }
                """;

        mockMvc.perform(put("/api/reminders/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(
                        "RecurrentConfig.frequencyType es obligatorio en un recordatorio recurrente"));
    }
}
