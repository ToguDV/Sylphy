package com.togudv.sylphy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SingleOwnerConversationIdProvider implements ConversationIdProvider {

    private final String conversationId;

    public SingleOwnerConversationIdProvider(@Value("${sylphy.conversation.id:owner-1}") String conversationId) {
        this.conversationId = conversationId == null ? "owner-1" : conversationId;
    }

    @Override
    public String getConversationId() {
        return conversationId;
    }
}
