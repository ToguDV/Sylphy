package com.togudv.sylphy.integrations.telegram;

import com.togudv.sylphy.config.ConversationIdProvider;
import com.togudv.sylphy.service.AIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.reactions.SetMessageReaction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Slf4j
@Component
public class TelegramBotHandler implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private static final String RECEIVED_REACTION_EMOJI = "👀";
    private final TelegramClient telegramClient;
    private final String botToken;
    private final AIService aiService;
    private final ConversationIdProvider conversationIdProvider;

    public TelegramBotHandler(
            @Value("${telegram.bot.token:}") String botToken,
            TelegramClient telegramClient,
            AIService aiService,
            ConversationIdProvider conversationIdProvider) {
        this.botToken = botToken;
        this.telegramClient = telegramClient;
        this.aiService = aiService;
        this.conversationIdProvider = conversationIdProvider;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        log.info("telegram: update recibido");
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            log.info("telegram: mensaje de chat {}: {}", chatId, messageText);

            reactToReceivedMessage(update, chatId);

            String replyToText = null;
            if (update.getMessage().getReplyToMessage() != null
                    && update.getMessage().getReplyToMessage().hasText()) {
                replyToText = update.getMessage().getReplyToMessage().getText();
            }

            String output;
            try {
                output = aiService.generate(messageText, conversationIdProvider.getConversationId(), replyToText);
            } catch (RuntimeException e) {
                log.error("telegram: error al generar respuesta para chat {}", chatId, e);
                output = "Lo siento, no pude procesar tu mensaje. Intenta de nuevo en un momento.";
            }

            SendMessage message = SendMessage
                    .builder()
                    .chatId(chatId)
                    .text(output)
                    .build();
            try {
                telegramClient.execute(message);
                log.info("telegram: respuesta enviada a chat {}", chatId);
            } catch (TelegramApiException e) {
                log.error("telegram: fallo al enviar respuesta a chat {}", chatId, e);
            }
        }
    }

    @AfterBotRegistration
    public void afterRegistration(BotSession botSession) {
        log.info("telegram: bot registrado, running={}", botSession.isRunning());
    }

    private void reactToReceivedMessage(Update update, long chatId) {
        Integer messageId = update.getMessage().getMessageId();
        if (messageId == null) {
            log.warn("telegram: mensaje sin messageId, se omite la reaccion en chat {}", chatId);
            return;
        }
        SetMessageReaction reaction = SetMessageReaction
                .builder()
                .chatId(chatId)
                .messageId(messageId)
                .reactionTypes(List.of(new ReactionTypeEmoji(RECEIVED_REACTION_EMOJI)))
                .build();
        try {
            telegramClient.execute(reaction);
            log.info("telegram: reaccion {} enviada al mensaje {} en chat {}",
                    RECEIVED_REACTION_EMOJI, messageId, chatId);
        } catch (TelegramApiException e) {
            log.error("telegram: fallo al reaccionar al mensaje en chat {}", chatId, e);
        }
    }

}
