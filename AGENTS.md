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

---

## Scope Roadmap

The roadmap is directional. It explains what future work should converge toward; it does not authorize implementing every capability now.

### Phase 1: Reliable reminder and task foundation — ✅ MVP COMPLETE

- Create, inspect, update, postpone, complete, and cancel tasks and reminders through natural language.
- Deliver one-shot and recurring notifications reliably, with explicit occurrence and failure semantics.
- Establish the REST contract in parallel with the Telegram tools.
- Keep the domain free of Telegram-specific fields so the first single-chat setup can evolve later.

Phase 1 is done end-to-end: domain model, REST CRUD, AI tools, scheduler, Telegram delivery, error contract, and test coverage. The **MVP Completion Record** below documents every decision made while building it.

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
| Static analysis | Spotbugs 6.5.9 + `spotbugs-annotations` | Wired into `./gradlew build` (main + test). |
| API docs | springdoc-openapi 3.1.0 | Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`. |
| Test | JUnit 5 (Jupiter) | `useJUnitPlatform()` in `build.gradle:54`. JaCoCo wired into `./gradlew check`. |

---

## Target Architecture

### Layered structure (current)

```
com.togudv.sylphy
├── SylphyApplication       # Entry point (@EnableScheduling)
├── config/                 # NotificationDestination provider (chat-id resolution)
├── controller/             # REST endpoints + GlobalExceptionHandler (RFC 7807)
├── service/                # Business logic
│   ├── notification/       # NotificationDispatcher SPI + Telegram impl + delivery errors
│   └── tools/              # Spring AI @Tool implementations exposed to the LLM
├── repository/             # Spring Data JPA
├── mapper/                 # MapStruct DTO ↔ entity mappers
├── dto/                    # API contract types (Java records)
├── model/                  # JPA entities + @Embeddable value objects
└── integrations/
    └── telegram/           # Telegram transport and presentation adapter
