package com.togudv.sylphy.service;

import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.togudv.sylphy.config.ConversationIdProvider;
import com.togudv.sylphy.service.conversation.JpaChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock
    SystemPromptService systemPromptService;

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
        when(systemPromptService.getEffectivePrompt()).thenReturn("");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

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
        when(systemPromptService.getEffectivePrompt()).thenReturn("");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

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
        when(systemPromptService.getEffectivePrompt()).thenReturn("");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

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
        when(systemPromptService.getEffectivePrompt()).thenReturn("");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        service.generate("ok", "owner-1", "   ");

        verify(promptSpec, never()).system(anyString());
    }

    @Test
    void generate_injectsConfiguredSystemPrompt() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta");
        when(systemPromptService.getEffectivePrompt()).thenReturn("Eres Sylphy, asistente personal.");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        service.generate("hola", "owner-1");

        verify(promptSpec).system("Eres Sylphy, asistente personal.");
    }

    @Test
    void generate_skipsSystemPromptWhenBlank() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta");
        when(systemPromptService.getEffectivePrompt()).thenReturn("   ");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        service.generate("hola", "owner-1");

        verify(promptSpec, never()).system(anyString());
    }

    @Test
    void constructor_registersProvidedToolsAndMemoryAdvisor() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        new AIService(builder, List.of(new StubTool()), chatMemory, conversationIdProvider, systemPromptService, 3, 1);
        verify(builder).defaultTools(any(ToolCallbackProvider.class));
        verify(builder).defaultAdvisors(any(Advisor[].class));
    }

    @Test
    void generate_retriesTransientProviderErrorThenSucceeds() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(systemPromptService.getEffectivePrompt()).thenReturn("");
        when(promptSpec.call()).thenThrow(new OpenAIInvalidDataException("`choices` is not set"))
                .thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        assertEquals("respuesta", service.generate("hola", "owner-1"));
        verify(promptSpec, times(2)).call();
    }

    @Test
    void generate_retriesSdkRetryableError() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(systemPromptService.getEffectivePrompt()).thenReturn("");
        when(promptSpec.call()).thenThrow(new OpenAIRetryableException("intentalo de nuevo"))
                .thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        assertEquals("respuesta", service.generate("hola", "owner-1"));
        verify(promptSpec, times(2)).call();
    }

    @Test
    void generate_retriesOnHttp5xx() {
        UnexpectedStatusCodeException httpError = mock(UnexpectedStatusCodeException.class);
        when(httpError.statusCode()).thenReturn(503);
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(systemPromptService.getEffectivePrompt()).thenReturn("");
        when(promptSpec.call()).thenThrow(httpError)
                .thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        assertEquals("respuesta", service.generate("hola", "owner-1"));
        verify(promptSpec, times(2)).call();
    }

    @Test
    void generate_doesNotRetryHttp4xx() {
        UnexpectedStatusCodeException httpError = mock(UnexpectedStatusCodeException.class);
        when(httpError.statusCode()).thenReturn(400);
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(systemPromptService.getEffectivePrompt()).thenReturn("");
        when(promptSpec.call()).thenThrow(httpError);

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        assertThrows(UnexpectedStatusCodeException.class, () -> service.generate("hola", "owner-1"));
        verify(promptSpec, times(1)).call();
    }

    @Test
    void generate_givesUpAfterMaxAttempts() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(systemPromptService.getEffectivePrompt()).thenReturn("");
        when(promptSpec.call()).thenThrow(new OpenAIInvalidDataException("`choices` is not set"));

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        assertThrows(OpenAIInvalidDataException.class, () -> service.generate("hola", "owner-1"));
        verify(promptSpec, times(3)).call();
    }

    @Test
    void generate_doesNotRetryNonTransientErrors() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(systemPromptService.getEffectivePrompt()).thenReturn("");
        when(promptSpec.call()).thenThrow(new IllegalArgumentException("otra causa"));

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        assertThrows(IllegalArgumentException.class, () -> service.generate("hola", "owner-1"));
        verify(promptSpec, times(1)).call();
    }

    @Test
    void generate_doesNotRetryWhenToolExecutedInFailedAttempt() {
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(systemPromptService.getEffectivePrompt()).thenReturn("");
        when(promptSpec.call()).thenAnswer(invocation -> {
            ToolCallTracker.markToolExecuted();
            throw new OpenAIInvalidDataException("`choices` is not set");
        });

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        assertThrows(OpenAIInvalidDataException.class, () -> service.generate("hola", "owner-1"));
        verify(promptSpec, times(1)).call();
        assertFalse(ToolCallTracker.wasToolExecuted());
    }

    @Test
    void generate_clearsToolTrackerAfterSuccess() {
        ToolCallTracker.markToolExecuted();
        when(builder.defaultOptions(any())).thenReturn(builder);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.defaultTools(any(ToolCallbackProvider.class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user("hola")).thenReturn(promptSpec);
        when(promptSpec.advisors(any(Consumer.class))).thenReturn(promptSpec);
        when(systemPromptService.getEffectivePrompt()).thenReturn("");
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("respuesta");

        AIService service = new AIService(builder, List.of(), chatMemory, conversationIdProvider, systemPromptService, 3, 1);

        service.generate("hola", "owner-1");

        assertFalse(ToolCallTracker.wasToolExecuted());
    }

    @Test
    void trackingWrapper_marksExecutionWhenToolIsCalled() {
        ToolCallTracker.reset();
        ToolCallbackProvider tracked = AIService.trackToolCalls(
                MethodToolCallbackProvider.builder().toolObjects(new StubTool()).build());
        ToolCallback callback = tracked.getToolCallbacks()[0];

        assertEquals("ping", callback.getToolDefinition().name());
        assertFalse(ToolCallTracker.wasToolExecuted());
        assertEquals("\"pong\"", callback.call("{}"));
        assertEquals("\"pong\"", callback.call("{}", new ToolContext(Map.of())));
        assertTrue(ToolCallTracker.wasToolExecuted());
        ToolCallTracker.clear();
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
