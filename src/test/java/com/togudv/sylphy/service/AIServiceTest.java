package com.togudv.sylphy.service;

import com.togudv.sylphy.config.ConversationIdProvider;
import com.togudv.sylphy.service.conversation.JpaChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
    @Mock
    JpaChatMemory chatMemory;
    @Mock
    ConversationIdProvider conversationIdProvider;

    @Test
    void generate_returnsModelContent() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta del modelo");
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider);

        String result = service.generate("hola");

        assertEquals("respuesta del modelo", result);
        verify(promptSpec).user("hola");
    }

    @Test
    void generate_withConversationIdPassesMemoryParam() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider);

        service.generate("hola", "owner-1");

        verify(promptSpec).advisors(any(Consumer.class));
    }

    @Test
    void generate_withReplyToTextAddsSystemContext() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("ok")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider);

        service.generate("ok", "owner-1", "mensaje previo");

        verify(promptSpec).system("El usuario esta respondiendo a tu mensaje anterior: «mensaje previo». "
                + "Tenlo en cuenta al interpretar la peticion.");
    }

    @Test
    void generate_skipsSystemContextWhenReplyIsBlank() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("ok")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider);

        service.generate("ok", "owner-1", "   ");

        verify(promptSpec, never()).system(anyString());
    }

    @Test
    void constructor_registersProvidedToolsAndMemoryAdvisor() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        new AIService(builder, List.of(new StubTool()), chatMemory, conversationIdProvider);

        verify(builder).defaultTools(any(ToolCallbackProvider.class));
        verify(builder).defaultAdvisors(any(Advisor[].class));
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
