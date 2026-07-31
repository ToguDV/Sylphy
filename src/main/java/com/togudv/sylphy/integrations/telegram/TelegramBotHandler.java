package com.togudv.sylphy.integrations.telegram;

import com.togudv.sylphy.service.AIService;
import com.togudv.sylphy.service.ReminderService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
        System.out.println("Got message");
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message_text = update.getMessage().getText();
            System.out.println(message_text);
            long chat_id = update.getMessage().getChatId();
            System.out.println("chatid"+chat_id);

            String output = aiService.generate(message_text);

            SendMessage message = SendMessage
                    .builder()
                    .chatId(chat_id)
                    .text(output)
                    .build();
            try {
                System.out.println("Trying to send message");
                telegramClient.execute(message);
                System.out.println("Message send");
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    @AfterBotRegistration
    public void afterRegistration(BotSession botSession) {
        System.out.println("Registered bot running state is: " + botSession.isRunning());
    }

}
