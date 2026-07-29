package com.togudv.sylphy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class TelegramNotificationDestination implements NotificationDestination {

    private final String chatId;

    public TelegramNotificationDestination(
            @Value("${telegram.notification.chat-id:}") String chatId) {
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalStateException(
                    "telegram.notification.chat-id is not set; "
                  + "configure TELEGRAM_NOTIFICATION_CHAT_ID before starting the app");
        }
        this.chatId = chatId;
    }

    @Override
    public String value() {
        return chatId;
    }

    @Override
    public Channel channel() {
        return Channel.TELEGRAM;
    }
}
