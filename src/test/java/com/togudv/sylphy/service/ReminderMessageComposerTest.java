package com.togudv.sylphy.service;

import com.togudv.sylphy.config.ConversationIdProvider;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.service.conversation.JpaChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderMessageComposerTest {

    @Mock ChatClient.Builder builder;
    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec promptSpec;
    @Mock ChatClient.CallResponseSpec callSpec;
    @Mock JpaChatMemory chatMemory;
    @Mock ConversationIdProvider conversationIdProvider;
    @Mock SystemPromptService systemPromptService;

    private ReminderMessageComposer composer() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.messages(anyList())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Mensaje de prueba");
        return new ReminderMessageComposer(builder, chatMemory, conversationIdProvider, systemPromptService);
    }

    private static Reminder reminder() {
        return new Reminder(1L, "Tomar pastilla", "Con el desayuno",
                LocalDateTime.of(2026, 8, 5, 9, 0), LocalDateTime.of(2026, 8, 5, 10, 0), null, null);
    }

    @Test
    void compose_injectsConfiguredSystemPrompt() {
        when(systemPromptService.getEffectivePrompt()).thenReturn("Eres Sylphy, paisa juguetona.");
        ReminderMessageComposer c = composer();

        c.compose(reminder());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(promptSpec).system(captor.capture());
        assertEquals("Eres Sylphy, paisa juguetona.", captor.getValue());
    }

    @Test
    void compose_fallsBackToDefaultPromptWhenEffectiveBlank() {
        when(systemPromptService.getEffectivePrompt()).thenReturn("   ");
        ReminderMessageComposer c = composer();

        c.compose(reminder());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(promptSpec).system(captor.capture());
        assertTrue(captor.getValue().contains("Eres Sylphy, un asistente personal"));
    }

    @Test
    void compose_resolvesConversationIdFromProvider() {
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        ReminderMessageComposer c = composer();

        c.compose(reminder());

        verify(chatMemory).get("owner-1");
    }

    @Test
    void compose_injectsMemoryContext() {
        UserMessage memoryMessage = new UserMessage("recuerdo del historial");
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        when(chatMemory.get("owner-1")).thenReturn(List.of(memoryMessage));
        ReminderMessageComposer c = composer();

        c.compose(reminder());

        verify(promptSpec).messages(List.of(memoryMessage));
    }

    @Test
    void compose_buildsUserPromptFromReminderData() {
        ReminderMessageComposer c = composer();

        c.compose(reminder());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(promptSpec).user(captor.capture());
        assertTrue(captor.getValue().contains("Tomar pastilla"));
        assertTrue(captor.getValue().contains("Con el desayuno"));
    }

    @Test
    void compose_returnsModelContent() {
        ReminderMessageComposer c = composer();

        assertEquals("Mensaje de prueba", c.compose(reminder()));
    }
}
