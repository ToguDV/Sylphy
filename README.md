# Sylphy — Personal Virtual Assistant (Telegram)

Sylphy is a **personal virtual assistant** that lives in Telegram and helps you
organize tasks and reminders by chatting in natural Spanish. It is not just a
reminder bot: it keeps a persistent conversation memory, uses an LLM with
function calling to manage your reminders, delivers scheduled notifications,
and lets you customize its personality at runtime.

This repository is the **backend and Telegram adapter** for that assistant.
The domain and application services are channel-independent, so other
interfaces (REST, web, mobile) can reuse the same core later.

---

## Table of contents

- [What it does](#what-it-does)
- [Tech stack](#tech-stack)
- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Environment setup (`env` / `.env.example`)](#environment-setup)
- [System prompt (`system-prompt.example.txt`)](#system-prompt)
- [Application properties](#application-properties)
- [REST API](#rest-api)
- [Build & test](#build--test)
- [Project structure](#project-structure)

---

## What it does

- **Conversational task management.** Chat with Sylphy in Telegram to create,
  inspect, update, postpone, complete, and cancel tasks and reminders in
  natural language. The LLM translates your message into deterministic tool
  calls that write to the domain model.
- **One-shot and recurring notifications.** Reminders can fire once or repeat
  with an interval (`MINUTELY`, `HOURLY`, `DAILY`, `WEEKLY`, `MONTHLY`,
  `YEARLY`), a recurrence count, and correct handling of short months
  (e.g. a monthly reminder on the 31st fires on the 28th/29th of February).
- **Reliable scheduler.** A background loop finds due reminders, dispatches
  the notification to Telegram, and only then advances the recurrence. Failed
  deliveries are retried on the next tick, never skipped.
- **Composed notifications.** At fire time the assistant writes a fresh
  notification text (with a guaranteed fallback chain) so the message feels
  alive instead of templated.
- **Persistent conversation memory.** Messages are stored and consolidated
  hierarchically (window → daily → weekly → monthly → annual summaries),
  keeping a bounded, coherent thread across restarts.
- **Configurable personality.** The system prompt is stored in the database
  and can be changed via REST or by chatting with Sylphy itself.
- **REST API.** The same capabilities are exposed at `/api/reminders` and
  `/api/system-prompt`, with Swagger UI for exploration.
- **Provider resilience.** Transient LLM-provider errors are retried
  automatically, with a guard that never re-executes a tool that already had
  side effects.

The user-facing language of the assistant is **Spanish** (product text, tool
descriptions, prompt). Source code identifiers are in English.

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.0 (WebMVC) |
| AI | Spring AI 2.0.0 (OpenAI-compatible endpoint, function calling) |
| Telegram | `telegrambots-springboot-longpolling-starter` 10.0.0 (long-polling) |
| Persistence | Spring Data JPA + H2 (in-memory) |
| Validation | Jakarta Bean Validation |
| Mapping | MapStruct |
| API docs | springdoc-openapi (Swagger UI) |
| Static analysis | Spotbugs |
| Tests | JUnit 5 + JaCoCo coverage |

> **Note:** the database is in-memory H2 — every restart wipes the data. This
> is acceptable for development; a persistent database is future work.

---

## Requirements

- **Java 25** (JDK toolchain pinned in `build.gradle`).
- A **Telegram bot token** from [@BotFather](https://t.me/BotFather).
- An **API key** for an OpenAI-compatible LLM endpoint (e.g. Mistral, or any
  provider exposing an OpenAI-compatible REST API).
- The **chat ID** of the Telegram chat where notifications should be delivered.

---

## Quick start

```bash
# 1. Clone the repository
git clone <repo-url>
cd Sylphy

# 2. Create your local environment file
cp .env.example .env
#    Edit .env and fill in your real token, chat id, and API key.
#    (The .example files must have the .example suffix removed — see below.)

# 3. (Optional) Create your system prompt file
cp system-prompt.example.txt system-prompt.txt
#    Edit it to shape the assistant's personality.

# 4. Build (compiles, runs tests, Spotbugs, and coverage)
./gradlew build

# 5. Run the bot
./gradlew bootRun
```

The bot starts polling Telegram. Message it and Sylphy will respond. Scheduled
notifications are pushed to the chat configured in `TELEGRAM_NOTIFICATION_CHAT_ID`.

---

## Environment setup

Sylphy reads its secrets from environment variables. For local development, a
`.env` file at the project root is auto-imported at startup
(`spring.config.import=optional:file:.env[.properties]` in
`application.properties`).

### `.env.example` → `.env`

The repository ships a template at **`.env.example`**. To use it:

1. Copy the template: `cp .env.example .env`
2. Fill in your real values.
3. **Rename the file by removing the `.example` suffix** — the application
   only reads `.env`, not `.env.example`.

> Same rule applies to `system-prompt.example.txt` → `system-prompt.txt`
> (see [System prompt](#system-prompt)).

`.env` is git-ignored and must **never** be committed.

### Variables

| Variable | Required | Description |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | Yes | Bot token from @BotFather (format `1234567890:AAH...`). |
| `TELEGRAM_NOTIFICATION_CHAT_ID` | Yes | Chat ID that receives notifications. The app fails fast at startup if missing. |
| `SPRING_AI_OPENAI_API_KEY` | Yes | API key for the OpenAI-compatible LLM endpoint. |

These map to the placeholders `${TELEGRAM_BOT_TOKEN}`, etc. in
`application.properties` (see below).

---

## System prompt

Sylphy's personality is defined by a **system prompt**. The active prompt is
resolved in this order:

1. The prompt **stored in the database** (set via `PUT /api/system-prompt` or
   by chatting with Sylphy); it wins until reset.
2. The contents of the **`system-prompt.txt`** file at the project root.
3. **No prompt** if the file is missing or blank.

The repository ships a template at **`system-prompt.example.txt`**. To use it:

```bash
cp system-prompt.example.txt system-prompt.txt
```

and **remove the `.example` suffix** — the application looks for
`system-prompt.txt` only. Edit the file to give your assistant its
personality, tone, rules, and quirks (the template contains a working Spanish
starter). The file is re-read on every LLM call, so edits take effect
immediately while the bot runs.

`system-prompt.txt` is git-ignored so your personal prompt stays out of the
repository.

---

## Application properties

All application configuration lives in `src/main/resources/application.properties`.
Secrets come from environment variables; everything else is tuned there.
The most relevant settings:

| Property | Default | Description |
|---|---|---|
| `telegram.bot.name` | `SylphyBot` | Display name of the bot. |
| `telegram.bot.token` | `${TELEGRAM_BOT_TOKEN:}` | Bot token from env. |
| `telegram.notification.chat-id` | `${TELEGRAM_NOTIFICATION_CHAT_ID:}` | Notification destination chat from env. |
| `spring.ai.openai.base-url` | `https://opencode.ai/zen/go/v1` | LLM endpoint (OpenAI-compatible). |
| `spring.ai.openai.api-key` | `${SPRING_AI_OPENAI_API_KEY:}` | LLM API key from env. |
| `spring.ai.openai.chat.options.model` | `deepseek-v4-flash` | LLM model to use. |
| `sylphy.scheduler.tick-millis` | `60000` | How often the scheduler checks for due reminders. |
| `sylphy.conversation.id` | `owner-1` | ID of the shared conversation (all channels share one thread). |
| `sylphy.system-prompt.file` | `system-prompt.txt` | File used as the fallback system prompt. |
| `sylphy.ai.retry.max-attempts` | `3` | LLM call retries on transient provider errors. |
| `sylphy.ai.retry.delay-ms` | `2000` | Delay between retry attempts. |
| `sylphy.chat.history.window` | `40` | Raw messages kept before consolidation into a summary. |
| `sylphy.chat.memory.cron.daily` | `0 0 2 * * *` | Cron for daily memory consolidation. |
| `sylphy.chat.memory.cron.weekly` | `0 0 3 * * MON` | Cron for weekly memory consolidation. |
| `sylphy.chat.memory.cron.monthly` | `0 0 4 1 * *` | Cron for monthly memory consolidation. |
| `sylphy.chat.memory.cron.annual` | `0 0 5 1 1 *` | Cron for annual memory consolidation. |
| `spring.config.import` | `optional:file:.env[.properties]` | Auto-imports `.env` for local development. |

To switch LLM provider or model, change the base URL and model here (or the
API key in `.env`). The provider is treated as a configuration concern, not a
code concern.

---

## REST API

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/reminders` | List all reminders. |
| `GET` | `/api/reminders/{id}` | Get a reminder by id (404 if missing). |
| `POST` | `/api/reminders` | Create a reminder (201). |
| `PUT` | `/api/reminders/{id}` | Full replace of a reminder (`creationDate` preserved). |
| `DELETE` | `/api/reminders/{id}` | Delete a reminder (204). |
| `GET` | `/api/system-prompt` | Get the active system prompt. |
| `PUT` | `/api/system-prompt` | Update the stored system prompt. |
| `DELETE` | `/api/system-prompt` | Reset to the file/default prompt (204). |

Interactive documentation: [Swagger UI](http://localhost:8080/swagger-ui.html)
and the OpenAPI spec at `/v3/api-docs`. Errors are returned as RFC 7807
`ProblemDetail` responses.

---

## Build & test

```bash
./gradlew build            # compile + tests + Spotbugs (main + test) + JaCoCo report
./gradlew test             # run tests only
./gradlew bootRun          # run the application
./gradlew clean            # clean build artifacts
./gradlew spotbugsMain     # Spotbugs on main sources
./gradlew spotbugsTest     # Spotbugs on test sources
./gradlew jacocoTestReport # coverage report (HTML + XML under build/reports/jacoco)
```

Coverage is enforced at ≥80% line coverage for business packages.

---

## Project structure

```
src/main/java/com/togudv/sylphy
├── SylphyApplication        # Entry point (@EnableScheduling)
├── config/                  # Notification destination + conversation id providers
├── controller/              # REST endpoints + GlobalExceptionHandler (RFC 7807)
├── service/                 # Business logic
│   ├── notification/        # Notification dispatcher SPI + Telegram impl
│   └── tools/               # Spring AI @Tool implementations exposed to the LLM
├── repository/              # Spring Data JPA
├── mapper/                  # MapStruct DTO <-> entity mappers
├── dto/                     # API contract types (Java records)
├── model/                   # JPA entities + @Embeddable value objects
└── integrations/
    └── telegram/            # Telegram transport and presentation adapter
```
