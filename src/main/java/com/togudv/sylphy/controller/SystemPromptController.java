package com.togudv.sylphy.controller;

import com.togudv.sylphy.dto.SystemPromptDTO;
import com.togudv.sylphy.dto.SystemPromptUpdateDTO;
import com.togudv.sylphy.model.SystemPrompt;
import com.togudv.sylphy.service.SystemPromptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/system-prompt")
public class SystemPromptController {

    private final SystemPromptService systemPromptService;

    public SystemPromptController(SystemPromptService systemPromptService) {
        this.systemPromptService = systemPromptService;
    }

    @GetMapping
    public SystemPromptDTO get() {
        Optional<SystemPrompt> stored = systemPromptService.getStored();
        if (stored.isPresent()) {
            return new SystemPromptDTO(stored.get().getContent(), stored.get().getUpdatedAt());
        }
        return new SystemPromptDTO(systemPromptService.getEffectivePrompt(), null);
    }

    @PutMapping
    public SystemPromptDTO update(@Valid @RequestBody SystemPromptUpdateDTO dto) {
        SystemPrompt prompt = systemPromptService.update(dto.content());
        return new SystemPromptDTO(prompt.getContent(), prompt.getUpdatedAt());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        systemPromptService.reset();
    }
}
