# AGENTS.md — Sylphy

> Operational guide for AI coding agents working on this repository. Read in full before any non-trivial change.

---

## Product Vision

**Sylphy is intended to become a complete personal virtual assistant**, not merely a reminder bot. The inspiration is an assistant that understands the user's current situation, remembers relevant facts, helps organize time, and supports the user in actually completing what they intend to do.

Telegram is the initial interface because it lets the project validate the assistant's backend, conversational model, memory, scheduling, and notification behavior without first building a frontend. It is a product shortcut, not the product boundary. The core domain and application services must remain usable by other interfaces later.

The long-term assistant should be able to:

- Maintain a reliable record of tasks, commitments, reminders, preferences, routines, and relevant personal context.
- Organize a flexible schedule rather than treating time as a collection of isolated fixed alarms.
- Know what the user is currently working on and preserve enough context to resume it.
- Follow up on active work: ask for progress, detect drift or inactivity, and help the user return to the intended task.
- Support postponement explicitly, including a reason, a new time, and the next follow-up instead of silently losing the commitment.
- Learn gradually from conversations and observed choices, while keeping the user in control of what is remembered, corrected, or forgotten.
- Eventually interact with more platforms and device capabilities, while keeping the assistant's decisions and memory in the backend.

### Product surfaces and boundaries

- **Current surface:** Telegram long-polling bot. All MVP interaction is conversational and Telegram-first.
- **Near-term surface:** REST API for development, automation, and a future custom web UI. It is an additional interface to the same application capabilities, not a separate product.
- **Long-term surfaces:** web/mobile clients, additional messaging channels, and selected device integrations. These are different interfaces to the same personal assistant and must share the owner's tasks, context, memory, preferences, and relevant history through common application services rather than maintaining isolated silos.
- **Primary user language:** Spanish for user-facing text, AI tool descriptions, and product behavior; source identifiers remain in English.

### Product principles

- **Follow-through over notification volume:** a reminder is successful only when it helps the user act, not merely when a message is sent.
- **Explicit state:** planned, active, paused, postponed, completed, cancelled, and missed work should not be inferred only from chat text when the distinction affects behavior.
- **Human-controlled memory:** the assistant may suggest memories and preferences, but should not silently treat every conversation detail as permanent truth.
- **Context before automation:** do not add autonomous device actions, invasive monitoring, or aggressive nudging before the underlying intent, consent, and audit trail are clear.
- **Channel independence:** Telegram and future channel adapters handle transport and presentation; scheduling, memory, task state, follow-up policy, and assistant reasoning belong in shared application services. Channels are separate interfaces, not separate assistants or data silos.

## Scope Roadmap

The roadmap is directional. It explains what future work should converge toward; it does not authorize implementing every capability now.

### Phase 1: Reliable reminder and task foundation

- Create, inspect, update, postpone, complete, and cancel tasks and reminders through natural language.
- Deliver one-shot and recurring notifications reliably, with explicit occurrence and failure semantics.
- Establish the REST contract in parallel with the Telegram tools.
- Keep the domain free of Telegram-specific fields so the first single-chat setup can evolve later.

### Phase 2: Active-work tracking

- Model the user's current focus, active task, intended duration, interruptions, and check-ins.
- Add a follow-up loop that asks for progress at appropriate times and supports pause, resume, completion, and postponement.
- Distinguish a missed check-in from task completion; never mark work complete merely because a notification was delivered.
- Make nudging frequency, quiet hours, and escalation user-configurable.

### Phase 3: Personal memory and planning

- Separate operational data (tasks, dates, states) from conversational/personal memory (preferences, routines, constraints, and learned context).
- Use memory to improve planning and wording, with provenance, confidence, correction, deletion, and retention rules.
- Coordinate tasks, routines, deadlines, and flexible time blocks rather than only calculating reminder timestamps.
- Prefer explainable suggestions: the assistant should be able to state why it scheduled, postponed, or prioritized something.

### Phase 4: Multiplatform assistant

- Add web and mobile experiences over the same backend.
- Add integrations selectively for calendars, notes, email, and device capabilities when each has a clear user benefit and permission model.
- Support multiple channels and devices for the same owner. Sylphy is a personal assistant with exactly one owner per instance, not a shared multi-user or multi-tenant assistant. All channels should access the same assistant state and never create separate memories or task universes for that owner.

