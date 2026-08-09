package com.togudv.sylphy.service.tools;

import com.togudv.sylphy.model.Frequency;
import com.togudv.sylphy.model.RecurrentConfig;
import com.togudv.sylphy.model.Reminder;
import com.togudv.sylphy.service.AITool;
import com.togudv.sylphy.service.ReminderService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ReminderAITool implements AITool {
    private final ReminderService reminderService;

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
            + "redactalo en espanol"
            + "Una o dos frases como "
            + "maximo.")
    public String createReminder(
            @ToolParam(description = "Nombre del recordatorio") String name,
            @ToolParam(description = "Descripcion del recordatorio") String description,
            @ToolParam(description = "Fecha donde se dará el recordatorio yyyy-MM-dd'T'HH:mm:ss") LocalDateTime remindDate,
            @ToolParam(description = "Mensaje personalizado en espanol, tono cercano, segunda persona, "
                    + "que el bot enviara cuando se dispare el recordatorio") String notificationMessage,
            @ToolParam(description = "Frecuencia de recurrencia (MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, "
                    + "YEARLY). null si el recordatorio es de una sola vez.", required = false) Frequency frequencyType,
            @ToolParam(description = "Cada cuantas unidades de frequencyType se repite el recordatorio. "
                    + "Solo si frequencyType no es null; si no se indica, se usa 1.", required = false) Integer recurrenceInterval,
            @ToolParam(description = "Numero total de veces que debe dispararse el recordatorio. "
                    + "null significa indefinido. Solo si frequencyType no es null.", required = false) Integer occurrences)
    {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del recordatorio es obligatorio");
        }
        if (remindDate == null) {
            throw new IllegalArgumentException("La fecha del recordatorio es obligatoria");
        }
        RecurrentConfig config = frequencyType == null
                ? null
                : RecurrentConfig.of(frequencyType, recurrenceInterval, occurrences);
        Reminder reminder = new Reminder(null, name, description, LocalDateTime.now(), remindDate, config, notificationMessage);
        reminderService.create(reminder);
        log.info("tool: recordatorio creado '{}'", name);
        return "El recordatorio: " + name + " ha sido guardado.";
    }

    @Tool(description = "Obtiene toda la lista de recordatorios")
    public String getAllReminders()
    {
        List<Reminder> reminders = reminderService.getAll();
        log.info("tool: consultados todos los recordatorios");
        if (reminders.isEmpty()) {
            return "No hay recordatorios guardados.";
        }
        StringBuilder sb = new StringBuilder("Recordatorios:\n");
        for (Reminder r : reminders) {
            sb.append("- ").append(r.getName())
                    .append(" (proximo: ").append(r.getNextDate()).append(')')
                    .append('\n');
        }
        return sb.toString();
    }
    @Tool(description = "Obtener fecha actual, util para antes de crear un recordatorio")
    public String getCurrentDate() {
        LocalDateTime now = LocalDateTime.now();
        return now.toString();
    }
}
