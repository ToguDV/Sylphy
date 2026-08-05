package com.togudv.sylphy.integrations.telegram;

import com.togudv.sylphy.service.AIService;
import com.togudv.sylphy.service.ReminderService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
public class TelegramBotHandler implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final TelegramClient telegramClient;
    private final String botToken;
    private final ReminderService reminderService;
    private final AIService aiService;

    @SuppressFBWarnings(
            value = "EI2",
            justification = "ReminderService y AIService son beans singleton gestionados por Spring; la referencia es estable por contrato del contenedor.")
    public TelegramBotHandler(
            @Value("${telegram.bot.token:}") String botToken,
            TelegramClient telegramClient,
            ReminderService reminderService,
            AIService aiService) {
        this.botToken = botToken;
        this.telegramClient = telegramClient;
        this.reminderService = reminderService;
        this.aiService = aiService;
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

            String output = aiService.generate(messageText);

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

}
