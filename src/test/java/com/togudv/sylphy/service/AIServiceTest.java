package com.togudv.sylphy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIServiceTest {

    @Mock
    ChatClient.Builder builder;
    @Mock
    ChatClient chatClient;
    @Mock
    ChatClient.ChatClientRequestSpec promptSpec;
    @Mock
    ChatClient.CallResponseSpec callSpec;

    @Test
    void generate_returnsModelContent() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta del modelo");

        AIService service = new AIService(builder, List.of());

        String result = service.generate("hola");

        assertEquals("respuesta del modelo", result);
        verify(promptSpec).user("hola");
    }

    @Test
    void constructor_registersProvidedTools() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        new AIService(builder, List.of(new StubTool()));

        verify(builder).defaultTools(any(ToolCallbackProvider.class));
    }

    static class StubTool implements AITool {

        @Override
        public String getName() {
            return "stub";
        }

        @Tool(description = "herramienta stub")
        public String ping() {
            return "pong";
        }
    }
}
