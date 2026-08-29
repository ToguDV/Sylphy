package com.togudv.sylphy.service;

import com.togudv.sylphy.model.SystemPrompt;
import com.togudv.sylphy.repository.SystemPromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemPromptServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    SystemPromptRepository repository;

    SystemPromptService service;

    @BeforeEach
    void setUp() throws IOException {
        Path promptFile = tempDir.resolve("system-prompt.txt");
        Files.writeString(promptFile, "prompt por defecto");
        service = new SystemPromptService(repository, promptFile.toString());
    }

    @Test
    void getEffectivePrompt_returnsStoredWhenPresent() {
        SystemPrompt stored = new SystemPrompt(SystemPrompt.FIXED_ID, "prompt configurado",
                LocalDateTime.of(2026, 8, 6, 12, 0));
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.of(stored));

        assertEquals("prompt configurado", service.getEffectivePrompt());
    }

    @Test
    void getEffectivePrompt_fallsBackToFileDefaultWhenNoRow() {
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.empty());

        assertEquals("prompt por defecto", service.getEffectivePrompt());
    }

    @Test
    void getEffectivePrompt_fallsBackToFileDefaultWhenStoredBlank() {
        SystemPrompt stored = new SystemPrompt(SystemPrompt.FIXED_ID, "   ",
                LocalDateTime.of(2026, 8, 6, 12, 0));
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.of(stored));

        assertEquals("prompt por defecto", service.getEffectivePrompt());
    }

    @Test
    void getEffectivePrompt_returnsEmptyWhenFileMissing() {
        service = new SystemPromptService(repository, tempDir.resolve("no-existe.txt").toString());
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.empty());

        assertEquals("", service.getEffectivePrompt());
    }

    @Test
    void getEffectivePrompt_returnsEmptyWhenFileBlank() throws IOException {
        Files.writeString(tempDir.resolve("system-prompt.txt"), "   \n");
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.empty());

        assertEquals("", service.getEffectivePrompt());
    }

    @Test
    void getEffectivePrompt_readsFileFreshAfterEdit() throws IOException {
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.empty());

        Files.writeString(tempDir.resolve("system-prompt.txt"), "prompt editado");

        assertEquals("prompt editado", service.getEffectivePrompt());
    }

    @Test
    void getStored_returnsEmptyWhenNoRow() {
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.empty());

        assertFalse(service.getStored().isPresent());
    }

    @Test
    void update_createsRowWithFixedIdAndTimestampWhenMissing() {
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.empty());
        when(repository.save(any(SystemPrompt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SystemPrompt result = service.update("nuevo prompt");

        assertEquals(SystemPrompt.FIXED_ID, result.getId());
        assertEquals("nuevo prompt", result.getContent());
        assertNotNull(result.getUpdatedAt());
        verify(repository).save(result);
    }

    @Test
    void update_replacesContentAndTimestampOfExistingRow() {
        SystemPrompt stored = new SystemPrompt(SystemPrompt.FIXED_ID, "viejo",
                LocalDateTime.of(2026, 8, 1, 10, 0));
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        SystemPrompt result = service.update("nuevo prompt");

        assertEquals("nuevo prompt", result.getContent());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void reset_deletesRowWhenPresent() {
        SystemPrompt stored = new SystemPrompt(SystemPrompt.FIXED_ID, "viejo",
                LocalDateTime.of(2026, 8, 1, 10, 0));
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.of(stored));

        service.reset();

        verify(repository).delete(stored);
    }

    @Test
    void reset_doesNothingWhenNoRow() {
        when(repository.findById(SystemPrompt.FIXED_ID)).thenReturn(Optional.empty());

        service.reset();

        verify(repository, never()).delete(any(SystemPrompt.class));
    }
}
