package com.togudv.sylphy.service.notification;

import com.togudv.sylphy.config.NotificationDestination;
import com.togudv.sylphy.model.Reminder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramNotificationDispatcherTest {

    @Mock TelegramClient telegramClient;
    @Mock NotificationDestination destination;

    @Test
    void sendsMessageWithNameAndDescription() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        TelegramNotificationDispatcher dispatcher =
                new TelegramNotificationDispatcher(telegramClient, destination);

        Reminder r = new Reminder(
                7L, "Tomar pastilla", "Con el desayuno", LocalDateTime.now(), LocalDateTime.now(), null);
        dispatcher.dispatch(r);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage sent = captor.getValue();
        assertEquals("chat-42", sent.getChatId());
        assertTrue(sent.getText().contains("Tomar pastilla"));
        assertTrue(sent.getText().contains("Con el desayuno"));
    }

    @Test
    void omitsDescriptionWhenBlank() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        TelegramNotificationDispatcher dispatcher =
                new TelegramNotificationDispatcher(telegramClient, destination);

        Reminder r = new Reminder(
                7L, "Tomar pastilla", "   ", LocalDateTime.now(), LocalDateTime.now(), null);
        dispatcher.dispatch(r);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("Recordatorio: Tomar pastilla", captor.getValue().getText());
    }

    @Test
    void wrapsTelegramFailureInDomainException() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(telegramClient.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiException("network down"));
        TelegramNotificationDispatcher dispatcher =
                new TelegramNotificationDispatcher(telegramClient, destination);

        Reminder r = new Reminder(
                7L, "x", null, LocalDateTime.now(), LocalDateTime.now(), null);

        NotificationDeliveryException ex = assertThrows(
                NotificationDeliveryException.class, () -> dispatcher.dispatch(r));
        assertTrue(ex.getMessage().contains("id=7"));
    }
}
