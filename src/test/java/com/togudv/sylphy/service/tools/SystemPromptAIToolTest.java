package com.togudv.sylphy.service.tools;

import com.togudv.sylphy.model.SystemPrompt;
import com.togudv.sylphy.service.SystemPromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemPromptAIToolTest {

    @Mock
    SystemPromptService systemPromptService;

    SystemPromptAITool tool;

    @BeforeEach
    void setUp() {
        tool = new SystemPromptAITool(systemPromptService);
    }

    @Test
    void getName_returnsSystemPrompt() {
        assertEquals("system-prompt", tool.getName());
    }

    @Test
    void getSystemPrompt_returnsStoredContentWithConfiguredSource() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 6, 12, 0);
        when(systemPromptService.getStored()).thenReturn(Optional.of(
                new SystemPrompt(SystemPrompt.FIXED_ID, "prompt configurado", updatedAt)));
        when(systemPromptService.getEffectivePrompt()).thenReturn("prompt configurado");

        String result = tool.getSystemPrompt();

        assertTrue(result.contains("fuente: configurado"));
        assertTrue(result.contains("prompt configurado"));
    }

    @Test
    void getSystemPrompt_returnsDefaultSourceWhenNothingStored() {
        when(systemPromptService.getStored()).thenReturn(Optional.empty());
        when(systemPromptService.getEffectivePrompt()).thenReturn("prompt por defecto");

        String result = tool.getSystemPrompt();

        assertTrue(result.contains("fuente: por defecto"));
        assertTrue(result.contains("prompt por defecto"));
    }

    @Test
    void getSystemPrompt_returnsNoPromptMessageWhenBlank() {
        when(systemPromptService.getStored()).thenReturn(Optional.empty());
        when(systemPromptService.getEffectivePrompt()).thenReturn("   ");

        String result = tool.getSystemPrompt();

        assertEquals("No hay system prompt configurado.", result);
    }

    @Test
    void updateSystemPrompt_updatesAndConfirms() {
        when(systemPromptService.update("nueva personalidad"))
                .thenReturn(new SystemPrompt(SystemPrompt.FIXED_ID, "nueva personalidad", LocalDateTime.now()));

        String result = tool.updateSystemPrompt("nueva personalidad");

        verify(systemPromptService).update("nueva personalidad");
        assertTrue(result.contains("actualizado"));
    }

    @Test
    void updateSystemPrompt_rejectsBlankContent() {
        assertThrows(IllegalArgumentException.class, () -> tool.updateSystemPrompt("   "));
    }

    @Test
    void updateSystemPrompt_rejectsTooLongContent() {
        String tooLong = "a".repeat(10001);

        assertThrows(IllegalArgumentException.class, () -> tool.updateSystemPrompt(tooLong));
    }

    @Test
    void resetSystemPrompt_resetsAndConfirms() {
        String result = tool.resetSystemPrompt();

        verify(systemPromptService).reset();
        assertTrue(result.contains("restaurado"));
    }
}
