package com.togudv.sylphy.mapper;

import com.togudv.sylphy.dto.CreateReminderDTO;
import com.togudv.sylphy.dto.RecurrentConfigDTO;
import com.togudv.sylphy.dto.ReminderDTO;
import com.togudv.sylphy.dto.UpdateReminderDTO;
import com.togudv.sylphy.model.Frequency;
import com.togudv.sylphy.model.RecurrentConfig;
import com.togudv.sylphy.model.Reminder;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReminderMapperTest {

    private final ReminderMapper mapper = Mappers.getMapper(ReminderMapper.class);

    @Test
    void toEntityFromCreate_setsCreationDateAndIgnoresId() {
        LocalDateTime next = LocalDateTime.of(2026, 2, 1, 9, 0);
        CreateReminderDTO dto = new CreateReminderDTO(
                "medicina", "tomar pastilla", next,
                new RecurrentConfigDTO(Frequency.DAILY, 2, 5), "Es hora de tu pastilla");

        Reminder entity = mapper.toEntity(dto);

        assertNull(entity.getId());
        assertNotNull(entity.getCreationDate());
        assertEquals("medicina", entity.getName());
        assertEquals(next, entity.getNextDate());
        assertEquals(Frequency.DAILY, entity.getRecurrentConfig().getFrequencyType());
        assertEquals(2, entity.getRecurrentConfig().getRecurrenceInterval());
        assertEquals(5, entity.getRecurrentConfig().getOccurrences());
        assertEquals("Es hora de tu pastilla", entity.getNotificationMessage());
    }

    @Test
    void toEntityFromCreate_mapsNullRecurrentConfig() {
        LocalDateTime next = LocalDateTime.of(2026, 2, 1, 9, 0);
        CreateReminderDTO dto = new CreateReminderDTO("reunion", null, next, null, null);

        Reminder entity = mapper.toEntity(dto);

        assertNull(entity.getRecurrentConfig());
    }

    @Test
    void toEntityFromUpdate_keepsCreationDateNull() {
        LocalDateTime next = LocalDateTime.of(2026, 3, 1, 9, 0);
        UpdateReminderDTO dto = new UpdateReminderDTO(
                "gimnasio", null, next,
                new RecurrentConfigDTO(Frequency.WEEKLY, 1, null), null);

        Reminder entity = mapper.toEntity(dto);

        assertNull(entity.getId());
        assertNull(entity.getCreationDate());
        assertEquals("gimnasio", entity.getName());
        assertEquals(Frequency.WEEKLY, entity.getRecurrentConfig().getFrequencyType());
    }

    @Test
    void toDto_mapsAllFields() {
        LocalDateTime creation = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime next = LocalDateTime.of(2026, 1, 2, 10, 0);
        Reminder entity = new Reminder(42L, "medicina", "tomar pastilla", creation, next,
                RecurrentConfig.of(Frequency.DAILY, 2, 5), "Es hora");

        ReminderDTO dto = mapper.toDto(entity);

        assertEquals(42L, dto.id());
        assertEquals("medicina", dto.name());
        assertEquals(creation, dto.creationDate());
        assertEquals(next, dto.nextDate());
        assertEquals(Frequency.DAILY, dto.recurrentConfig().frequencyType());
        assertEquals(2, dto.recurrentConfig().recurrenceInterval());
        assertEquals(5, dto.recurrentConfig().occurrences());
        assertEquals("Es hora", dto.notificationMessage());
    }

    @Test
    void toDto_mapsNullRecurrentConfig() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reminder entity = new Reminder(1L, "reunion", null, t, t, null, null);

        ReminderDTO dto = mapper.toDto(entity);

        assertNull(dto.recurrentConfig());
    }
}
