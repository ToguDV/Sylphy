package com.togudv.sylphy.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class AIService {

    private final ChatClient chatClient;

    public AIService(ChatClient.Builder chatClientBuilder, List<AITool> tools) {
        ToolCallbackProvider provider = MethodToolCallbackProvider
                .builder()
                .toolObjects(tools.toArray())
                .build();

        this.chatClient = chatClientBuilder
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(0.8))
                .defaultTools(provider)
                .build();
    }

    public String generate(String input) {
        return chatClient
                .prompt()
                .user(input)
                .call()
                .content();
    }
}
