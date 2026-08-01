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
| Mapping | MapStruct 1.6.3 | Declared on classpath but **no `@Mapper` interfaces exist yet**. |
| Test | JUnit 5 (Jupiter) | `useJUnitPlatform()` in `build.gradle:54`. |

---

## Target Architecture

### Layered structure (existing + planned)

```
com.togudv.sylphy
├── controller/         # REST endpoints for future clients and automation
├── service/            # Business logic
│   └── tools/          # Spring AI @Tool implementations exposed to the LLM
├── repository/         # Spring Data JPA
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

- `./gradlew compileJava` succeeds; the project **compiles** end-to-end.
- Gradle build configuration is consistent; wrapper resolves.
- `Reminder` + `RecurrentConfig` + `Frequency` form a coherent aggregate.
- `ReminderRepository extends CrudRepository<Reminder, Long>` is valid.
- `ReminderService.updateById` properly loads the existing entity, applies the patch, and saves; throws `NoSuchElementException` when the id is missing.
- `ReminderAITool.createReminder` constructs `Reminder` with the matching 6-arg ctor (`id=null, recurrentConfig=null`).
- Telegram bot message loop is wired: text → `AIService.generate` → reply.
- `AIService` builds a `ChatClient` with tools correctly.
- `application.properties` is syntactically valid; Mistral config is active.
- **Secrets are externalized.** `telegram.bot.token` and `spring.ai.openai.api-key` resolve from env vars (`TELEGRAM_BOT_TOKEN`, `SPRING_AI_OPENAI_API_KEY`) with empty defaults — nothing sensitive is hardcoded. A `.env` file at the project root is picked up via `spring.config.import=optional:file:.env[.properties]`. `TelegramBotHandler` injects the token with `@Value("${telegram.bot.token:}")` (constructor-injected).

### ❌ Still broken — fix before any feature work

- `ReminderController` `@GetMapping("/ ")` — path contains a stray space; the endpoint is effectively dead.
- `TelegramBotHandler.consume` (`TelegramBotHandler.java:46-71`) — still uses `System.out.println` for log lines and an `e.printStackTrace()` for the catch block. Cosmetic, but it conflicts with the SLF4J convention and blocks TODO item #10.
- **Historical secret leak (informational, not a code fix).** The initial commit (`0ee3e35`) hardcoded real Telegram bot and Mistral API keys. The keys were rotated; do not reintroduce secrets into source or configuration committed to git.

### 📋 Missing (priority-ordered TODO for MVP)

0. ~~Arreglar problemas encontrados con spotbugs~~ ✅ Done. CT closed by making `TelegramNotificationDestination` `final`; EI/EI2 on `CreateReminderDTO` closed by introducing `RecurrentConfigDTO` (JPA @Embeddable no longer leaks into the API contract); EI2 on `ReminderAITool` closed via `@SuppressFBWarnings` with justification. `./gradlew spotbugsMain` and `spotbugsTest` are green.
1. ~~Wire `NotificationDestination` provider (constructor-injected, reads `telegram.notification.chat-id`) and inject it into the dispatcher. The entity stays free of per-channel fields.~~ ✅ Provider done (`config/NotificationDestination` + `TelegramNotificationDestination`, constructor-injected, fails fast on missing `chat-id`). The "inject into dispatcher" half lands with TODO #3.
2. ~~Implement scheduler: `@EnableScheduling` + logic to read `RecurrentConfig`, compute next fire time, update `nextDate`, and trigger notification.~~ ✅ Done end-to-end. `service/NextDateCalculator` (pure function `next(Reminder)`, anchor + N units, throws on missing `frequencyType` or invalid `interval`, returns `null` to signal "one-shot → delete"); `service/ReminderScheduler` (`@Scheduled(fixedDelayString="${sylphy.scheduler.tick-millis:60000}")`); `service/notification/NotificationDispatcher` interface + `TelegramNotificationDispatcher` (real `SendMessage` via `TelegramClient.execute`, errors wrapped in `NotificationDeliveryException`); `integrations/telegram/TelegramConfig` provides the `TelegramClient` bean and `TelegramBotHandler` was refactored to inject it; `ReminderService.advanceAfterFire(id)` does the "compute next or delete" step inside a transaction; `ReminderRepository.findByNextDateLessThanEqual(now)` derived query; `SylphyApplication` enables scheduling and dropped three dead imports; `application.properties` adds `sylphy.scheduler.tick-millis=60000`. Test coverage: 5 in `ReminderSchedulerTest`, 11 in `NextDateCalculatorTest`, 3 in `TelegramNotificationDispatcherTest`. `./gradlew build` (compile + test + spotbugs main+test) green. Side-effect refactors: `RecurrentConfig.frequencyType` (and the DTO field) changed from `Set<Frequency>` to `Frequency` (the set was incoherent with the calculator's strict semantics); `daysOfWeek` / `daysOfMonth` removed from `RecurrentConfig` and the DTO (the chosen `anchor + N units` algorithm does not consume them — keeping them would have been a dead-field smell; if multi-day semantics are needed later, that is a separate design + a new `NextDateCalculator` algorithm).
3. Implement notification dispatcher (Telegram client push from a scheduled task). Will consume `NotificationDestination` (TODO #1) at that point. ✅ Done as a side-effect of TODO #2: `TelegramNotificationDispatcher` does the real `SendMessage` push and reads `NotificationDestination` for the chat id.
4. Wire `createReminder` AI tool to populate `RecurrentConfig` correctly (the `isRecurrent` boolean is currently dropped; the `RecurrentConfig` argument is passed as `null`).
5. Implement real CRUD endpoints in `ReminderController` (GET list, GET by id, POST, PUT, DELETE).
6. Add a DTO ↔ entity mapper. MapStruct is already on the classpath — write the `@Mapper` interface.
7. ~~Stop leaking the JPA `RecurrentConfig` through `CreateReminderDTO`; create a parallel `RecurrentConfigDTO`.~~ ✅ Done. `dto/RecurrentConfigDTO` is a record; `CreateReminderDTO.recurrentConfig` is now typed as the DTO. (The compact-constructor `Set.copyOf` was dropped together with the `daysOfWeek` / `daysOfMonth` removal in TODO #2.)
8. Add `@ControllerAdvice` for RFC 7807 problem-details error responses.
9. Add `@Valid` on every controller request body and parameter validation on `@Tool` methods.
10. Replace `System.out.println` with SLF4J (`@Slf4j`).
11. Add tests. Project has 0% business-logic coverage. *(Initial seed: `TelegramNotificationDestinationTest` covers the chat-id validation contract. TODO #2 added 19 more: `ReminderSchedulerTest`, `NextDateCalculatorTest`, `TelegramNotificationDispatcherTest`.)*
12. Add OpenAPI/springdoc when the REST surface stabilizes.
13. Make `RecurrentConfig.occurrences` cap the total number of fires of a recurring reminder. Today the field exists in the model but is read by nothing: a recurrent reminder with `occurrences = N` keeps firing forever. The next `nextDate` is already computed by `NextDateCalculator`; the missing piece is the "have we used all our shots?" check. Implementation sketch: in `ReminderService.advanceAfterFire(id)`, if `recurrentConfig.occurrences != null`, decrement it on each fire; when it would reach `0` (i.e. before the last fire decrements it, or after — pick one and document), do not compute a next `nextDate` and `repository.delete(r)` instead. `occurrences == null` keeps the current "infinite" behaviour. Validate at the boundary (creation in `ReminderService.create` or in the AI tool TODO #4) that `occurrences`, if set, is `>= 1`. Add tests covering: `occurrences = 1` (fires once, deleted), `occurrences = N` (fires exactly N times), `occurrences = null` (infinite — current behaviour), and the validation rejection.
14. Configure JaCoCo and bring test coverage up. Today the project has no coverage tool wired — the "0% business-logic coverage" claim in TODO #11 is qualitative. (a) Add the `jacoco` plugin to `build.gradle` and wire `jacocoTestReport` to produce an HTML report under `build/reports/jacoco`. Decide whether to fold it into `./gradlew check` or document it as a separate `./gradlew jacocoTestReport` task. (b) Identify gaps before adding tests — at minimum: `ReminderService` CRUD (`create`, `getAll`, `updateById`, `deleteById`) has no direct tests, `ReminderAITool` is uncovered, `AIService` is uncovered, `TelegramBotHandler.consume` is uncovered. (c) Add tests for those, targeting a high bar (≥80% line coverage on the `service/`, `service/notification/`, and `service/tools/` packages). (d) Treat any uncovered branch as either a tested edge case or a documented decision in AGENTS.md — no silent "we didn't bother".
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
- **MapStruct is configured but unused.** The `mapstruct-processor` annotation processor is wired in `build.gradle` but no `@Mapper` interface exists in the source tree. If you write one, it should compile out of the box; if it does not, check that the annotation processor is on the `annotationProcessor` configuration.
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
