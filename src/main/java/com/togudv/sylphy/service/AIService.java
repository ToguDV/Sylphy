package com.togudv.sylphy.service;

import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.togudv.sylphy.config.ConversationIdProvider;
import com.togudv.sylphy.service.conversation.JpaChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;


@Slf4j
@Service
public class AIService {

    private final ChatClient chatClient;
    private final ConversationIdProvider conversationIdProvider;
    private final SystemPromptService systemPromptService;
    private final int maxAttempts;
    private final long retryDelayMs;

    public AIService(ChatClient.Builder chatClientBuilder,
                     List<AITool> tools,
                     JpaChatMemory chatMemory,
                     ConversationIdProvider conversationIdProvider,
                     SystemPromptService systemPromptService,
                     @Value("${sylphy.ai.retry.max-attempts:3}") int maxAttempts,
                     @Value("${sylphy.ai.retry.delay-ms:2000}") long retryDelayMs) {
        ToolCallbackProvider baseProvider = MethodToolCallbackProvider
                .builder()
                .toolObjects(tools.toArray())
                .build();
        ToolCallbackProvider provider = trackToolCalls(baseProvider);

        this.chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(0.8)
                        .reasoningEffort("max"))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(provider)
                .build();
        this.conversationIdProvider = conversationIdProvider;
        this.systemPromptService = systemPromptService;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    /**
     * Envuelve cada callback para marcar en ToolCallTracker cuando una
     * herramienta se ejecuta realmente.
     */
    static ToolCallbackProvider trackToolCalls(ToolCallbackProvider base) {
        return () -> Arrays.stream(base.getToolCallbacks())
                .map(TrackingToolCallback::new)
                .toArray(ToolCallback[]::new);
    }

    private static final class TrackingToolCallback implements ToolCallback {

        private final ToolCallback delegate;

        TrackingToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String toolInput) {
            ToolCallTracker.markToolExecuted();
            return delegate.call(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            ToolCallTracker.markToolExecuted();
            return delegate.call(toolInput, toolContext);
        }
    }

    private static final String REPLY_CONTEXT_SYSTEM_PROMPT =
            "El usuario esta respondiendo a tu mensaje anterior: «%s». Tenlo en cuenta al interpretar la peticion.";

    public String generate(String input) {
        return generate(input, conversationIdProvider.getConversationId(), null);
    }

    public String generate(String input, String conversationId) {
        return generate(input, conversationId, null);
    }

    public String generate(String input, String conversationId, String replyToText) {
        try {
            return callWithRetry(input, conversationId, replyToText);
        } finally {
            ToolCallTracker.clear();
        }
    }

    private String callWithRetry(String input, String conversationId, String replyToText) {
        for (int attempt = 1; ; attempt++) {
            ToolCallTracker.reset();
            try {
                return buildSpec(input, conversationId, replyToText).call().content();
            } catch (RuntimeException e) {
                boolean toolAlreadyExecuted = ToolCallTracker.wasToolExecuted();
                if (attempt >= maxAttempts || !isTransientProviderError(e) || toolAlreadyExecuted) {
                    throw e;
                }
                log.warn("IA: error transitorio del proveedor (intento {}/{}): {}",
                        attempt, maxAttempts, e.getMessage());
                sleep();
            }
        }
    }

    private ChatClient.ChatClientRequestSpec buildSpec(String input, String conversationId, String replyToText) {
        ChatClient.ChatClientRequestSpec spec = chatClient
                .prompt()
                .user(input)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        String systemPrompt = systemPromptService.getEffectivePrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            spec = spec.system(systemPrompt);
        }
        if (replyToText != null && !replyToText.isBlank()) {
            spec = spec.system(REPLY_CONTEXT_SYSTEM_PROMPT.formatted(replyToText));
        }
        return spec;
    }

    /**
     * Errores transitorios del proveedor: respuestas malformadas sin choices
     * (OpenAIInvalidDataException), errores marcados reintentables por el SDK
     * y HTTP 429 / 5xx. Todo lo demas se propaga sin reintento.
     */
    private static boolean isTransientProviderError(RuntimeException e) {
        if (e instanceof OpenAIInvalidDataException || e instanceof OpenAIRetryableException) {
            return true;
        }
        return e instanceof UnexpectedStatusCodeException http
                && (http.statusCode() == 429 || http.statusCode() >= 500);
    }

    private void sleep() {
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hilo interrumpido durante el reintento de la IA", ie);
        }
    }
}
