package com.togudv.sylphy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.togudv.sylphy.model.SystemPrompt;
import com.togudv.sylphy.service.SystemPromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SystemPromptControllerTest {

    @Mock
    SystemPromptService systemPromptService;

    MockMvc mockMvc;

    @BeforeEach
    @SuppressWarnings("removal") // MappingJackson2HttpMessageConverter(ObjectMapper) esta marcado para removal en Spring 7; el setup standalone lo requiere.
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        SystemPromptController controller = new SystemPromptController(systemPromptService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void get_returnsStoredPromptWithTimestamp() throws Exception {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 6, 12, 0);
        when(systemPromptService.getStored()).thenReturn(Optional.of(
                new SystemPrompt(SystemPrompt.FIXED_ID, "prompt configurado", updatedAt)));

        mockMvc.perform(get("/api/system-prompt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("prompt configurado"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-06T12:00:00"));
    }

    @Test
    void get_returnsDefaultWithNullTimestampWhenNothingStored() throws Exception {
        when(systemPromptService.getStored()).thenReturn(Optional.empty());
        when(systemPromptService.getEffectivePrompt()).thenReturn("prompt por defecto");

        mockMvc.perform(get("/api/system-prompt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("prompt por defecto"))
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void put_updatesAndReturnsPrompt() throws Exception {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 6, 13, 0);
        when(systemPromptService.update("nuevo prompt")).thenReturn(
                new SystemPrompt(SystemPrompt.FIXED_ID, "nuevo prompt", updatedAt));
        String body = """
                {"content": "nuevo prompt"}
                """;

        mockMvc.perform(put("/api/system-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("nuevo prompt"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-06T13:00:00"));

        verify(systemPromptService).update("nuevo prompt");
    }

    @Test
    void put_rejectsBlankContentWithProblemDetail400() throws Exception {
        String body = """
                {"content": "   "}
                """;

        mockMvc.perform(put("/api/system-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.properties.errors[0]").exists());
    }

    @Test
    void put_rejectsMissingContentWithProblemDetail400() throws Exception {
        String body = "{}";

        mockMvc.perform(put("/api/system-prompt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void delete_resetsToDefault() throws Exception {
        mockMvc.perform(delete("/api/system-prompt"))
                .andExpect(status().isNoContent());

        verify(systemPromptService).reset();
    }
}
