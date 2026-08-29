package com.togudv.sylphy.controller;

import com.togudv.sylphy.dto.CreateReminderDTO;
import com.togudv.sylphy.dto.ReminderDTO;
import com.togudv.sylphy.dto.UpdateReminderDTO;
import com.togudv.sylphy.mapper.ReminderMapper;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.service.ReminderService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminderService;
    private final ReminderMapper mapper;

    @SuppressFBWarnings(
            value = "EI2",
            justification = "ReminderService y ReminderMapper son beans singleton gestionados por Spring; la referencia es estable por contrato del contenedor.")
    public ReminderController(ReminderService reminderService, ReminderMapper mapper) {
        this.reminderService = reminderService;
        this.mapper = mapper;
    }

    @GetMapping
    public List<ReminderDTO> getAll() {
        return reminderService.getAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ReminderDTO getById(@PathVariable Long id) {
        return mapper.toDto(reminderService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderDTO create(@Valid @RequestBody CreateReminderDTO dto) {
        Reminder reminder = mapper.toEntity(dto);
        reminderService.create(reminder);
        return mapper.toDto(reminder);
    }

    @PutMapping("/{id}")
    public ReminderDTO update(@PathVariable Long id, @Valid @RequestBody UpdateReminderDTO dto) {
        Reminder updated = reminderService.updateById(id, mapper.toEntity(dto));
        return mapper.toDto(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        reminderService.deleteById(id);
    }
}
