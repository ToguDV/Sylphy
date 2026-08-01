package com.togudv.sylphy.service.tools;

import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.service.AITool;
import com.togudv.sylphy.service.ReminderService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReminderAITool implements AITool {
    private final ReminderService reminderService;

    @Autowired
    @SuppressFBWarnings(
            value = "EI2",
            justification = "ReminderService is a Spring-managed singleton bean; the reference is reference-stable by container contract.")
    public ReminderAITool(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public String getName() {
        return "reminder";
    }


    @Tool(description = "Crea y guarda un recordatorio. El campo notificationMessage es el "
            + "texto que el bot enviara al usuario cuando se dispare el recordatorio: "
            + "redactalo en espanol, en segunda persona, con tono cercano y personal, "
            + "mencionando el detalle concreto que pidio el usuario. Una o dos frases como "
            + "maximo, sin markdown, sin comillas, sin emojis.")
    public String createReminder(
            @ToolParam(description = "Nombre del recordatorio") String name,
            @ToolParam(description = "Descripcion del recordatorio") String description,
            @ToolParam(description = "Fecha de creacion yyyy-MM-dd'T'HH:mm:ss") LocalDateTime creationDate,
            @ToolParam(description = "Fecha donde se dará el recordatorio yyyy-MM-dd'T'HH:mm:ss") LocalDateTime remindDate,
            @ToolParam(description = "Define si el recordatorio es recurrente") Boolean isRecurrent,
            @ToolParam(description = "Mensaje personalizado en espanol, tono cercano, segunda persona, "
                    + "que el bot enviara cuando se dispare el recordatorio") String notificationMessage)
    {
        Reminder reminder = new Reminder(null, name, description, creationDate, remindDate, null, notificationMessage);
        reminderService.create(reminder);
        System.out.println("Recordatorio guardado");
        System.out.println(reminder);
        return "El recordatorio: "+name +" "+"ha sido guardado.";
    }

    @Tool(description = "Obtiene toda la lista de recordatorios")
    public String getAllReminders()
    {
        System.out.println("Obteniendo todos los reminders...");
        Iterable<Reminder> reminders = reminderService.getAll();
        System.out.println("Reminders obtenidos:");
        System.out.println(reminders);
        return reminders.toString();
    }
    @Tool(description = "Obtener fecha actual, util para antes de crear un recordatorio")
    public String getCurrentDate() {
        LocalDateTime now = LocalDateTime.now();
        return now.toString();
    }
}
