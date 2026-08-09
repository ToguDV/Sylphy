package com.togudv.sylphy.service.notification;

import com.togudv.sylphy.config.ConversationIdProvider;
import com.togudv.sylphy.config.NotificationDestination;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.service.ReminderMessageComposer;
import com.togudv.sylphy.service.conversation.JpaChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramNotificationDispatcherTest {

    @Mock TelegramClient telegramClient;
    @Mock NotificationDestination destination;
    @Mock ReminderMessageComposer composer;
    @Mock JpaChatMemory chatMemory;
    @Mock ConversationIdProvider conversationIdProvider;

    private TelegramNotificationDispatcher dispatcher() {
        return new TelegramNotificationDispatcher(
                telegramClient, destination, composer, chatMemory, conversationIdProvider);
    }

    private static Reminder reminder(String name, String description, String notificationMessage) {
        return new Reminder(7L, name, description,
                LocalDateTime.now(), LocalDateTime.now(), null, notificationMessage);
    }

    @Test
    void usesComposedMessageWhenComposerSucceeds() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(composer.compose(any(Reminder.class)))
                .thenReturn("Ey, no te olvides de la pastilla a las 10");
        TelegramNotificationDispatcher d = dispatcher();

        d.dispatch(reminder("Tomar pastilla", "Con el desayuno", "fallback viejo"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("chat-42", captor.getValue().getChatId());
        assertEquals("Ey, no te olvides de la pastilla a las 10", captor.getValue().getText());
    }

    @Test
    void fallsBackToPersistedMessageWhenComposerThrows() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(composer.compose(any(Reminder.class)))
                .thenThrow(new RuntimeException("Mistral caido"));
        TelegramNotificationDispatcher d = dispatcher();

        d.dispatch(reminder("Tomar pastilla", "Con el desayuno",
                "Mensaje persistido al crear el reminder"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("Mensaje persistido al crear el reminder", captor.getValue().getText());
    }

    @Test
    void fallsBackToPersistedMessageWhenComposerReturnsBlank() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(composer.compose(any(Reminder.class))).thenReturn("   ");
        TelegramNotificationDispatcher d = dispatcher();

        d.dispatch(reminder("Tomar pastilla", "Con el desayuno", "persistido"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("persistido", captor.getValue().getText());
    }

    @Test
    void fallsBackToDefaultFormatWhenComposerFailsAndPersistedBlank() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(composer.compose(any(Reminder.class)))
                .thenThrow(new RuntimeException("Mistral caido"));
        TelegramNotificationDispatcher d = dispatcher();

        d.dispatch(reminder("Tomar pastilla", "Con el desayuno", null));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        String text = captor.getValue().getText();
        assertTrue(text.contains("Tomar pastilla"));
        assertTrue(text.contains("Con el desayuno"));
    }

    @Test
    void defaultFormatWhenAllFallbacksBlankAndDescriptionBlank() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(composer.compose(any(Reminder.class)))
                .thenThrow(new RuntimeException("Mistral caido"));
        TelegramNotificationDispatcher d = dispatcher();

        d.dispatch(reminder("Tomar pastilla", "   ", ""));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("Recordatorio: Tomar pastilla", captor.getValue().getText());
    }

    @Test
    void wrapsTelegramFailureInDomainException() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(telegramClient.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiException("network down"));
        TelegramNotificationDispatcher d = dispatcher();

        Reminder r = new Reminder(
                7L, "x", null, LocalDateTime.now(), LocalDateTime.now(), null, null);

        NotificationDeliveryException ex = assertThrows(
                NotificationDeliveryException.class, () -> d.dispatch(r));
        assertTrue(ex.getMessage().contains("id=7"));
    }

    @Test
    void recordsDeliveredTextInHistoryAfterSuccess() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        when(composer.compose(any(Reminder.class)))
                .thenReturn("Ey, no te olvides de la pastilla a las 10");
        TelegramNotificationDispatcher d = dispatcher();

        d.dispatch(reminder("Tomar pastilla", "Con el desayuno", null));

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatMemory).add(eq("owner-1"), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("Ey, no te olvides de la pastilla a las 10",
                captor.getValue().get(0).getText());
        assertEquals(AssistantMessage.class, captor.getValue().get(0).getClass());
    }

    @Test
    void recordsPersistedFallbackTextInHistory() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        when(composer.compose(any(Reminder.class)))
                .thenThrow(new RuntimeException("Mistral caido"));
        TelegramNotificationDispatcher d = dispatcher();

        d.dispatch(reminder("Tomar pastilla", "Con el desayuno", "texto persistido"));

        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatMemory).add(eq("owner-1"), captor.capture());
        assertEquals("texto persistido", captor.getValue().get(0).getText());
    }

    @Test
    void doesNotRecordInHistoryWhenTelegramFails() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(telegramClient.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiException("network down"));
        TelegramNotificationDispatcher d = dispatcher();

        Reminder r = new Reminder(
                7L, "x", null, LocalDateTime.now(), LocalDateTime.now(), null, null);

        assertThrows(NotificationDeliveryException.class, () -> d.dispatch(r));
        verify(chatMemory, never()).add(anyString(), anyList());
    }

    @Test
    void dispatchSucceedsWhenHistoryWriteFails() throws TelegramApiException {
        when(destination.value()).thenReturn("chat-42");
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        when(composer.compose(any(Reminder.class))).thenReturn("texto");
        doThrow(new RuntimeException("bd caida"))
                .when(chatMemory).add(anyString(), anyList());
        TelegramNotificationDispatcher d = dispatcher();

        d.dispatch(reminder("Tomar pastilla", null, null));

        verify(telegramClient).execute(any(SendMessage.class));
    }
}