Do not implement Phase 2-4 as speculative abstractions during Phase 1. Instead, avoid choices that make them impossible: keep business logic out of Telegram handlers, use explicit domain state, preserve event/history information where follow-up or learning will need it, and isolate provider-specific AI code.

---

## Tech Stack (locked)

| Layer | Choice | Notes |
|---|---|---|
| Language | Java 25 | Toolchain pinned in `build.gradle:13`. |
| Framework | Spring Boot 4.1.0 | **Bleeding edge** — 4.x renamed `-web` to `-webmvc`. |
| AI | Spring AI 2.0.0 | `spring-ai-starter-model-openai` pointed at Mistral via OpenAI-compat endpoint. Previously Gemini (commented config kept in `application.properties`). |
| Telegram | `telegrambots-springboot-longpolling-starter:10.0.0` | Long-polling, not webhooks. |
| Persistence | Spring Data JPA + H2 (in-memory) | No migrations, no external DB. |
| Validation | `spring-boot-starter-validation` | Jakarta Bean Validation. |
| Boilerplate | Lombok | Partial usage — see Conventions. |
| Mapping | MapStruct 1.6.3 | `mapper/ReminderMapper` — DTO ↔ entity (`componentModel = "spring"`). |
| API docs | springdoc-openapi 3.1.0 | Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`. |
| Test | JUnit 5 (Jupiter) | `useJUnitPlatform()` in `build.gradle:54`. JaCoCo wired into `./gradlew check`. |

---

## Target Architecture

### Layered structure (existing + planned)

```
com.togudv.sylphy
├── controller/         # REST endpoints for future clients and automation
├── service/            # Business logic
│   └── tools/          # Spring AI @Tool implementations exposed to the LLM
├── repository/         # Spring Data JPA
├── mapper/             # MapStruct DTO ↔ entity mappers
├── dto/                # API contract types (Java records)
├── model/              # JPA entities + @Embeddable value objects
├── integrations/
│   └── telegram/       # Telegram transport and presentation adapter
└── SylphyApplication   # Entry point
```

This is **layered architecture**, not hexagonal. Do not refactor to ports/adapters without an explicit request — it is premature.

### Key patterns in use

- **Spring AI function calling** via `MethodToolCallbackProvider` built over `List<AITool>` in `AIService:17`.
- **Telegram bot SPI** via `SpringLongPollingBot` + `LongPollingSingleThreadUpdateConsumer` in `TelegramBotHandler`.
- **Interface-based tool registry**: implement `AITool` (`getName()`) to add new tools.
- **JPA aggregate**: `Reminder` is the root; `RecurrentConfig` is `@Embedded` (not a separate entity).
- **Constructor injection** is the preferred style. Field injection exists in legacy code (see Conventions).
- **REST contract + RFC 7807**: `ReminderController` returns DTO records via `ReminderMapper`; errors bubble up as domain exceptions (`NoSuchElementException`, `IllegalArgumentException`) and `GlobalExceptionHandler` translates them into `ProblemDetail` (404/400) with `properties.errors` for validation failures. `@Valid` on every request body.
- **Notification destination is config, not data.** A single `NotificationDestination` bean reads `telegram.notification.chat-id` from properties and is injected wherever a destination is needed. The `Reminder` entity carries no per-channel fields. When a second channel or a second user appears, this provider is refactored — the entity stays untouched.

### Notification destination (target)

- Lives in `application.properties` as `telegram.notification.chat-id`, resolved from the `TELEGRAM_NOTIFICATION_CHAT_ID` env var (with empty default — fail loudly at startup if missing, do not silently fall back).
- Exposed to the rest of the app via a `NotificationDestination` `@Component` (constructor-injected via `@Value`).
- The dispatcher depends on `NotificationDestination`, not on a per-reminder field.
- Rationale: one bot = one chat today. Storing `chatId` per `Reminder` would be redundant data that has to be backfilled and migrated when the web UI or a second user lands. Keep the domain clean; push channel-specific state to config until the model actually needs it.

### Domain model (target)

- `Reminder`: `id`, `name`, `description`, `creationDate`, `nextDate`, `recurrentConfig`. **No `chatId`, no `userId`** — those are channel/identity concerns, not reminder concerns.
- `RecurrentConfig` (`@Embeddable`): `frequencyType: Frequency`, `recurrenceInterval: Integer`, `occurrences: Integer`. (`Set<Frequency>` / `daysOfWeek` / `daysOfMonth` were removed during TODO #2 — the chosen `anchor + N units` algorithm does not need them. See TODO #13 for `occurrences` semantics.)
- `Frequency` enum: `MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY` — `public`, lives in its own file `model/Frequency.java`.

### Capability boundaries (target)

- **Assistant core:** tasks, reminders, commitments, memory, context, planning, follow-up policy, and explicit state transitions. These capabilities must not depend on Telegram classes.
- **Conversation layer:** interprets natural language, asks clarifying questions, proposes actions, and invokes application tools. It may use an LLM, but important state changes belong to deterministic services.
- **Scheduling and follow-up:** calculates notification times, tracks active work, handles check-ins, and records postponement reasons and outcomes. A delivered message is not evidence that work was completed.
- **Memory:** stores only information that is useful to the assistant, with source/provenance and a way for the user to correct or delete it. Do not turn the chat transcript into unquestioned permanent truth.
- **Telegram adapter:** currently provides the complete interaction surface: receive messages, present responses, and deliver notifications. It should translate transport details into application requests and never own business rules.
- **Other clients later:** REST, web, mobile, and device integrations consume the same application services; they do not reimplement task, memory, planning, or follow-up behavior.

---

## Current State

### ✅ Works (verified by code inspection)

- `./gradlew build` succeeds: compile + tests + spotbugs (main+test) + JaCoCo all green.
- Gradle build configuration is consistent; wrapper resolves.
- `Reminder` + `RecurrentConfig` + `Frequency` form a coherent aggregate.
- `ReminderRepository extends CrudRepository<Reminder, Long>` is valid.
- `ReminderService` full CRUD (`getAll`, `getById`, `create`, `updateById` — loads existing, applies the patch, saves, throws `NoSuchElementException` when missing —, `deleteById`) plus `advanceAfterFire` with the `occurrences` cap (last fire deletes the reminder).
- `ReminderAITool.createReminder` constructs `Reminder` with the matching 7-arg ctor (`id=null`), builds `RecurrentConfig.of(...)` only when `frequencyType != null`, and validates `name`/`creationDate`/`remindDate` deterministically.
- `mapper/ReminderMapper` (MapStruct) maps DTO ↔ entity; `creationDate` is server-assigned on create and ignored on update.
- REST contract at `/api/reminders` with `@Valid` bodies and RFC 7807 `ProblemDetail` errors via `GlobalExceptionHandler`.
- OpenAPI docs: Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`.
- Telegram bot message loop is wired: text → `AIService.generate` → reply; all logging via SLF4J (`@Slf4j`), no `System.out`.
- `AIService` builds a `ChatClient` with tools correctly.
- `application.properties` is syntactically valid; Mistral config is active.
- **Secrets are externalized.** `telegram.bot.token` and `spring.ai.openai.api-key` resolve from env vars (`TELEGRAM_BOT_TOKEN`, `SPRING_AI_OPENAI_API_KEY`) with empty defaults — nothing sensitive is hardcoded. A `.env` file at the project root is picked up via `spring.config.import=optional:file:.env[.properties]`. `TelegramBotHandler` injects the token with `@Value("${telegram.bot.token:}")` (constructor-injected).

