# Revisión del proyecto — Sylphy

Fecha: 2026-08-06
Estado del build al momento de la revisión: `BUILD SUCCESSFUL` (compilación, tests, Spotbugs main+test, JaCoCo).

---

## 🔴 Bugs lógicos (corregir antes de producción)

### 1. `ReminderAITool` permite que el LLM fije `creationDate` arbitrariamente
- `service/tools/ReminderAITool.java:67` — El tool construye la entidad con el `creationDate` que le pase el LLM. La ruta REST (`ReminderMapper.toEntity(CreateReminderDTO)`) lo pisa con `LocalDateTime.now()` del servidor. Son dos comportamientos inconsistentes para la misma operación. El LLM puede inventar una fecha futura o pasada.
- **Fix:** El tool debería usar `LocalDateTime.now()` para `creationDate`, igual que el mapper.

### 2. `ReminderController.getAll()` tiene casteo inseguro `(List<Reminder>)`
- `controller/ReminderController.java:41` — Convierte `Iterable<Reminder>` a `List<Reminder>` sin `instanceof`. Si el repositorio alguna vez cambia la implementación subyacente (o Spring Data decide devolver otro `Iterable`), esto explota con `ClassCastException` en runtime.
- **Fix:** `StreamSupport.stream(iterable.spliterator(), false)` o que el servicio devuelva `List<Reminder>` directamente.

### 3. `ReminderService.deleteById` nunca lanza excepción para IDs inexistentes
- `service/ReminderService.java:69` — Delega en `CrudRepository.deleteById(id)` que en Spring Data 3.x+ **no lanza** si el ID no existe. Sin embargo, el test `ReminderControllerTest:219-227` mockea que sí lanza `NoSuchElementException` esperando 404. En producción, borrar un ID inexistente devolvería 204 (éxito silencioso) en vez de 404, lo cual es comportamiento incorrecto para una API REST.
- **Fix:** Hacer `findById` primero, lanzar `NoSuchElementException` si no existe, luego borrar.

### 4. `AIService` es vulnerable a inyección de formato en `String.formatted`
- `service/AIService.java:57` — `spec.system(REPLY_CONTEXT_SYSTEM_PROMPT.formatted(replyToText))` donde `REPLY_CONTEXT_SYSTEM_PROMPT` contiene `%s`. Si `replyToText` contiene `%s`, `%d`, etc., `formatted()` lanza `IllegalFormatException` y rompe la conversación.
- **Fix:** Concatenación directa o `replace("«%s»", replyToText)` literal.

