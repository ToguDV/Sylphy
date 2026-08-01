package com.togudv.sylphy.service.notification;

import com.togudv.sylphy.config.NotificationDestination;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.service.ReminderMessageComposer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramNotificationDispatcher implements NotificationDispatcher {

    private final TelegramClient telegramClient;
    private final NotificationDestination destination;
    private final ReminderMessageComposer composer;

    @Override
    public void dispatch(Reminder reminder) {
        String text = resolveText(reminder);
        SendMessage message = SendMessage.builder()
                .chatId(destination.value())
                .text(text)
                .build();
        try {
            telegramClient.execute(message);
            log.info("telegram: notificacion enviada para recordatorio id={}", reminder.getId());
        } catch (TelegramApiException e) {
            log.error("telegram: error al enviar notificacion para recordatorio id={}",
                    reminder.getId(), e);
            throw new NotificationDeliveryException(
                    "Fallo al enviar notificacion Telegram para recordatorio id="
                            + reminder.getId(), e);
        }
    }

    private String resolveText(Reminder r) {
        try {
            String composed = composer.compose(r);
            if (composed != null && !composed.isBlank()) {
                return composed.trim();
            }
            log.warn("telegram: composer devolvio mensaje vacio para recordatorio id={}, usando fallback",
                    r.getId());
        } catch (RuntimeException e) {
            log.warn("telegram: fallo al redactar mensaje para recordatorio id={}, usando fallback",
                    r.getId(), e);
        }
        String persisted = r.getNotificationMessage();
        if (persisted != null && !persisted.isBlank()) {
            return persisted;
        }
        return format(r);
    }

    private static String format(Reminder r) {
        StringBuilder sb = new StringBuilder("Recordatorio: ").append(r.getName());
        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            sb.append('\n').append(r.getDescription());
        }
        return sb.toString();
    }
}