### ❌ Still broken — fix before any feature work

- `ReminderController` `@GetMapping("/ ")` dead endpoint and the `System.out.println` / `e.printStackTrace()` logging in `TelegramBotHandler` are **fixed** (see TODO #5 and #10 below).
- **Historical secret leak (informational, not a code fix).** The initial commit (`0ee3e35`) hardcoded real Telegram bot and Mistral API keys. The keys were rotated; do not reintroduce secrets into source or configuration committed to git.

### 📋 Missing (priority-ordered TODO for MVP)

0. ~~Arreglar problemas encontrados con spotbugs~~ ✅ Done. CT closed by making `TelegramNotificationDestination` `final`; EI/EI2 on `CreateReminderDTO` closed by introducing `RecurrentConfigDTO` (JPA @Embeddable no longer leaks into the API contract); EI2 on `ReminderAITool` closed via `@SuppressFBWarnings` with justification. `./gradlew spotbugsMain` and `spotbugsTest` are green.
1. ~~Wire `NotificationDestination` provider (constructor-injected, reads `telegram.notification.chat-id`) and inject it into the dispatcher. The entity stays free of per-channel fields.~~ ✅ Provider done (`config/NotificationDestination` + `TelegramNotificationDestination`, constructor-injected, fails fast on missing `chat-id`). The "inject into dispatcher" half lands with TODO #3.
2. ~~Implement scheduler: `@EnableScheduling` + logic to read `RecurrentConfig`, compute next fire time, update `nextDate`, and trigger notification.~~ ✅ Done end-to-end. `service/NextDateCalculator` (pure function `next(Reminder)`, anchor + N units, throws on missing `frequencyType` or invalid `interval`, returns `null` to signal "one-shot → delete"); `service/ReminderScheduler` (`@Scheduled(fixedDelayString="${sylphy.scheduler.tick-millis:60000}")`); `service/notification/NotificationDispatcher` interface + `TelegramNotificationDispatcher` (real `SendMessage` via `TelegramClient.execute`, errors wrapped in `NotificationDeliveryException`); `integrations/telegram/TelegramConfig` provides the `TelegramClient` bean and `TelegramBotHandler` was refactored to inject it; `ReminderService.advanceAfterFire(id)` does the "compute next or delete" step inside a transaction; `ReminderRepository.findByNextDateLessThanEqual(now)` derived query; `SylphyApplication` enables scheduling and dropped three dead imports; `application.properties` adds `sylphy.scheduler.tick-millis=60000`. Test coverage: 5 in `ReminderSchedulerTest`, 11 in `NextDateCalculatorTest`, 3 in `TelegramNotificationDispatcherTest`. `./gradlew build` (compile + test + spotbugs main+test) green. Side-effect refactors: `RecurrentConfig.frequencyType` (and the DTO field) changed from `Set<Frequency>` to `Frequency` (the set was incoherent with the calculator's strict semantics); `daysOfWeek` / `daysOfMonth` removed from `RecurrentConfig` and the DTO (the chosen `anchor + N units` algorithm does not consume them — keeping them would have been a dead-field smell; if multi-day semantics are needed later, that is a separate design + a new `NextDateCalculator` algorithm).
3. Implement notification dispatcher (Telegram client push from a scheduled task). Will consume `NotificationDestination` (TODO #1) at that point. ✅ Done as a side-effect of TODO #2: `TelegramNotificationDispatcher` does the real `SendMessage` push and reads `NotificationDestination` for the chat id.
4. ~~Wire `createReminder` AI tool to populate `RecurrentConfig` correctly (the `isRecurrent` boolean is currently dropped; the `RecurrentConfig` argument is passed as `null`).~~ ✅ Done. `ReminderAITool.createReminder` now takes `Frequency frequencyType`, `Integer recurrenceInterval`, `Integer occurrences` (ToolParams en español); construye `RecurrentConfig.of(...)` solo cuando `frequencyType != null` (config `null` = recordatorio de una sola vez) y lo pasa al ctor de 7-arg. La validación de frontera (freq obligatoria si hay config, `recurrenceInterval` default `1` y `>= 1`, `occurrences` si está definido `>= 1`) vive en `ReminderService.create` vía `validateRecurrence` (reutilizable por el REST #5 y por #13), no en el tool. `RecurrentConfig` ganó un `static of(...)` de conveniencia (Lombok `@Data` no generaba ctor con args). Tests: 5 en `ReminderAIToolTest`, 9 en `ReminderServiceTest`. `./gradlew build` verde.
5. ~~Implement real CRUD endpoints in `ReminderController` (GET list, GET by id, POST, PUT, DELETE).~~ ✅ Done. `ReminderController` now exposes `GET /api/reminders` (list), `GET /api/reminders/{id}`, `POST /api/reminders` (201, `creationDate` es asignado por el mapper al servidor, nunca viene del cliente), `PUT /api/reminders/{id}` (reemplazo completo de `name`, `description`, `nextDate`, `recurrentConfig`, `notificationMessage`; `creationDate` se conserva) y `DELETE /api/reminders/{id}` (204). El endpoint muerto `@GetMapping("/ ")` fue eliminado. `ReminderService` ganó `getById(id)` (lanza `NoSuchElementException`) y `updateById` ahora valida recurrencia y copia `notificationMessage`. Tests: 13 en `ReminderControllerTest` (MockMvc standalone + advice).
6. ~~Add a DTO ↔ entity mapper. MapStruct is already on the classpath — write the `@Mapper` interface.~~ ✅ Done. `mapper/ReminderMapper` (`@Mapper(componentModel = "spring")`): `toEntity(CreateReminderDTO)` (ignora `id`, setea `creationDate = now`), `toEntity(UpdateReminderDTO)` (ignora `id` y `creationDate`), `toDto(Reminder)`, `toRecurrentConfigDto(RecurrentConfig)`. El impl generado usa el ctor de 7-arg de `Reminder`, que ahora es manual (no Lombok) y acepta `creationDate` nullable solo para el path de update (justificado con `@SuppressFBWarnings` EI2/NP). Tests: 5 en `ReminderMapperTest`.
7. ~~Stop leaking the JPA `RecurrentConfig` through `CreateReminderDTO`; create a parallel `RecurrentConfigDTO`.~~ ✅ Done. `dto/RecurrentConfigDTO` is a record; `CreateReminderDTO.recurrentConfig` is now typed as the DTO. (The compact-constructor `Set.copyOf` was dropped together with the `daysOfWeek` / `daysOfMonth` removal in TODO #2.)
8. ~~Add `@ControllerAdvice` for RFC 7807 problem-details error responses.~~ ✅ Done. `controller/GlobalExceptionHandler` (`@RestControllerAdvice` + `ProblemDetail`): `NoSuchElementException` → 404 "Recurso no encontrado"; `IllegalArgumentException` → 400; `MethodArgumentNotValidException` → 400 con `properties.errors` (lista `campo: mensaje`); `HttpMessageNotReadableException` → 400; excepciones genéricas → 500 logueadas. Tests: 5 en `GlobalExceptionHandlerTest` + cobertura vía `ReminderControllerTest`.
9. ~~Add `@Valid` on every controller request body and parameter validation on `@Tool` methods.~~ ✅ Done. `@Valid` en `POST`/`PUT` de `ReminderController`; `CreateReminderDTO` exige `name` y `nextDate` (`@NotNull @Future`), `UpdateReminderDTO` igual; `RecurrentConfigDTO` valida `recurrenceInterval >= 1` y `occurrences >= 1` con `@Min` (null-safe). `ReminderAITool.createReminder` ahora valida de forma determinista (name en blanco, `creationDate` o `remindDate` null → `IllegalArgumentException` con mensaje en español que el LLM recibe como feedback) — la validación de recurrencia sigue en `ReminderService.validateRecurrence`.
10. ~~Replace `System.out.println` with SLF4J (`@Slf4j`).~~ ✅ Done. `TelegramBotHandler` (`@Slf4j`, `log.info`/`log.error`, sin `printStackTrace`) y `ReminderAITool` (println eliminados). `grep -r "System.out" src/` no da resultados.
11. ~~Add tests. Project has 0% business-logic coverage.~~ ✅ Done — see TODO #14 for the coverage numbers. New since TODO #4: `ReminderMapperTest` (5), `ReminderControllerTest` (13), `GlobalExceptionHandlerTest` (5), `AIServiceTest` (2), `TelegramBotHandlerTest` (5), `ReminderServiceTest` grew from 9 to 22 (CRUD completo + `advanceAfterFire` + occurrences), `ReminderAIToolTest` grew from 5 to 9 (validación determinista + `getAllReminders` + `getCurrentDate`).
12. ~~Add OpenAPI/springdoc when the REST surface stabilizes.~~ ✅ Done. `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0` (serie 3.x, compatible con Spring Boot 4). Swagger UI en `/swagger-ui.html`, spec en `/v3/api-docs`.
13. ~~Make `RecurrentConfig.occurrences` cap the total number of fires of a recurring reminder.~~ ✅ Done. Semántica elegida: **`occurrences = N` significa que el recordatorio se dispara exactamente N veces en total; la última ocurrencia borra el recordatorio.** En `ReminderService.advanceAfterFire(id)`: si `occurrences != null` y `remaining <= 1` → `repository.delete(r)` (sin consultar a `NextDateCalculator`); si `remaining > 1` → decrementa y sigue el flujo normal (calcular `nextDate`). `occurrences == null` conserva el comportamiento infinito. La validación `occurrences >= 1` ya estaba en `create`/`update` vía `validateRecurrence`. Tests: `advanceAfterFire_lastOccurrenceDeletesReminder` (N=1 → fire + delete), `advanceAfterFire_decrementsRemainingOccurrences` (N=3 → queda 2 y se reprograma), el caso infinito ya cubierto por `advanceAfterFire_recurrentWithoutOccurrencesSchedulesNext`.
14. ~~Configure JaCoCo and bring test coverage up.~~ ✅ Done. (a) Plugin `jacoco` + `jacocoTestReport` (HTML + XML bajo `build/reports/jacoco`) integrado en `./gradlew check` (`check.dependsOn jacocoTestReport`). (b/c) Cobertura de línea actual (objetivo ≥80% en los paquetes de negocio): `service` 85.7%, `service/notification` 100%, `service/tools` 95.7%, `controller` 100%, `mapper` 95.6%, `model` 100%, `config` 100%, `dto` 100%, `integrations/telegram` 89.7%. (d) Ramas sin cubrir documentadas: `SylphyApplication.main` (bootstrap, 33%) y `ReminderScheduler`/`TelegramNotificationDispatcher` ya tienen tests propios desde TODO #2/#15.
15. ~~Personalizar el mensaje de notificación con IA.~~ ✅ Done con el **enfoque híbrido**: (a) al crear, el LLM redacta un `notificationMessage` base que se persiste en `Reminder.notificationMessage` (`@Column(length = 1000)`); (b) al disparar, `TelegramNotificationDispatcher` invoca `ReminderMessageComposer` (servicio, no `@Tool`) con system prompt en español y los campos de la entidad, y usa el texto que devuelve; (c) cadena de fallback en `resolveText`: composer OK → texto compuesto; composer lanza `RuntimeException` o devuelve blank → `r.getNotificationMessage()`; persistido blank → `format(r)` viejo (`Recordatorio: <name>` + descripción). Garantía: la notificación nunca se pierde, en el peor caso sale el formato genérico. Coste: 1 call LLM por cada fire real, 0 si Mistral está caído. Tests: 3 nuevos en `TelegramNotificationDispatcherTest` cubriendo las tres ramas del fallback + el caso "todo blank cae al formato viejo".

---

## Build & Run

```bash
# Build everything (compile + test + jar)
./gradlew build

# Run the application
./gradlew bootRun

# Run tests only
./gradlew test

# Clean build artifacts
./gradlew clean

# Run spotbugs only
./gradlew spotbugsMain

# Coverage report (HTML + XML under build/reports/jacoco)
./gradlew jacocoTestReport

```

### Required environment variables

- `TELEGRAM_BOT_TOKEN` — Telegram bot token from BotFather.
- `TELEGRAM_NOTIFICATION_CHAT_ID` — destination chat for outbound notifications (resolved by `NotificationDestination` from `telegram.notification.chat-id`). Must be set; the app should fail fast at startup if it is missing.
- `SPRING_AI_OPENAI_API_KEY` — Mistral API key. (`spring.ai.openai.base-url` is hardcoded to `https://api.mistral.ai/v1` and `spring.ai.openai.chat.options.model` to `mistral-small-latest`; if these change, both belong in properties, not env.)
- For local dev, a `.env` file at the project root is auto-imported (`spring.config.import=optional:file:.env[.properties]`). Use `KEY=value` lines; do **not** commit the file (it is git-ignored).

### Lint / typecheck

There is no separate lint or typecheck step beyond `./gradlew build`. There is no Checkstyle, or SonarQube configuration.
Spotbugs is configurated and working.

---

## Code Conventions

- **Identifiers** (class, method, field, package, parameter): **English**.
- **User-facing strings, AI tool descriptions, comments**: **Spanish**. This matches the existing `ReminderAITool` descriptions and product intent.
- **Lombok**: use `@Data` on entities; consider expanding to services for getters/loggers. Do not use Lombok `@Builder` until a use case demands it.
- **Dependency injection**: **constructor injection preferred**. Both `TelegramBotHandler` and `ReminderService` are constructor-injected (the previous field injection in `ReminderService` was removed during TODO #2; the rest of the codebase already follows this convention). `TelegramBotHandler` does take `botToken` via `@Value`; that's a value, not a bean reference, and stays in the constructor. Constructor-injected beans (e.g. `AIService`, `ReminderAITool`) are the model to follow.
- **DTOs**: Java `record`, kept separate from JPA entities. Validate with Jakarta annotations. Do not let `@Embeddable` types leak into the API contract.
- **Validation**: `@NotBlank` on required strings, `@Future` on future-only timestamps, `@Valid` on controller request bodies.
- **Mappers**: prefer MapStruct (already on classpath) over manual mapping in services.
- **Error handling**: business errors throw a domain exception; let `@ControllerAdvice` translate. Do not return `null` to signal failure.
- **Logging**: SLF4J via `@Slf4j`. No `System.out.println` in committed code.

---

## Known Gotchas

- **Spring Boot 4.x renamed `-web` to `-webmvc`**. If a tutorial suggests `spring-boot-starter-web`, it is for Boot 3.x. Use `spring-boot-starter-webmvc`.
- **Spring AI 2.0.0** has API differences vs 1.x. The Mistral integration works via OpenAI-compat mode; do not assume Anthropic/Gemini-native starters behave the same.
- **The LLM provider is not pinned to a specific vendor** — `spring-ai-starter-model-openai` is being used as a transport. The project is currently pointed at Mistral; the commented-out lines in `application.properties` show it was on Gemini before. Treat LLM provider as a configuration concern, not a code concern.
- **MapStruct + Lombok.** Both annotation processors share the `annotationProcessor` configuration; MapStruct 1.6 has built-in Lombok support. The generated impl uses `Reminder`'s 7-arg constructor, which is written by hand (Lombok's `@AllArgsConstructor` was replaced in TODO #6 because it null-checked `creationDate` and broke the update mapping). If a new `@Mapper` fails to compile, check that the annotation processor is on the `annotationProcessor` configuration.
- **`ProblemDetail` serializes `properties` under a JSON key of the same name.** Clients must read `$.properties.errors`, not `$.errors`.
- **Bean Validation messages depend on the JVM locale** (e.g. `must not be blank` vs `no debe estar vacío`). Tests must not assert on the exact message text.
- **H2 is in-memory only.** Every restart wipes the database. Acceptable for dev, unacceptable for any persistent use.
- **The Dockerfile is the Spring Initializr default** (`FROM ubuntu:latest` + `ENTRYPOINT ["top", "-b"]`). Do not deploy with this image. A real Dockerfile is out of scope until deployment is planned.
- **`HELP.md` is auto-generated** by Spring Initializr and is not maintained. Ignore it.

---

## Out of Scope For Now — Ask Before Touching

- Migrating from H2 to PostgreSQL/MySQL or adding Flyway/Liquibase.
- Switching LLM provider (Anthropic, native Gemini, local models, etc.).
- Refactoring the layered structure into hexagonal/clean architecture.
- Renaming packages or moving classes across packages.
- Replacing the Dockerfile for production deployment.
- Adding Spring Security, rate limiting, or chat-id allowlisting (planned, not MVP).
- WebSocket / webhook-based Telegram integration (current is long-polling only).
- Shared multi-user or multi-tenant operation. Sylphy is intentionally one personal assistant per owner; future clients and channels belong to that same owner and must not mix identities or personal data.
- Autonomous device control, invasive activity monitoring, or aggressive anti-distraction behavior before consent, policy, and an audit trail exist.
- Treating every conversation detail as permanent memory without provenance, confidence, correction, and deletion semantics.

---

## Development Workflow

When starting a new task in this repo, in order:

1. **Propose the solution first** when the change is non-trivial, including why it fits the product direction and its tradeoffs.
2. **Verify the baseline** with `./gradlew build`. If it does not compile or tests fail, fix the regression before adding unrelated work.
3. **Write or update tests** alongside any non-trivial change. The project starts at 0% coverage; do not let it regress further.
4. **Run `./gradlew build`** again after the change to confirm compilation and tests.
5. **Do not commit** unless the user explicitly asks. Use the imperative mood in commit messages; reference any related TODO item from the list above.

If the task is in the "Missing" TODO list, do not implement it without first confirming priority and approach with the user — many of those items have non-obvious design decisions (e.g. what `chatId` looks like when reminders are created from the web UI, not Telegram).