### 5. `TelegramBotHandler.consume` no captura excepciones del LLM
- `integrations/telegram/TelegramBotHandler.java:68` — Si `aiService.generate()` lanza (por timeout de red, error del modelo, `IllegalFormatException` del bug #4, etc.), la excepción no se captura y **mata el consumer single-thread del bot**. El bot deja de responder hasta que se reinicie la app.
- **Fix:** Envolver `aiService.generate()` y `telegramClient.execute()` en try-catch con log y respuesta de fallback al usuario.

---

## 🟡 Malas prácticas y defectos de diseño

### 6. Dispatchers múltiples: entrega duplicada si uno falla y otro no
- `service/ReminderScheduler.java:37-44` — Itera dispatchers; si el primero envía OK y el segundo falla, se hace `return` sin avanzar. En el siguiente tick, ambos disparan de nuevo → **notificación duplicada** para el primero. No hay idempotencia.
- **Sugerencia:** Enviar todos primero, acumular errores, avanzar solo si todos OK. O al menos registrar que el primero ya entregó.

### 7. `MemoryConsolidationService.foldPeriods` no protege contra periodKeys malformados
- `service/conversation/MemoryConsolidationService.java:132-133` — `keyParser.apply(s.getPeriodKey())` puede lanzar `DateTimeParseException` si el `periodKey` en BD está corrupto. Esto **aborta toda la consolidación** del nivel, no solo esa conversación. No hay try-catch.
- **Fix:** Envolver el parseo en try-catch, loguear y saltar esa entrada corrupta.

### 8. `TelegramBotHandler` inyecta `ReminderService` pero nunca lo usa
- Campo muerto (`TelegramBotHandler.java:25`). Rompe la convención de inyectar solo lo necesario y añade un acoplamiento espurio.

### 9. `CreateReminderDTO` y `UpdateReminderDTO` son idénticos
- Duplicación exacta de 4 campos + validaciones. Cualquier cambio debe hacerse en dos sitios. Si divergen, es un bug.

### 10. `@SuppressWarnings("removal")` sin justificación clara en test
- `controller/ReminderControllerTest.java:43` — Suprime un warning de "removal" pero no hay nada deprecated a simple vista. Puede ser residuo de una versión anterior o de otra API.

### 11. `ReminderAITool.getAllReminders` usa `toString()` del Iterable
- `service/tools/ReminderAITool.java:78` — Devuelve `reminders.toString()` que depende de `@Data` en `Reminder`. Frágil, ilegible para el LLM si hay muchos recordatorios, y acoplado al formato de `toString()` de Lombok.

### 12. `MemoryConsolidationService.consolidateDaily` hace query sin límite ni batching
- `service/conversation/MemoryConsolidationService.java:78` — `findByTimestampBefore(todayStart)` carga **todos** los mensajes antiguos de **todas** las conversaciones en una sola consulta. Si la app está caída días, esto puede cargar miles de registros en memoria de golpe.

---

## 🟢 Observaciones menores

| # | Archivo | Observación |
|---|---------|------------|
| 13 | `ReminderAITool.java:22` | `@Autowired` redundante en constructor único (Spring lo inyecta automáticamente) |
| 14 | `ReminderScheduler.java:48-50` | Si `advanceAfterFire` lanza `IllegalArgumentException`, el recordatorio queda vencido y se re-dispara cada tick → bucle infinito de notificaciones. No hay mecanismo de "marcar como roto". |
| 15 | `ReminderController.java:55` | `create()` hace `mapper.toDto(reminder)` justo después del `create` — pero el `reminder` no tiene `id` asignado aún si el `save` no hizo flush. Funciona pero es sutil. |
| 16 | `NextDateCalculator.java:34` | `anchor.plusMonths(interval)` — caso borde: 31 enero + 1 mes = 28 feb. Correcto por `LocalDate`, pero inesperado para el usuario que pone recordatorio "cada mes el día 31". |
| 17 | `JpaChatMemory.java:76` | Las etiquetas de resumen se construyen en `MemoryConsolidationService.levelLabel()` que usa `switch`. Si se añade un nuevo `MemoryLevel`, el `get()` concatenará "null: content" silenciosamente. |

---

## Resumen de prioridades

- **Críticos (bugs reales):** #1, #2, #3, #4, #5
- **Importantes (deuda técnica con impacto):** #6, #7, #8
- **Menores (mejora sin urgencia):** #9–#17

El build pasa, los tests cubren bien y Spotbugs está verde. Los problemas están en la lógica de runtime y los edge cases, no en la compilación.

---

## Veredicto contrastado

La revisión es útil, pero no debe aplicarse literalmente. La mayoría de los riesgos señalados son válidos, aunque hay dos diagnósticos incorrectos y varias observaciones que son decisiones de diseño o mejoras opcionales, no bugs actuales.

**Estado de aplicación: 2026-08-06** — ✅ = aplicado en el código; — = sin cambio (por el motivo indicado).

| # | Veredicto | Recomendación | Estado |
|---|---|---|---|
| 1 | Correcto | `creationDate` debe asignarlo el servidor, igual que en la ruta REST. Corregir. | ✅ Aplicado — el parámetro se eliminó del tool; asigna `LocalDateTime.now()` |
| 2 | Correcto | El casteo de `Iterable` a `List` es inseguro. Usar `StreamSupport` o devolver una colección concreta desde el servicio. Corregir. | ✅ Aplicado — `getAll()` devuelve `List<Reminder>` |
| 3 | Correcto | `CrudRepository.deleteById` puede ignorar un ID inexistente y producir `204`. Comprobar la existencia antes de borrar. Corregir. | ✅ Aplicado — `findById` previo + `NoSuchElementException` → 404 |
| 4 | Incorrecto | `replyToText` es un argumento de `formatted`; los caracteres `%` que contiene no se vuelven a interpretar como formato. No cambiar por este motivo. | — Sin cambio (diagnóstico incorrecto) |
| 5 | Correcto | Una excepción de `AIService.generate` escapa del consumidor de Telegram. Debe aislarse el procesamiento de cada mensaje y registrarse un fallback. Corregir. | ✅ Aplicado — try-catch en `consume` con respuesta de fallback |
| 6 | Correcto como riesgo futuro | Con varios dispatchers puede haber entregas duplicadas. La solución propuesta no garantiza idempotencia; haría falta registrar el estado de entrega por dispatcher. No es urgente mientras solo exista uno. | ✅ Aplicado (mejora) — despacha a todos acumulando errores; avanza solo si todos OK. Idempotencia por dispatcher sigue pendiente |
| 7 | Correcto | Un `periodKey` corrupto puede abortar el resto de la consolidación. Capturar el error, registrarlo y aislar la entrada o conversación afectada. | ✅ Aplicado — `parsePeriodKey` con try-catch; la entrada corrupta se omite |
| 8 | Correcto | `ReminderService` está inyectado en `TelegramBotHandler` pero no se utiliza. Eliminar la dependencia como limpieza. | ✅ Aplicado — dependencia y supresión EI2 eliminadas |
| 9 | No requiere cambio ahora | Mantener DTOs separados permite que creación y actualización evolucionen de forma independiente. La duplicación actual no es un bug. | — Sin cambio (decisión: mantener) |
| 10 | Revisión válida, no urgente | Comprobar si el warning sigue existiendo y eliminar la supresión si no es necesaria. | ✅ Aplicado — verificado: el warning existe (`MappingJackson2HttpMessageConverter`, removal en Spring 7); supresión acotada a `setUp` con justificación |
| 11 | Correcto como mejora | La salida del tool debería ser determinista y legible, sin depender del `toString()` de Lombok. | ✅ Aplicado — listado legible por recordatorio |
| 12 | Correcto como riesgo de escalabilidad | La consulta puede cargar demasiados mensajes. Priorizar antes de usar una base persistente o manejar grandes periodos de caída; aplicar batching con cuidado para no romper la consolidación atómica. | ✅ Aplicado (mejora) — `consolidateDaily` pagina por lotes de 500 |
| 13 | Correcto, cosmético | `@Autowired` es redundante en un constructor único. Puede eliminarse al tocar la clase. | ✅ Aplicado — eliminado |
| 14 | Correcto y subestimado | Una configuración inválida puede provocar notificaciones repetidas en cada tick. Debe existir una forma de bloquear, marcar o aislar el recordatorio defectuoso. | ✅ Aplicado — el recordatorio roto se **elimina** al fallar `advanceAfterFire` (decisión: eliminar, no marcar) |
| 15 | No es un bug urgente | Con JPA normalmente el mismo objeto recibe el ID al hacer `persist`. Devolver la entidad guardada desde el servicio sería más claro, pero no es necesario por esta observación. | — Sin cambio (no urgente) |
| 16 | Correcto como decisión de producto | `plusMonths` normaliza el día 31 al último día disponible y puede cambiar el día en ejecuciones posteriores. No cambiar hasta definir explícitamente la semántica de “mensual”. | ✅ Aplicado — decisión funcional tomada: fijar último día del mes vía `dayOfMonth` interno (31 ene → 28 feb → 31 mar) |
| 17 | Incorrecto | El `switch` de `levelLabel` es una expresión exhaustiva. Si se añade un nuevo `MemoryLevel`, el código no compila hasta añadir el caso; no devuelve `null` silenciosamente. | — Sin cambio (diagnóstico incorrecto) |

### Prioridad resultante

- Corregir antes de producción: **#1, #2, #3, #5, #7 y #14** — ✅ todos aplicados.
- Hacer como limpieza o mejora posterior: **#6, #8, #10, #11, #12 y #13** — ✅ todos aplicados.
- No modificar basándose únicamente en esta revisión: **#4 y #17** — sin cambio, como indicaba el veredicto.
- Mantener sin refactor inmediato: **#9 y #15** — sin cambio.
- Resolver mediante una decisión funcional antes de cambiar el código: **#16** — ✅ resuelto (semántica "fijar último día del mes").
