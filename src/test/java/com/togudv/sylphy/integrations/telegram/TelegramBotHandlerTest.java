package com.togudv.sylphy.integrations.telegram;

import com.togudv.sylphy.service.AIService;
import com.togudv.sylphy.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramBotHandlerTest {

    @Mock
    TelegramClient telegramClient;
    @Mock
    ReminderService reminderService;
    @Mock
    AIService aiService;

    TelegramBotHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelegramBotHandler("token", telegramClient, reminderService, aiService);
    }

    @Test
    void getBotToken_returnsConfiguredToken() {
        assertEquals("token", handler.getBotToken());
    }

    @Test
    void consume_sendsReplyForTextMessage() throws TelegramApiException {
        Update update = textUpdate(42L, "hola");
        when(aiService.generate("hola")).thenReturn("respuesta");

        handler.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("respuesta", captor.getValue().getText());
        assertEquals("42", captor.getValue().getChatId());
    }

    @Test
    void consume_ignoresUpdateWithoutMessage() throws TelegramApiException {
        handler.consume(new Update());

        verify(aiService, never()).generate(any());
        verifyNoInteractions(telegramClient);
    }

    @Test
    void consume_ignoresMessageWithoutText() throws TelegramApiException {
        Update update = new Update();
        update.setMessage(Message.builder()
                .chat(Chat.builder().id(1L).type("private").build())
                .build());

        handler.consume(update);

        verify(aiService, never()).generate(any());
        verifyNoInteractions(telegramClient);
    }

    @Test
    void consume_swallowsTelegramApiException() throws TelegramApiException {
        Update update = textUpdate(1L, "hola");
        when(aiService.generate("hola")).thenReturn("respuesta");
        doThrow(new TelegramApiException("boom")).when(telegramClient).execute(any(SendMessage.class));

        handler.consume(update);

        verify(telegramClient).execute(any(SendMessage.class));
    }

    private static Update textUpdate(long chatId, String text) {
        Update update = new Update();
        update.setMessage(Message.builder()
                .text(text)
                .chat(Chat.builder().id(chatId).type("private").build())
                .build());
        return update;
    }
}
