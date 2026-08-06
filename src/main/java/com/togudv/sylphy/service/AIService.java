package com.togudv.sylphy.service;

import com.togudv.sylphy.config.ConversationIdProvider;
import com.togudv.sylphy.service.conversation.JpaChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class AIService {

    private final ChatClient chatClient;
    private final ConversationIdProvider conversationIdProvider;

    public AIService(ChatClient.Builder chatClientBuilder,
                     List<AITool> tools,
                     JpaChatMemory chatMemory,
                     ConversationIdProvider conversationIdProvider) {
        ToolCallbackProvider provider = MethodToolCallbackProvider
                .builder()
                .toolObjects(tools.toArray())
                .build();

        this.chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(0.8))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(provider)
                .build();
        this.conversationIdProvider = conversationIdProvider;
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
        ChatClient.ChatClientRequestSpec spec = chatClient
                .prompt()
                .user(input)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));
        if (replyToText != null && !replyToText.isBlank()) {
            spec = spec.system(REPLY_CONTEXT_SYSTEM_PROMPT.formatted(replyToText));
        }
        return spec.call().content();
    }
}