```

This is **layered architecture**, not hexagonal. Do not refactor to ports/adapters without an explicit request — it is premature.

### Key patterns in use

- **Spring AI function calling** via `MethodToolCallbackProvider` built over `List<AITool>` in `AIService:17`.
- **Telegram bot SPI** via `SpringLongPollingBot` + `LongPollingSingleThreadUpdateConsumer` in `TelegramBotHandler`.
- **Interface-based tool registry**: implement `AITool` (`getName()`) to add new tools.
- **JPA aggregate**: `Reminder` is the root; `RecurrentConfig` is `@Embedded` (not a separate entity).
- **Constructor injection everywhere.** No field injection remains. `@Value` is used only for scalar config (bot token, chat id), never for bean references.
- **Service-side boundary validation shared by all entry points.** `ReminderService.validateRecurrence` runs on create and update, so REST, the AI tool, and the scheduler all get the same semantics regardless of caller.
- **Load-then-patch update.** `ReminderService.updateById` loads the existing entity, copies the editable fields, and saves — `creationDate` is never overwritten.
- **Transactional fire-and-advance.** `ReminderService.advanceAfterFire` computes the next fire date (or deletes the reminder, honoring the `occurrences` cap) inside a transaction; the scheduler never mutates the aggregate directly.
- **REST contract + RFC 7807**: `ReminderController` returns DTO records via `ReminderMapper`; errors bubble up as domain exceptions (`NoSuchElementException`, `IllegalArgumentException`) and `GlobalExceptionHandler` translates them into `ProblemDetail` (404/400) with `properties.errors` for validation failures. `@Valid` on every request body.
- **Notification destination is config, not data.** A single `NotificationDestination` bean reads `telegram.notification.chat-id` from properties and is injected wherever a destination is needed. The `Reminder` entity carries no per-channel fields. When a second channel or a second user appears, this provider is refactored — the entity stays untouched.
- **Delivery failure is retried, never skipped.** The scheduler only advances the reminder after every dispatcher succeeds; a failed delivery (`NotificationDeliveryException`, logged) leaves `nextDate` in the past so the next tick retries it.
- **Notification text has a guaranteed fallback chain.** Composer output → persisted `notificationMessage` → generic `format(Reminder)`; a notification is always sent even when the LLM is down.

### Runtime flows

**Creation (two entry points, one service):**

- Telegram: message → `AIService.generate` (LLM + tools) → `ReminderAITool.createReminder` → `ReminderService.create`.
- REST: `POST /api/reminders` (`@Valid`) → `ReminderMapper.toEntity` (server-assigned `creationDate`) → `ReminderService.create`.
- The LLM also drafts a base `notificationMessage` at creation time (hybrid approach — see MVP Completion Record).

**Fire (scheduler loop):**

- `ReminderScheduler.tick` (`@Scheduled`, `sylphy.scheduler.tick-millis`, default 60000) → `ReminderRepository.findByNextDateLessThanEqual(now)` → for each due reminder: dispatch to **every** `NotificationDispatcher` accumulating errors (a failure in one dispatcher does not skip the others) → only if all delivered, `advanceAfterFire` (compute next via `NextDateCalculator` or delete; `occurrences` decremented). Scheduler failure semantics: delivery error → reminder stays due and is retried next tick (documented limitation: with several dispatchers a partially-failed delivery is re-delivered to all; idempotency requires per-dispatcher delivery state — deferred while only one dispatcher exists); invalid recurrence config (`IllegalArgumentException` from `advanceAfterFire`) → the broken reminder is **deleted** so it cannot re-fire every tick; entity vanished mid-tick → warn and continue.
- `TelegramNotificationDispatcher` resolves text via `ReminderMessageComposer` (fallback chain above) and sends a real `SendMessage` through the `TelegramClient` bean.

### Notification destination (current)

- Lives in `application.properties` as `telegram.notification.chat-id`, resolved from the `TELEGRAM_NOTIFICATION_CHAT_ID` env var (with empty default — fails fast at startup if missing, no silent fallback).
- Exposed to the rest of the app via the `NotificationDestination` `@Component` (constructor-injected via `@Value`); `TelegramNotificationDestination` is the sole implementation, `final`, and throws `IllegalStateException` when unset.
- The dispatcher depends on `NotificationDestination`, not on a per-reminder field.
- Rationale: one bot = one chat today. Storing `chatId` per `Reminder` would be redundant data that has to be backfilled and migrated when the web UI or a second user lands. Keep the domain clean; push channel-specific state to config until the model actually needs it.

### Domain model (current)

- `Reminder`: `id`, `name`, `description`, `creationDate`, `nextDate`, `recurrentConfig`, `notificationMessage` (`@Column(length = 1000)`). **No `chatId`, no `userId`** — those are channel/identity concerns, not reminder concerns.
- `RecurrentConfig` (`@Embeddable`): `frequencyType: Frequency`, `recurrenceInterval: Integer`, `occurrences: Integer`, `dayOfMonth: Integer` (nullable, internal). **`occurrences = N` means the reminder fires exactly N times in total; the last fire deletes the reminder.** `occurrences == null` means infinite. `dayOfMonth` is assigned by `ReminderService` at create/update from `nextDate` for MONTHLY/YEARLY reminders: it is the day the user picked, so a monthly reminder on the 31st keeps firing on the 31st (31 ene → 28 feb → 31 mar) instead of drifting to the 28th. (`Set<Frequency>` / `daysOfWeek` / `daysOfMonth` were removed during the MVP — the chosen `anchor + N units` algorithm does not need them; see MVP Completion Record.)
- `Frequency` enum: `MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY` — `public`, lives in its own file `model/Frequency.java`.
- `NextDateCalculator.next(Reminder)` is a pure function: anchor = current `nextDate`, plus N units; MONTHLY/YEARLY clamp the day to the last day of the target month when it is shorter than the recorded `dayOfMonth` (falls back to the anchor's day when `dayOfMonth` is null, e.g. pre-existing rows); throws `IllegalArgumentException` on missing `frequencyType` or invalid interval; returns `null` to signal "one-shot → delete".

### Capability boundaries (target)

- **Assistant core:** tasks, reminders, commitments, memory, context, planning, follow-up policy, and explicit state transitions. These capabilities must not depend on Telegram classes.
- **Conversation layer:** interprets natural language, asks clarifying questions, proposes actions, and invokes application tools. It may use an LLM, but important state changes belong to deterministic services.
- **Scheduling and follow-up:** calculates notification times, tracks active work, handles check-ins, and records postponement reasons and outcomes. A delivered message is not evidence that work was completed.
- **Memory:** stores only information that is useful to the assistant, with source/provenance and a way for the user to correct or delete it. Do not turn the chat transcript into unquestioned permanent truth.
- **Telegram adapter:** currently provides the complete interaction surface: receive messages, present responses, and deliver notifications. It should translate transport details into application requests and never own business rules.
- **Other clients later:** REST, web, mobile, and device integrations consume the same application services; they do not reimplement task, memory, planning, or follow-up behavior.

---

## Current State — Phase 1 MVP complete

The original 16-item MVP TODO list is **done**. `./gradlew build` (compile + tests + spotbugs main+test + JaCoCo) is green, and the coverage target (≥80% line coverage in business packages) is met. The **MVP Completion Record** below is the permanent record of what was built and why.

### Verified working

- Full CRUD at `/api/reminders` (GET list, GET by id, POST 201, PUT full replace preserving `creationDate`, DELETE 204) with `@Valid` bodies, `ReminderMapper` (MapStruct), and RFC 7807 `ProblemDetail` errors.
- AI tools: `createReminder` (recurrence via `Frequency` / `recurrenceInterval` / `occurrences`), `getAllReminders`, `getCurrentDate` — deterministic validation in the tool, boundary validation in the service.
- Scheduler loop end-to-end: tick → due query → dispatch → advance/delete; recurring math in `NextDateCalculator`; `occurrences` cap honored.
- Telegram: message loop (text → `AIService.generate` → reply) plus outbound push via `TelegramClient` with the composer fallback chain.
- OpenAPI docs at `/swagger-ui.html` and `/v3/api-docs`.
- Secrets fully externalized; `.env` auto-imported for local dev; no `System.out` anywhere (`grep -r "System.out" src/` is clean).

### Historical note (informational, not a code fix)

The initial commit (`0ee3e35`) hardcoded real Telegram bot and Mistral API keys. The keys were rotated. **Do not reintroduce secrets into source or committed configuration.** Secrets live in env vars: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_NOTIFICATION_CHAT_ID`, `SPRING_AI_OPENAI_API_KEY`.

