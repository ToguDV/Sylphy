package com.togudv.sylphy.service.conversation;

import com.togudv.sylphy.model.MemoryLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemorySummarizerTest {

    @Mock
    ChatClient.Builder builder;
    @Mock
    ChatClient chatClient;
    @Mock
    ChatClient.ChatClientRequestSpec promptSpec;
    @Mock
    ChatClient.CallResponseSpec callSpec;

    @Test
    void summarize_returnsTrimmedModelContent() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("  resumen generado  ");

        MemorySummarizer summarizer = new MemorySummarizer(builder);

        assertEquals("resumen generado", summarizer.summarize(MemoryLevel.WINDOW, "contenido"));
    }

    @Test
    void summarize_returnsNullWhenModelContentIsBlank() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("   ");

        MemorySummarizer summarizer = new MemorySummarizer(builder);

        assertNull(summarizer.summarize(MemoryLevel.DAILY, "contenido"));
    }

    @Test
    void summarize_returnsNullWhenModelFails() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(null);

        MemorySummarizer summarizer = new MemorySummarizer(builder);

        assertNull(summarizer.summarize(MemoryLevel.ANNUAL, "contenido"));
    }
}
