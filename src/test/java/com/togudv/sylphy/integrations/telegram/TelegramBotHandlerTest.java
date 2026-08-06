package com.togudv.sylphy.integrations.telegram;

import com.togudv.sylphy.config.ConversationIdProvider;
import com.togudv.sylphy.service.AIService;
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
    AIService aiService;
    @Mock
    ConversationIdProvider conversationIdProvider;

    TelegramBotHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelegramBotHandler("token", telegramClient, aiService, conversationIdProvider);
    }

    @Test
    void getBotToken_returnsConfiguredToken() {
        assertEquals("token", handler.getBotToken());
    }

    @Test
    void consume_sendsReplyForTextMessage() throws TelegramApiException {
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        Update update = textUpdate(42L, "hola");
        when(aiService.generate("hola", "owner-1", null)).thenReturn("respuesta");

        handler.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("respuesta", captor.getValue().getText());
        assertEquals("42", captor.getValue().getChatId());
    }

    @Test
    void consume_ignoresUpdateWithoutMessage() throws TelegramApiException {
        handler.consume(new Update());

        verify(aiService, never()).generate(any(), any(), any());
        verifyNoInteractions(telegramClient);
    }

    @Test
    void consume_ignoresMessageWithoutText() throws TelegramApiException {
        Update update = new Update();
        update.setMessage(Message.builder()
                .chat(Chat.builder().id(1L).type("private").build())
                .build());

        handler.consume(update);

        verify(aiService, never()).generate(any(), any(), any());
        verifyNoInteractions(telegramClient);
    }

    @Test
    void consume_swallowsTelegramApiException() throws TelegramApiException {
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        Update update = textUpdate(1L, "hola");
        when(aiService.generate("hola", "owner-1", null)).thenReturn("respuesta");
        doThrow(new TelegramApiException("boom")).when(telegramClient).execute(any(SendMessage.class));

        handler.consume(update);

        verify(telegramClient).execute(any(SendMessage.class));
    }

    @Test
    void consume_sendsFallbackWhenAiGenerationFails() throws TelegramApiException {
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        Update update = textUpdate(1L, "hola");
        when(aiService.generate("hola", "owner-1", null)).thenThrow(new RuntimeException("llm down"));

        handler.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("Lo siento, no pude procesar tu mensaje. Intenta de nuevo en un momento.",
                captor.getValue().getText());
    }

    @Test
    void consume_passesRepliedMessageTextToAi() throws TelegramApiException {
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        Update update = textUpdate(1L, "ok");
        Message quoted = Message.builder()
                .text("recuerda comprar pan")
                .chat(Chat.builder().id(1L).type("private").build())
                .build();
        update.getMessage().setReplyToMessage(quoted);
        when(aiService.generate("ok", "owner-1", "recuerda comprar pan")).thenReturn("de nada");

        handler.consume(update);

        verify(aiService).generate("ok", "owner-1", "recuerda comprar pan");
        verify(telegramClient).execute(any(SendMessage.class));
    }

    @Test
    void consume_ignoresRepliedMessageWithoutText() throws TelegramApiException {
        when(conversationIdProvider.getConversationId()).thenReturn("owner-1");
        Update update = textUpdate(1L, "ok");
        update.getMessage().setReplyToMessage(Message.builder()
                .chat(Chat.builder().id(1L).type("private").build())
                .build());
        when(aiService.generate("ok", "owner-1", null)).thenReturn("respuesta");

        handler.consume(update);

        verify(aiService).generate("ok", "owner-1", null);
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