---

## MVP Completion Record (decision log)

The original priority-ordered TODO list, reorganized by theme. These are **decisions**, not just "things done": future changes must respect the semantics they establish or deliberately reverse them.

### Build & static analysis

- **Spotbugs green (was TODO #0).** `CT` closed by making `TelegramNotificationDestination` `final`; `EI/EI2` on `CreateReminderDTO` closed by introducing `RecurrentConfigDTO` (the JPA `@Embeddable` no longer leaks into the API contract); `EI2` on `ReminderAITool`, `ReminderController`, and `Reminder` closed via `@SuppressFBWarnings` with written justifications. Convention: suppressions are acceptable for Spring-managed singletons and for the aggregate root mutating its own `@Embedded` value — always with a justification.
- **SLF4J everywhere (was TODO #10).** `System.out.println` / `e.printStackTrace()` replaced with `@Slf4j` in `TelegramBotHandler` and `ReminderAITool`.
- **JaCoCo wired into `./gradlew check` (was TODO #14).** Plugin + report (HTML/XML under `build/reports/jacoco`). Line coverage at completion: `service` 85.7%, `service/notification` 100%, `service/tools` 95.7%, `controller` 100%, `mapper` 95.6%, `model` 100%, `config` 100%, `dto` 100%, `integrations/telegram` 89.7%. Uncovered branches are accepted only where documented (e.g. `SylphyApplication.main` bootstrap).

### Domain & API contract

- **`RecurrentConfigDTO` introduced (was TODO #7).** The API never exposes the JPA `@Embeddable`; DTOs are plain records.
- **MapStruct mapper (was TODO #6).** `mapper/ReminderMapper` (`componentModel = "spring"`): `toEntity(CreateReminderDTO)` ignores `id` and sets `creationDate = now` (server-assigned, never client-supplied); `toEntity(UpdateReminderDTO)` ignores `id` and `creationDate`; `toDto(Reminder)` / `toRecurrentConfigDto(RecurrentConfig)` round-trip.
- **Manual 7-arg `Reminder` constructor.** Replaced Lombok `@AllArgsConstructor`, which null-checked `creationDate` and broke the update mapping (the update path passes `creationDate = null`; `ReminderService.updateById` never persists it, it copies only editable fields onto the existing entity). `@Nullable` + `@SuppressFBWarnings(NP)` with justification.
- **`Frequency` promoted to a public top-level enum** (`MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY`) so DTO, entity, and tool can share it.
- **Recurrence model simplified (side-effect of TODO #2).** `frequencyType` changed from `Set<Frequency>` to a single `Frequency`; `daysOfWeek` / `daysOfMonth` were removed. The chosen `anchor + N units` algorithm cannot consume them — keeping them would have been a dead-field smell. If multi-day semantics are needed later, that is a separate design plus a new `NextDateCalculator` algorithm, not a field re-add. **Post-MVP addendum (REVISION.md #16):** a `dayOfMonth: Integer` field was added back with a different purpose — preserving the user's chosen day across short months for MONTHLY/YEARLY reminders (31 ene → 28 feb → 31 mar, decided semantics: clamp to the last day of the target month). It is assigned by `ReminderService` at create/update, never exposed in the API contract, and `null` falls back to the anchor's day (pre-existing rows).

### Validation & error handling

- **`GlobalExceptionHandler` (was TODO #8).** `@RestControllerAdvice` producing RFC 7807 `ProblemDetail`: `NoSuchElementException` → 404 "Recurso no encontrado"; `IllegalArgumentException` → 400; `MethodArgumentNotValidException` → 400 with `properties.errors` (`campo: mensaje`); `HttpMessageNotReadableException` → 400; generic exceptions → 500, logged.
- **`@Valid` + tool parameter validation (was TODO #9).** `@Valid` on every controller request body; `CreateReminderDTO` / `UpdateReminderDTO` require `name` (`@NotBlank`) and `nextDate` (`@NotNull @Future`); `RecurrentConfigDTO` enforces `recurrenceInterval >= 1` and `occurrences >= 1` (`@Min`, null-safe). `ReminderAITool.createReminder` validates deterministically in the tool (blank name, null `remindDate` → `IllegalArgumentException` with a Spanish message the LLM receives as feedback; the `creationDate` is always server-assigned, the LLM has no parameter for it).
- **Boundary validation centralized in the service.** `ReminderService.validateRecurrence` (frequency required if config present; interval defaults to 1 and must be ≥ 1; occurrences ≥ 1) runs on both create and update, shared by the REST and AI-tool paths — the tool does not re-implement it.

### REST surface

- **Full CRUD (was TODO #5).** `GET /api/reminders`, `GET /api/reminders/{id}`, `POST` (201), `PUT /api/reminders/{id}` (full replace of `name`, `description`, `nextDate`, `recurrentConfig`, `notificationMessage`; `creationDate` preserved), `DELETE` (204). The dead `@GetMapping("/ ")` endpoint was removed. `ReminderService` gained `getById` (throws `NoSuchElementException`).
- **OpenAPI/springdoc (was TODO #12).** `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0` (3.x series, compatible with Spring Boot 4). Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`.

### Scheduling & delivery

- **`NotificationDestination` provider (was TODO #1).** `config/NotificationDestination` interface + `TelegramNotificationDestination` implementation, constructor-injected via `@Value`, fails fast at startup when `telegram.notification.chat-id` is missing. The entity stays free of per-channel fields.
- **Scheduler + recurrence math (was TODO #2).** `@EnableScheduling`; `ReminderScheduler.tick` with `@Scheduled(fixedDelayString = "${sylphy.scheduler.tick-millis:60000}")`; `NextDateCalculator` (pure function, anchor = current `nextDate`, `null` result → one-shot delete); `ReminderRepository.findByNextDateLessThanEqual(now)` derived query; `advanceAfterFire` performs the compute-next-or-delete step inside a transaction; `TelegramConfig` provides the `TelegramClient` bean (bot handler refactored to inject it). Scheduler failure semantics: delivery error → reminder stays due and is retried next tick (all dispatchers are attempted, errors accumulated; advance only when all delivered); invalid recurrence config → the broken reminder is **deleted** so it cannot re-fire every tick; entity vanished mid-tick → warn and continue.
- **Dispatcher (was TODO #3).** `NotificationDispatcher` interface + `TelegramNotificationDispatcher` (real `SendMessage` through `TelegramClient`, errors wrapped in `NotificationDeliveryException`).
- **`occurrences` semantics (was TODO #13).** `occurrences = N` means the reminder fires exactly N times in total; the last fire deletes the reminder. In `advanceAfterFire`: `remaining <= 1` → delete without consulting the calculator; `remaining > 1` → decrement and reschedule normally; `occurrences == null` → infinite.
- **LLM-composed notification text (was TODO #15) — hybrid approach.** (a) At creation, the LLM drafts a base `notificationMessage` persisted on the entity; (b) at fire time, `ReminderMessageComposer` (a service, not an `@Tool`; Spanish system prompt, temperature 0.7) composes fresh text from the entity fields; (c) fallback chain in `resolveText`: composer OK → composed text; composer throws or returns blank → persisted `notificationMessage`; persisted blank → generic `format(Reminder)` ("Recordatorio: <name>" + description). Guarantee: the notification is never lost; worst case is the generic format. Cost: one LLM call per real fire, zero when Mistral is down. Tests cover all three fallback branches.

### AI tooling

- **`createReminder` recurrence wiring (was TODO #4).** Tool parameters in Spanish: `Frequency frequencyType`, `Integer recurrenceInterval`, `Integer occurrences`; builds `RecurrentConfig.of(...)` only when `frequencyType != null` (null config = one-shot). `RecurrentConfig` gained a static `of(...)` convenience factory (Lombok `@Data` generates no all-args constructor).

### Tests

- **Coverage built up (was TODO #11).** Test inventory at MVP completion: `ReminderSchedulerTest` (5), `NextDateCalculatorTest` (11), `TelegramNotificationDispatcherTest` (6), `ReminderMapperTest` (5), `ReminderControllerTest` (13), `GlobalExceptionHandlerTest` (5), `AIServiceTest` (2), `TelegramBotHandlerTest` (5), `ReminderServiceTest` (22), `ReminderAIToolTest` (9), `TelegramNotificationDestinationTest`, `SylphyApplicationTests`. Do not let business-package coverage regress below the ≥80% line target.

---

## Chat Memory & Consolidación Episódica (decision record, post-MVP)

Implemented to give the agent a persistent conversation thread whose history is shared across channels. This is the first slice of Phase 3 (episodic memory); the semantic-memory slice (provenance, correction, deletion by the user) is still pending.

### Design decisions

- **One shared conversation, resolved by config, not data.** `config/ConversationIdProvider` + `SingleOwnerConversationIdProvider` mirror the `NotificationDestination` pattern: every channel resolves the same `conversationId` (`sylphy.conversation.id`, default `owner-1`), so Telegram today and REST/web tomorrow read and write the **same** history. No Telegram `chatId` ever enters the domain. Multi-owner future = refactor this provider only.
- **Spring AI 2.0.0 native integration.** `JpaChatMemory` implements the `ChatMemory` SPI and is wired via `MessageChatMemoryAdvisor` in `AIService`; `ChatMemory.CONVERSATION_ID` is passed per request. `generate(input, conversationId)` is the canonical call; the 1-arg overload delegates to the provider for compatibility.
- **Decremental hierarchical consolidation (MemGPT-style), not a fixed window.** Raw messages (`ChatMessage`: `conversationId`, `role` USER/ASSISTANT — own enum, not Spring AI's, keeping the entity provider-free, `content` up to 4000 chars, `timestamp`) are kept only while recent; when the raw count reaches `sylphy.chat.history.window` (default 40) they are summarized into a `WINDOW` summary and deleted. Levels: `MemoryLevel WINDOW → DAILY → WEEKLY → MONTHLY → ANNUAL`. Each level folds the one below into a `MemorySummary` (`content`, `createdAt`, `periodKey`: `2026-08-05` / `2026-W32` / `2026-08` / `2026`); the fold target exists and then the lower level is deleted. Annual summaries are kept forever. `periodKey` is nullable (WINDOW summaries fold by `createdAt`).
- **Golden rule: nothing is deleted before its summary exists.** `MemoryConsolidationService` runs the LLM call outside any transaction; `persistAndDelete` (TransactionTemplate — Spring 7 removed `ResourcelessTransactionManager`) saves the summary and deletes the folded items in one transaction. Blank/null summary → abort, keep everything, retry on next trigger. If the LLM is down, raw messages grow (window trigger retries on every subsequent `add`) and the next successful job catches up — self-healing by design, no data loss.
- **Period-boundary folding uses parsed period keys, not `createdAt`.** Daily job folds `timestamp`/`createdAt` < today; weekly/monthly/annual fold items whose **period key** starts before the current period (a DAILY created Monday 02:00 for Sunday's data must fold into Monday's WEEKLY, not next week's). ISO week keys (`2026-W32`) are parsed via `DateTimeFormatter.ISO_WEEK_DATE`.
- **LLM context = all existing summaries + current raw window.** `JpaChatMemory.get` returns every summary as a `SystemMessage` labelled in Spanish (`Resumen diario (2026-08-05): ...`) plus the last `windowSize` raw messages. Bounded by the hierarchy: ≤5 DAILY + ≤5 WEEKLY + ≤12 MONTHLY + N ANNUAL. WINDOW summaries are injected too but are transient (folded nightly).
- **Known limitation (documented in Spring AI 2.0.0):** intermediate tool-call messages are not stored by the memory advisor; the user turn and the final assistant reply are. Sufficient for thread continuity; revisit only if tool-call internals are ever needed in history.
- **Consolidation jobs** (`MemoryConsolidationScheduler`): `sylphy.chat.memory.cron.daily` (default `0 0 2 * * *`), `.weekly` (`0 0 3 * * MON`), `.monthly` (`0 0 4 1 * *`), `.annual` (`0 0 5 1 1 *`). Jobs swallow and log failures; the next run retries.
- **Intentional latency/cost tradeoffs:** the WINDOW consolidation runs synchronously inside `ChatMemory.add`, briefly blocking the Telegram single-thread consumer (~1–2 s every 40 messages). One LLM call per consolidation trigger; zero calls when idle or when Mistral is down. The window trigger keeps raw ≤ `windowSize` (0..39) in normal operation.
- **Notifications do not enter the conversation history** (they are outbound pushes, not part of the chat client flow). If desired later, that is a Phase 3 provenance decision, not a bug.

### Coverage at implementation

`service/conversation` 94% line coverage (`JpaChatMemoryTest`, `MemoryConsolidationServiceTest`, `MemorySummarizerTest`, `MemoryConsolidationSchedulerTest`); `config` 100% (`SingleOwnerConversationIdProviderTest`). Business packages all ≥80%.

---

## System Prompt Configurable (decision record, post-MVP)

The assistant's system prompt (personality/behavior instructions) is now configurable at runtime — stored in DB, edited via REST or by chat.

### Design decisions

- **Single active prompt, single row.** `SystemPrompt` entity with fixed `id = 1L` (table `system_prompt`), `content` (≤10000 chars) and `updatedAt`. No versioning: PUT replaces. Coherent with the single-owner principle; adding a version history later is a separate feature.
- **Fallback chain: BD > archivo `system-prompt.txt` > nothing.** `SystemPromptService.getEffectivePrompt` returns the stored content if present and non-blank; otherwise it reads `sylphy.system-prompt.file` (default `system-prompt.txt` at the project root, same working-dir resolution as `.env`). A missing or blank file means no system prompt. Rationale: H2 wipes on restart (same known limitation as reminders), so the file default guarantees the bot never loses its base instructions. The file is read fresh on every `generate` call (one small read, no cache) so editing it while the bot runs takes effect immediately. This is the first system prompt ever injected into `AIService.generate` (previously only the per-request reply-context prompt existed); both can coexist (Spring AI merges system messages).
- **REST contract at `/api/system-prompt`:** `GET` → `SystemPromptDTO(content, updatedAt)` with `updatedAt == null` meaning "default in effect"; `PUT` with `@Valid SystemPromptUpdateDTO` (`@NotBlank @Size(max=10000)`) upserts and returns the stored state; `DELETE` (204, idempotent) clears the stored row to fall back to the default.
- **Chat access via AI tools.** `SystemPromptAITool` (`getName()` = `system-prompt`) exposes `getSystemPrompt()` (content + source "configurado"/"por defecto"), `updateSystemPrompt(content)` (deterministic validation: non-blank, ≤10000) and `resetSystemPrompt()`. Natural language drives configuration: "cambia tu personalidad...". The LLM is not asked to edit its own prompt implicitly — only when the user asks.
- **Prompt resolution happens per request, no cache.** One `findById` per `generate` call on H2 is negligible; caching would need invalidation on update for zero benefit today.

### Coverage at implementation

`SystemPromptServiceTest` (8), `SystemPromptControllerTest` (6), `SystemPromptAIToolTest` (8), `AIServiceTest` extended (7 total). Business packages all ≥80%.

---

## Reintentos del proveedor IA (decision record, post-MVP)

OpenRouter (modelo `:free`) devuelve ocasionalmente respuestas malformadas sin el campo `choices`, que `OpenAiChatModel` rechaza con `OpenAIInvalidDataException`. `AIService.generate` reintenta ahora los errores transitorios del proveedor.

- **Reintento en `AIService.generate`, configurable por properties.** `sylphy.ai.retry.max-attempts` (default 3) y `sylphy.ai.retry.delay-ms` (default 2000). El usuario no ve el fallback de Telegram ("Lo siento, no pude procesar...") a menos que el proveedor falle los 3 intentos.
- **Clasificación de errores transitorios:** `OpenAIInvalidDataException` (respuesta malformada, caso real observado), `OpenAIRetryableException` (marcado por el SDK) y `UnexpectedStatusCodeException` con status 429 o ≥500. Cualquier otro error se propaga sin reintento.
- **Guarda contra efectos secundarios duplicados.** Los `ToolCallback` se envuelven (`TrackingToolCallback`) para marcar en `ToolCallTracker` (ThreadLocal) que una herramienta se ejecutó. Si el fallo transitorio ocurre DESPUÉS de ejecutar alguna herramienta (2ª+ llamada del ciclo tool-calling), **no se reintenta**: el error se propaga para que el usuario repita, pero `createReminder` y demás tools con efectos nunca se re-ejecutan. El tracker se resetea al inicio de cada intento y se limpia al terminar `generate`. Tradeoff: un fallo post-herramienta de un tool de solo lectura tampoco se reintenta — conservador a propósito.
- **Cada intento reconstruye el spec** (user + advisors + system prompts) en vez de reusar el objeto: evita acumular efectos de advisor entre intentos. Costo: 2s extra por fallo en el peor caso, bloqueando el hilo único de Telegram — tradeoff aceptado, similar a la consolidación WINDOW.

### Coverage at implementation

`AIServiceTest` extended (16 total; 9 nuevos: retry con `OpenAIInvalidDataException`, retry con `OpenAIRetryableException`, retry con HTTP 5xx, sin retry con HTTP 4xx, límite de intentos, sin retry para errores no transitorios, sin retry si una herramienta ya se ejecutó, limpieza del tracker tras éxito, wrapper de tracking). `ToolCallTracker` 100%, wrapper 100%. Business packages all ≥80%.

---

## What's Next (post-MVP)

Directional candidates, aligned with the roadmap. **Do not start any of these without confirming scope and approach with the user** — several involve real design decisions (state machines, new domain concepts, data migration).

- **Phase 2 — active-work tracking** (most likely next): model a current focus/active task with intended duration and check-ins; add a follow-up loop (progress asks, pause/resume/postpone) and explicit state transitions; make nudging frequency and quiet hours configurable. Existing scheduler and dispatcher infrastructure is the delivery backbone; `Reminder` would need an explicit state model (planned/active/paused/postponed/completed/cancelled/missed).
- **Persistence hardening:** H2 → PostgreSQL/MySQL + Flyway/Liquibase. Currently listed as out of scope; revisit when real deployment or persistent data is needed.
- **Channel expansion:** second Telegram chat or REST-driven creation from a future web UI — the `NotificationDestination` provider is the designed extension point.
- **Phase 3 — semantic memory and planning:** episodic memory (shared conversation + hierarchical consolidation) is done; the remaining slice is semantic memory with provenance, correction, and deletion semantics from day one, plus using memory for planning and wording. Only after active-work tracking stabilizes.

---

## Build & Run

```bash
# Build everything (compile + tests + spotbugs main+test + JaCoCo report)
./gradlew build

# Run the application
./gradlew bootRun

# Run tests only
./gradlew test

# Clean build artifacts
./gradlew clean

# Run spotbugs only (main, then test)
./gradlew spotbugsMain
./gradlew spotbugsTest

# Coverage report (HTML + XML under build/reports/jacoco)
./gradlew jacocoTestReport
```

### Required environment variables

- `TELEGRAM_BOT_TOKEN` — Telegram bot token from BotFather.
- `TELEGRAM_NOTIFICATION_CHAT_ID` — destination chat for outbound notifications (resolved by `NotificationDestination` from `telegram.notification.chat-id`). Must be set; the app fails fast at startup if missing.
- `SPRING_AI_OPENAI_API_KEY` — Mistral API key. (`spring.ai.openai.base-url` is hardcoded to `https://api.mistral.ai/v1` and `spring.ai.openai.chat.options.model` to `mistral-small-latest`; if these change, both belong in properties, not env.)
- For local dev, a `.env` file at the project root is auto-imported (`spring.config.import=optional:file:.env[.properties]`). Use `KEY=value` lines; do **not** commit the file (it is git-ignored).

### Lint / typecheck

There is no separate lint or typecheck step beyond `./gradlew build`; no Checkstyle or SonarQube. Spotbugs runs as part of the build (main + test) — a build with spotbugs findings is a red build.

---

## Code Conventions

- **Identifiers** (class, method, field, package, parameter): **English**.
- **User-facing strings, AI tool descriptions, comments**: **Spanish**. This matches the `ReminderAITool` descriptions and product intent.
- **Lombok**: use `@Data` on entities; consider expanding to services for getters/loggers. Do not use Lombok `@Builder` until a use case demands it.
- **Dependency injection**: **constructor injection everywhere**. `@Value` only for scalar config values (bot token, chat id), never for bean references. See the injected constructors in `AIService`, `ReminderService`, `ReminderAITool`, and `TelegramNotificationDispatcher` as the model.
- **DTOs**: Java `record`, kept separate from JPA entities. Validate with Jakarta annotations. Do not let `@Embeddable` types leak into the API contract.
- **Validation**: `@NotBlank` on required strings, `@Future` on future-only timestamps, `@Valid` on controller request bodies; deterministic parameter checks in `@Tool` methods; cross-field rules centralized in the service (`validateRecurrence`).
- **Mappers**: prefer MapStruct (already on classpath) over manual mapping in services.
- **Error handling**: business errors throw a domain exception; let `@ControllerAdvice` translate. Do not return `null` to signal failure (the one exception is `NextDateCalculator.next` returning `null` for "one-shot → delete").
- **Logging**: SLF4J via `@Slf4j`. No `System.out.println` in committed code.

---

## Known Gotchas

- **Spring Boot 4.x renamed `-web` to `-webmvc`**. If a tutorial suggests `spring-boot-starter-web`, it is for Boot 3.x. Use `spring-boot-starter-webmvc`.
- **Spring AI 2.0.0** has API differences vs 1.x. The Mistral integration works via OpenAI-compat mode; do not assume Anthropic/Gemini-native starters behave the same.
- **The LLM provider is not pinned to a specific vendor** — `spring-ai-starter-model-openai` is being used as a transport. The project is currently pointed at Mistral; the commented-out lines in `application.properties` show it was on Gemini before. Treat LLM provider as a configuration concern, not a code concern.
- **MapStruct + Lombok.** Both annotation processors share the `annotationProcessor` configuration; MapStruct 1.6 has built-in Lombok support. The generated impl uses `Reminder`'s 7-arg constructor, which is written by hand (Lombok's `@AllArgsConstructor` was replaced during the MVP because it null-checked `creationDate` and broke the update mapping). If a new `@Mapper` fails to compile, check that the annotation processor is on the `annotationProcessor` configuration.
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
3. **Write or update tests** alongside any non-trivial change. Keep business-package line coverage at or above the 80% target; new branches in the scheduler/composer fallback chains need explicit tests.
4. **Run `./gradlew build`** again after the change to confirm compilation, tests, spotbugs, and the JaCoCo report.
5. **Do not commit** unless the user explicitly asks. Use the imperative mood in commit messages; reference the roadmap phase or the MVP Completion Record item when one applies.

If the task maps to a roadmap phase (see "What's Next"), do not implement it without first confirming priority and approach with the user — those items have non-obvious design decisions (e.g. what an explicit task state machine looks like, or what `chatId` means when reminders are created from the web UI instead of Telegram).

