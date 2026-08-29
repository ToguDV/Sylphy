package com.togudv.sylphy.mapper;

import com.togudv.sylphy.dto.CreateReminderDTO;
import com.togudv.sylphy.dto.RecurrentConfigDTO;
import com.togudv.sylphy.dto.ReminderDTO;
import com.togudv.sylphy.dto.UpdateReminderDTO;
import com.togudv.sylphy.model.RecurrentConfig;
import com.togudv.sylphy.model.Reminder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReminderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creationDate", expression = "java(java.time.LocalDateTime.now())")
    Reminder toEntity(CreateReminderDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    Reminder toEntity(UpdateReminderDTO dto);

    ReminderDTO toDto(Reminder reminder);

    RecurrentConfigDTO toRecurrentConfigDto(RecurrentConfig config);
}
