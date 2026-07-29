package com.togudv.sylphy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TelegramNotificationDestinationTest {

    @Test
    void buildsBeanWhenChatIdIsSet() {
        TelegramNotificationDestination destination =
                new TelegramNotificationDestination("123456789");

        assertEquals("123456789", destination.value());
        assertEquals(NotificationDestination.Channel.TELEGRAM, destination.channel());
    }

    @Test
    void rejectsNullChatId() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new TelegramNotificationDestination(null));

        assertEquals(
                "telegram.notification.chat-id is not set; "
              + "configure TELEGRAM_NOTIFICATION_CHAT_ID before starting the app",
                exception.getMessage());
    }

    @Test
    void rejectsEmptyChatId() {
        assertThrows(
                IllegalStateException.class,
                () -> new TelegramNotificationDestination(""));
    }

    @Test
    void rejectsBlankChatId() {
        assertThrows(
                IllegalStateException.class,
                () -> new TelegramNotificationDestination("   "));
    }
}
