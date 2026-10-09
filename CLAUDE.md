# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A customer support system where customers interact solely via email: inbound emails become tickets (via the Resend webhook), an AI agent auto-categorizes and attempts to auto-respond from a knowledge base, and a single admin escalates/resolves whatever the AI can't handle from a dashboard UI. Full requirements and MVP scope live in `PROJECT_PLAN.md`; the intended tech stack and what's explicitly out of scope live in `TECH_STACK.md`. Read both before making product/architecture decisions — don't re-derive scope from code alone.

**Current state**: scaffolding, a `/api/health` check, and full session-based auth (login page + protected dashboard shell) exist end-to-end, with one reference test per layer (unit/integration/e2e — see Testing below). The backend ticket-management core (data model + `/api/tickets` REST API, see Tickets below) exists, and email is integrated both ways through **Resend** (see Email below): inbound via Resend's signed `email.received` webhook (reached through the ngrok tunnel), outbound via `smtp.resend.com` with threading headers. SendGrid (declined the account) and IONOS IMAP support were removed. Locally, replies go to Mailpit. The frontend has a Tickets list (`pages/TicketsPage.jsx`, filters kept in the URL query string) and a ticket detail/thread page (`pages/TicketDetailPage.jsx`, reply + status controls) backed by `api/tickets.js`; shared ticket badges are `components/TicketBadges.jsx`, labels/date helpers `utils/ticketLabels.js`. Locally, `DemoTicketSeeder` fills an empty DB with sample tickets (`app.demo-data.enabled`). The Home route (`/dashboard/home`) is the metrics dashboard (`pages/DashboardPage.jsx`, lazy-loaded so Recharts stays out of the main bundle) backed by `GET /api/dashboard/metrics`. AI: new tickets are auto-categorized by Gemini via Spring AI (see AI categorization below) and then auto-answered from the knowledge base `backend/src/main/resources/knowledge-base-short.md` or escalated to the admin (see AI auto-reply below).

## Branches: `main` = local, `deploy` = Render

- **`main`** is the local setup (Docker Postgres + Mailpit, ngrok tunnel, SMTP). The user wants it kept free of deployment changes: don't add Render files or prod-only code there.
- **`deploy`** = `main` + the Render deployment (`docs/DEPLOY_RENDER.md`): `Dockerfile` (node build → frontend `dist` copied into `static/` → Maven jar → JRE with 512 MB / 0.1 CPU JVM flags), `.dockerignore`, `render.yaml` (Blueprint: free Postgres + free Docker web service, `branch: deploy`, autodeploy, secrets `sync: false`), `application-prod.properties` (Render sets `SPRING_PROFILES_ACTIVE=prod`: `PORT`, DB from `DB_HOST/PORT/NAME/USER/PASSWORD`, secure cookie + forwarded headers, CORS = `PUBLIC_APP_URL`, `app.email.outbound=resend-api`, demo data off, reserved recipients rejected, `APP_ADMIN_PASSWORD` required with no fallback), `service/ResendApiEmailSender` + `ResendClient.sendEmail` (`POST /emails`, threading + `Auto-Submitted` as custom headers; `RestClientException` → `MailSendException` so 502/rollback/escalation behave like SMTP), `controller/SpaController` (`@Profile("prod")`, forwards `/`, `/login`, `/dashboard/**` to `index.html`; `PingController`'s placeholder `GET /` text is removed on `deploy` for this), and `SecurityConfig` permits for the static shell (`/index.html`, `/assets/**`, `/favicon.svg`, `/login`, `/dashboard/**`).
- New features: build on `main`, then `git checkout deploy && git merge main && git push` (Render redeploys). Keep deploy-only changes in new files or the `prod` profile to keep merges clean.
- Why: Render's free tier blocks outbound SMTP (25/465/587) → Resend API; free Postgres expires after 30 days (user accepted, monthly recreate); the service sleeps after 15 min (Resend retries the webhook). Same-origin serving keeps the `SameSite=Lax` session cookie first-party.
- A new frontend route must be added to `SpaController` and the `SecurityConfig` static permits on `deploy`, or refreshing it 404s/401s there.

## Repo layout

- `backend/` — Spring Boot (Java 21, Maven)
- `frontend/` — React + Vite, plain JavaScript (not TypeScript — was deliberately converted from the `react-ts` template; keep new code in `.jsx`/`.js`, no `tsconfig`/type annotations)
- `docker-compose.yml` — Postgres + Mailpit (local fake SMTP; inbox UI at http://localhost:8025) for local dev
- `docs/EMAIL_SETUP.md` — user-facing guide: the Resend setup (a `support.` subdomain of your own domain), running it, troubleshooting
- `scripts/run-backend-resend.sh <address>` — starts the backend wired to Resend (prompts for API key, webhook signing secret, an optional Gemini API key (empty = AI off) and a required admin password of 12+ characters, typed twice; turns off demo seeding and turns on `app.email.reject-reserved-recipients`; needs `scripts/start-tunnel.sh` running)
- `scripts/start-tunnel.sh` — ngrok tunnel (`NGROK_URL` = the user's free static ngrok domain, exported in their shell profile; deliberately not in the repo, which may go public) to the **frontend preview server :4173** (`npm run start:public` = build + `vite preview`, serves only `dist/`) by default, so one public URL serves the UI *and*, via the `/api` proxy (preview inherits `server.proxy`/`server.allowedHosts`), the Resend webhook (`TUNNEL_PORT=8080` = backend only). Don't tunnel the dev server (5173): it serves the whole `frontend/` tree, incl. `e2e/` with the dev password. `server.allowedHosts` allows `.ngrok-free.dev`/`.ngrok-free.app` — Vite rejects unknown Host headers otherwise. ngrok is installed via Homebrew; the authtoken is per-user (`ngrok config add-authtoken`).

### Backend package structure (`com.customersupport.backend`)

Layered, not feature-based. Put new classes in the matching layer package rather than creating feature packages:

- `config/` — Spring configuration (`SecurityConfig`, `WebConfig`), `@ConfigurationProperties` records, startup runners (`DemoTicketSeeder`)
- `controller/` — `@RestController`s only; thin, delegate to services
- `dto/` — request/response records and other plain value types (`OutboundEmail`); no JPA annotations
- `entity/` — JPA `@Entity` classes only
- `enums/` — all enums (`TicketStatus`, `TicketCategory`, `MessageAuthor`), including the ones entities persist
- `repository/` — Spring Data repositories
- `service/` — business logic and external-system abstractions (`TicketService`, `EmailSender` + its `LoggingEmailSender` impl)
- `exception/` — custom exceptions and the single `@RestControllerAdvice` (`GlobalExceptionHandler`)

Tests mirror the same packages (e.g. `controller/TicketIntegrationTest`, `service/TicketServiceTest`).

### Frontend structure (`frontend/src`)

`api/` (axios modules, one per backend resource), `components/` (reusable UI incl. `TicketBadges.jsx`), `context/` (React context providers), `pages/` (route-level components), `utils/` (pure helpers/constants, e.g. `ticketLabels.js`), `test/` (Vitest setup).

## Commands

### Backend (`backend/`)
- Run: `./mvnw spring-boot:run` (port 8080)
- Test: `./mvnw test` — single test: `./mvnw test -Dtest=ClassName#methodName`. Runs against an in-memory H2 DB (see Testing below), not Postgres — no need to `docker compose up -d` first.
- Compile only (fast sanity check without a DB): `./mvnw compile`
- Package: `./mvnw clean package -DskipTests` → `target/backend-0.0.1-SNAPSHOT.jar`

### Frontend (`frontend/`)
- Install: `npm install`
- Dev server: `npm run dev` (port 5173) — proxies `/api/**` to `http://localhost:8080` (see `vite.config.js`), so API calls use relative paths and never hit the browser's CORS/cross-origin cookie rules in dev
- Build: `npm run build`
- Lint: `npm run lint` (oxlint)
- Unit tests: `npm test` (Vitest) — no backend needed
- E2E tests: `npm run test:e2e` (Playwright) — needs the full stack already running (see Testing below)

### Postgres
- Start: `docker compose up -d` (from repo root)
- **Runs on host port 5433, not 5432** — this machine already runs a native Postgres instance on 5432 for another project, so the mapping in `docker-compose.yml` was moved to `5433:5432` to avoid colliding with it. `backend/src/main/resources/application.properties` points at `jdbc:postgresql://localhost:5433/customer_support`. If Postgres connection fails with a role/auth error, check for a port collision here first before assuming credentials are wrong.
- If `docker` isn't on `PATH` in a shell, it's likely still callable at `/Applications/Docker.app/Contents/Resources/bin/docker` (Docker Desktop CLI symlinks aren't always installed on this machine).

## Testing

One test per layer exists as a reference pattern (unit, integration, e2e) — deliberately not a full suite. Follow these same patterns when adding more rather than introducing a new style per test.

- **Backend unit** — `backend/.../controller/AuthControllerTest.java`: `AuthController` constructed directly with a Mockito-mocked `AuthenticationManager`, no Spring context, no DB.
- **Backend integration** — `backend/.../controller/AuthIntegrationTest.java`: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`, exercises the real `SecurityConfig`+`AuthController` wiring end-to-end (login → session cookie → `/me` → logout → `/me` 401). Captures the `MockHttpSession` from the login `MvcResult` and reuses it on later calls via `.session(...)` rather than chaining cookie headers.
- **Frontend unit** — `frontend/src/components/ProtectedRoute.test.jsx`: Vitest + Testing Library, `useAuth` mocked via `vi.mock`, rendered inside a `MemoryRouter`.
- **E2E** — `frontend/e2e/auth.spec.js`: Playwright, against the real running stack. `playwright.config.js` has no `webServer` auto-start block — it assumes `docker compose up -d`, the backend, and `npm run dev` are already running, same as local dev always is in this project.

Non-obvious gotchas worth knowing before writing more tests (confirmed via actual dependency-tree/jar inspection, not assumption, since this stack is new enough that older muscle memory is wrong here):

- Spring Boot 4.1 moved `@AutoConfigureMockMvc` to `org.springframework.boot.webmvc.test.autoconfigure` — not the Boot 2/3 `org.springframework.boot.test.autoconfigure.web.servlet` package.
- Jackson is now Jackson 3 (`tools.jackson.databind.ObjectMapper`), not classic `com.fasterxml.jackson.databind.ObjectMapper`. For a small test request body, a raw JSON string literal sidesteps the ambiguity entirely rather than autowiring an `ObjectMapper`.
- JUnit 5, Mockito (`MockitoExtension`), AssertJ, MockMvc, and `spring-security-test` are all already on the test classpath transitively via the `-test` starters already in `pom.xml` (Boot 4.1's per-starter `-test` artifact convention) — no new backend test dependencies needed for more unit/MockMvc-style tests.
- The one real gap: **no embedded DB is pulled in automatically**. `spring-boot-starter-data-jpa` is on the main classpath even with zero `@Entity` classes, so Hibernate still tries to resolve a DB dialect at context startup — any `@SpringBootTest` needs `@ActiveProfiles("test")` (→ `backend/src/test/resources/application-test.properties`, H2 in-memory) or it'll require real Postgres. This is why `com.h2database:h2` (test scope) was added to `pom.xml`.
- `frontend/vite.config.js`'s `test` block explicitly excludes `e2e/**` — without that, Vitest would also try to run the Playwright spec with the wrong test runner/globals.
- Playwright needs a one-time `npx playwright install chromium` before `npm run test:e2e` works on a machine that hasn't run it before.

## Architecture notes

### Auth

- Session-based auth via Spring Security (`backend/.../config/SecurityConfig.java`), not JWT/stateless — a single hardcoded admin account (credentials in `application.properties` as `app.admin.username`/`app.admin.password`, loaded via `AdminAccountProperties` and seeded into an `InMemoryUserDetailsManager`, BCrypt-hashed at startup). There's no multi-user/roles model; don't build toward one without being asked.
- Login is a custom JSON endpoint (`POST /api/auth/login` in `AuthController`), not Spring Security's built-in form login: it authenticates via `AuthenticationManager` and manually saves the `SecurityContext` into the HTTP session (`HttpSessionSecurityContextRepository`) — follow this same pattern for any other custom auth-adjacent endpoint. `GET /api/auth/me` and `POST /api/auth/logout` (the latter fully handled by Spring Security's `.logout()` DSL, no controller method needed) round out the auth API.
- `anyRequest().authenticated()` is the default in `SecurityConfig` — new backend endpoints are protected automatically; add to the `permitAll()` matcher list explicitly if one needs to be public.
- CSRF is disabled (see the comment in `SecurityConfig`) — an accepted MVP trade-off. The defense is the session cookie's `SameSite=Lax` + `HttpOnly` (`server.servlet.session.cookie.*` in `application.properties`; keep them) plus CORS locked to the app's origins. Revisit before a real production deployment.
- `service/LoginThrottle`: after 5 failed logins in a row, all logins get 429 for 5 minutes (global, not per IP: there's one account, and client IPs behind ngrok + the Vite proxy come from spoofable forwarded headers). In-memory, so a restart clears it.
- Frontend auth state lives in `frontend/src/context/AuthContext.jsx`, backed by `GET /api/auth/me` on mount. `frontend/src/components/ProtectedRoute.jsx` gates the `/dashboard/*` route tree (see `App.jsx` for the route table) and redirects to `/login` when there's no session.
- `frontend/src/api/axios.js` is the one shared axios instance (`withCredentials: true`) — route all API calls through it (via `frontend/src/api/auth.js` for auth, or a new sibling module for future features) rather than raw `fetch`. Its response interceptor turns any `401` into a global `auth:unauthorized` DOM event; `AuthContext` listens for it and clears `user`, so a session expiring mid-use on *any* future authenticated call bounces the user back to `/login` automatically — don't re-implement 401 handling per-component.

### Tickets

- `Ticket` (1) → (*) `TicketMessage` (the email thread; `author` is `CUSTOMER`/`AI`/`ADMIN`). `TicketStatus`/`TicketCategory` match `PROJECT_PLAN.md`; `category` is null until AI categorizes. `firstResponseAt` (first non-customer message) and `resolvedAt` are stamped by the entity's `addMessage`/`changeStatus` and exist for the future metrics — mutate tickets through those methods, not raw setters, so the timestamps stay correct.
- `TicketService` is the only write path, and it maps to DTO records (`dto/`) inside its transaction. `PATCH /api/tickets/{id}/category` (`updateCategory`) lets the admin set/correct the category (category `<select>` on the ticket page). `openTicket(...)` is the entry point the inbound webhook should call. Admins may only set `PENDING_HUMAN`/`IN_PROGRESS`/`RESOLVED`; `OPEN`/`AI_RESPONDED` are system-only (400 otherwise). An admin reply moves the ticket to `IN_PROGRESS` unless it's `RESOLVED`.
- API: `GET /api/tickets?status=&category=&page=&size=` (own `PageResponse` shape, newest activity first, size capped at 100), `GET /api/tickets/{id}`, `PATCH /api/tickets/{id}/status`, `PATCH /api/tickets/{id}/category`, `POST /api/tickets/{id}/replies`. Errors go through `exception/GlobalExceptionHandler` as `{"error": "..."}`. Add new exception mappings there rather than per-controller.
- Outbound email goes through `service/EmailSender` (see Email below). The send happens inside the reply transaction, so a send failure rolls the reply back (`MailException` → 502 via `GlobalExceptionHandler`).
- `Ticket.createdAt`/`updatedAt` are `LocalDateTime`, generated by Hibernate's `@CreationTimestamp`/`@UpdateTimestamp` (JVM default zone) — don't set them by hand. `@UpdateTimestamp` only fires when a ticket column changes, so `addMessage` touches `updatedAt` to make a message-only reply still count as activity. Other timestamps (`firstResponseAt`, `resolvedAt`, `TicketMessage.createdAt`) are still `Instant`s passed in by the caller. The API never exposes the zone-less `LocalDateTime`s: DTOs convert them with `dto/ApiTimestamps.toInstant`, so every timestamp in a response is a UTC instant (`...Z`) — keep it that way for new DTO fields.
- `ddl-auto=update` never changes an existing column's type: a Postgres DB created while these two fields were `Instant` keeps `timestamp with time zone` columns, while a fresh DB gets `timestamp`. Both work (verified against the local DB) — pgjdbc converts using the JVM zone, which is also the zone Hibernate generates them in. Only a JVM zone change would shift them.
- Gotcha: to get the id of a newly added `TicketMessage` on an already-managed `Ticket`, call `ticketRepository.flush()`, not `save`/`saveAndFlush`. `save` merges, and the cascaded merge persists a *copy*, leaving your instance's id null.
- Test gotcha: any unit test that calls `AuthController.login` directly must `SecurityContextHolder.clearContext()` afterward (see `AuthControllerTest`). Otherwise its mock `Authentication` leaks via the thread-local and later MockMvc tests on the same thread get 403s.

### Email

- **Provider: Resend only.** One inbound path: `POST /api/webhooks/resend` (`controller/ResendWebhookController`, `permitAll()`) → `service/ResendWebhookVerifier` → `service/ResendClient` → `service/InboundEmailParser` → `service/InboundEmailService`. Outbound: `service/SmtpEmailSender` over SMTP (`smtp.resend.com`, username `resend`, password = the `re_` key). SendGrid and IONOS/IMAP support were deliberately removed — don't reintroduce provider-specific paths without being asked.
- `ResendWebhookVerifier` checks the Svix signature over the **raw** body (HMAC-SHA256 of `id.timestamp.body` with the base64 part of `whsec_…`, any `v1,` entry, constant-time, ±5 min, fails closed without a secret; unit-tested against Svix's official test vector). Only `email.received` is handled. The event has metadata only, so `ResendClient` (`RestClient.builder()` — Boot 4 here has no auto-configured RestClient.Builder) fetches `GET /emails/receiving/{id}` with the `re_` key (must be **full access**). Status codes steer Resend's retries: 401 bad signature, 502 when the fetch fails (retried), 200 otherwise (incl. deliberately dropped emails).
- `ResendClient` has 5s connect / 10s read timeouts (each fetch holds a webhook thread; Resend gives up on a webhook after ~15s and retries).
- `InboundEmailParser.parse(ResendReceivedEmail)` (pure, no persistence) → `dto/InboundEmail`: sender from the `from` header (keeps the display name) else the top-level field, lower-cased; text body (HTML-only → jsoup plain text), quoted history stripped heuristically (never to empty), attachment note appended (attachments are out of scope), subject/name truncated to the 255-char columns, Message-ID (header, else Resend's `message_id`, else `<{resend email id}@resend.invalid>` so retries still dedupe) + thread IDs (In-Reply-To then References newest-first; Resend's header map is lowercase-keyed, lookups are case-insensitive), automated flag (Auto-Submitted/Precedence, `mailer-daemon@`/`postmaster@`, `multipart/report` bounces).
- `InboundEmailService` decides the outcome — IGNORED (automated, or from our own support address: loop guard), DUPLICATE (Message-ID already stored; Resend retries), APPENDED (matched by thread IDs → a stored `TicketMessage.externalMessageId`, else by the `[#id-TOKEN]` subject tag, where `TOKEN` must equal `Ticket.replyToken` — **and only if the sender is that ticket's `customerEmail`**), or CREATED. From addresses are forgeable and ticket ids sequential, so the random token (8 chars, `SecureRandom`; assigned lazily by `Ticket.replyTag()` for tickets older than the column) is what actually keeps a forger out of an existing ticket. Old `[#id]` tags without a token no longer match. All writes go through `TicketService.openTicket` / `appendCustomerReply` (a reply to RESOLVED/AI_RESPONDED → PENDING_HUMAN).
- `TicketMessage.externalMessageId` holds the RFC 5322 Message-ID incl. angle brackets, unique-indexed (dedupe; NULLs allowed).
- **Outbound threading**: `TicketService.reply` generates `<uuid@support-domain>`, stores it on the ADMIN message, sets In-Reply-To (latest customer email) / References (all thread IDs), subject `Re: <subject> [#id-TOKEN]`. `SmtpEmailSender` overrides `MimeMessage.updateMessageID()` to keep our ID — but Resend may still replace it, so matching customer replies relies on the subject-tag fallback in practice. `app.email.outbound`: `smtp` (default; Mailpit locally) or `log` (`LoggingEmailSender`; tests). `app.email.reject-reserved-recipients` (`EmailProperties.rejectReservedRecipients`, on in the Resend script): `reply` refuses RFC 2606/6761 test domains (`example.com`, `.test`, …) with a 400 (`UndeliverableRecipientException`), since the demo tickets' bounces would hurt the real sending domain's reputation; off locally so demo tickets can be answered into Mailpit.
- Config `app.email.*` (`EmailProperties`: support address/name + `resend.*`) ← env vars `SUPPORT_EMAIL_ADDRESS`, `RESEND_API_KEY`, `RESEND_WEBHOOK_SECRET`, `SMTP_*`, set by `scripts/run-backend-resend.sh` (hidden prompts; never in files). Test profile has fake `re_`/`whsec_` values.
- Gotcha: after adding Maven dependencies, a running `./mvnw spring-boot:run` must be **restarted** — DevTools only reloads classes, so its automatic restart fails with the new jars missing and the process sits there not listening on 8080. Likewise delete stale `.class` files in `target/` when removing classes, or DevTools may load them.

### AI categorization

- Spring AI **2.0.1** (`spring-ai-bom` + `spring-ai-starter-model-google-genai`, Gemini Developer API with an AI Studio key; works on Boot 4.1.1 although 2.0 targets 4.0). Switch: `spring.ai.model.chat=${AI_CHAT_PROVIDER:none}` — `google-genai` = on (needs `GEMINI_API_KEY`, env only), `none` = off; the context starts fine either way, even without a key. `run-backend-resend.sh` sets `none` explicitly when the key prompt is left empty. Test profile: `none`.
- Model `gemini-3.8-flash` (the user's choice: older models like `gemini-2.5-flash` are rejected for new keys). Thinking is off via `thinking-budget=0` — verified working with this model; it **rejects** `thinking-level=MINIMAL` (400 "Thinking level MINIMAL is not supported for this model"), so don't switch to thinking-level without testing against the real API. Thinking tokens would count against `max-output-tokens=1024`. `response-mime-type=application/json`. Property names are the non-deprecated `spring.ai.google.genai.chat.*` (not `chat.options.*`).
- **Synchronous, before the ticket exists**: `InboundEmailService` calls `TicketCategorizer` on the CREATED path and passes the result to `TicketService.openTicket(..., category)`, so a ticket never appears Uncategorized unless the AI is off or failed. `InboundEmailService` is deliberately **not** `@Transactional` (each `TicketService` call is its own transaction), so the AI call doesn't hold a DB connection. It runs while Resend waits for the webhook (~15s before it retries), so `AiTicketCategorizer` enforces its own `TIME_LIMIT` (10s, `Future.get` on a virtual thread; the Gemini client has no timeout property). Timeout/API error/unparseable answer/unknown category → `Optional.empty()` → ticket created uncategorized, logged as `AI categorization failed: <root cause>` (the Google client wraps API errors in a generic "Failed to generate content"). No backfill/retry by design — the admin sets it by hand; replies (APPENDED) are never re-categorized. Categorization doesn't change status (the auto-reply step does).
- `TicketCategorizer` mirrors `EmailSender`: `AiTicketCategorizer` (`@ConditionalOnProperty spring.ai.model.chat=google-genai`; `ChatClient` from the auto-configured `ChatClient.Builder`, structured output `.entity(dto/CategoryResult)`) or `NoOpTicketCategorizer` (otherwise). Sent: subject + body (truncated to 4,000 chars, inside `<email>` tags; system prompt says it's untrusted data). Name/address aren't sent as fields but often appear in the body (signatures) — don't claim otherwise.
- No automated tests for the AI categorization, by the user's choice (the admin category endpoint/dropdown is tested). If ever needed: `ChatClient.builder(model)` over a lambda `ChatModel` (single abstract method `call(Prompt)`) avoids the network.

### AI auto-reply

- Flow: `InboundEmailService` (CREATED path only, after `openTicket` commits) → `AutoReplyService.replyAsync` (virtual thread; the webhook returns at once, since categorization already uses up to 10s of Resend's ~15s) → `KnowledgeBaseResponder.answer(subject, body)` → `dto/KnowledgeBaseAnswer(answerable, reply)`:
  - answerable with a non-blank reply → `TicketService.sendAiReply`: `AI` message (+ fixed `AI_REPLY_FOOTER`, stored as sent) through `addMessage`, `AI_RESPONDED`, emailed with the same threading/`[#id-TOKEN]` subject as admin replies (shared private `sendReply`). A send failure or reserved test address rolls it back → escalate.
  - not answerable, or AI failure (timeout 30s / API error / unparseable) → `TicketService.escalateToHuman` → `PENDING_HUMAN`, no email.
  - AI off (`NoOpKnowledgeBaseResponder`, `Optional.empty()`) → ticket stays `OPEN`.
  - Both writes are no-ops unless the ticket is still `OPEN` with no `firstResponseAt` (the admin may have answered first). A customer reply to `AI_RESPONDED` → `PENDING_HUMAN` (existing `appendCustomerReply` rule); replies are never auto-answered.
- Known gap: a restart while a reply is in flight leaves that ticket `OPEN` (no retry/backfill).
- `AiKnowledgeBaseResponder` mirrors `AiTicketCategorizer` (same switch, `ChatClient.Builder`, `<email>` tags, 4,000-char body). The whole KB (`app.ai.knowledge-base`, default `classpath:knowledge-base-short.md`, `KNOWLEDGE_BASE=file:…` to override; read once at startup, fails fast if missing) goes into the system prompt: it's one short doc, so no RAG/vector store. Prompt rules (user's choices): answer only from KB facts; any uncovered part, a request for a person, anger, unclear or non-English → not answerable; for requests only a person can do (refund, resend) give the KB's next step (e.g. ask for the order ID) but never claim it's done.
- `{`/`}` in the KB or emails are safe: Spring AI 2.0.1 only renders system/user text as a template when params are passed (checked in the jar by the code review).
- Safeguards: `<email>`/`<knowledge_base>` tags are stripped from the email before prompting (it can't close its own block); AI emails carry `Auto-Submitted: auto-replied` (`OutboundEmail.autoReply`, RFC 3834) so other auto-responders don't answer them; a customer follow-up that arrives while the AI drafts makes `sendAiReply` return false → escalated.
- Known gaps (reported in review, not fixed yet): any From address — forged too — gets an AI email, with no rate cap; and no row lock between the OPEN check and the write, so an admin action in the same second could race the AI.
- Tests: `AutoReplyServiceTest` (same-thread `Executor` via the package-private constructor), `TicketServiceTest` (`sendAiReply`/`escalateToHuman`), `InboundEmailServiceTest`. No live-AI tests, same as categorization.

### Dashboard metrics

- `GET /api/dashboard/metrics?period=DAILY|WEEKLY` (`DashboardController` → `MetricsService`). DAILY = last 30 days bucketed per day, WEEKLY = last 12 ISO weeks (Monday start); buckets are server-local dates, zero-filled.
- Every metric covers **tickets created in the window**, so all numbers on the page describe the same set: totals per bucket; current status and category counts (null category = uncategorized, always listed); handled split (every ticket in exactly one group, see `MetricsService.handledBy`) — AI = AI replied, no admin reply, and never escalated (status `AI_RESPONDED`/`RESOLVED`); unhandled = still `OPEN` (excluded from the AI/human shares); human = everything else, i.e. an admin replied, it's escalated (`PENDING_HUMAN`/`IN_PROGRESS` means the AI couldn't resolve it, even before any admin reply), or the admin closed it with no reply; avg response = mean of `firstResponseAt − createdAt` over replied tickets; resolution rate = currently `RESOLVED` ÷ total. Null rate/avg when there's nothing to measure.
- Aggregation is in Java over one `TicketMetricRow` per ticket (`TicketRepository.findMetricRowsCreatedSince`) to stay DB-agnostic — move to SQL `GROUP BY` if volume ever makes this slow. Time comes from the injectable `Clock` bean (`config/TimeConfig`) so tests can fix "now".
- The AI auto-reply writes `AI` messages through `Ticket.addMessage`, so `firstResponseAt` and the AI/human split are correct without metrics-specific code.
- Chart colors were checked with the `dataviz` skill's palette validator against the dashboard's white surface; magnitude bars use one hue (identity comes from the badge label), AI vs human is a 2-segment stacked bar with a legend.

### Other

- `spring.jpa.hibernate.ddl-auto=update` — schema is currently auto-managed by Hibernate from entities, no migration tool (e.g. Flyway/Liquibase) is set up yet.
- `WebConfig` CORS allows the origins in `app.cors.allowed-origins` (comma-separated; default `http://localhost:5173`, `http://localhost:4173` + `${PUBLIC_APP_URL:}`, which `run-backend-resend.sh` fills from `NGROK_URL`; empty entries are skipped). It is **not** dormant behind the Vite proxy: the browser's `Origin` header is forwarded, so every address the UI is opened on must be listed, or logins fail with 403 "Invalid CORS request" before the password is checked (`CorsIntegrationTest`).
- Backend dependencies now include Spring Security, Mail, jsoup and Spring AI (Google GenAI / Gemini) in addition to Web/Data JPA/PostgreSQL driver/Validation/Lombok/DevTools. Frontend now has React Router and Axios in addition to React/Vite; Recharts is added (dashboard charts); Tailwind CSS from the target stack is still not added.
- Ticket statuses and the dashboard metrics to build toward are defined in `PROJECT_PLAN.md` — implement against those definitions rather than inventing new ones.

## Git / GitHub

- This repo is pushed to a **private** GitHub repo at `https://github.com/Imtiyaz-faisalkhan/customer-support-system`. The `origin` remote uses HTTPS (not SSH) with `gh`'s stored credentials, since no SSH key is configured for GitHub on this machine — use `gh` for push/PR auth rather than assuming an SSH key exists.
- `.vscode/mcp.json` (repo root) holds a real Context7 API key and is gitignored — don't remove that `.gitignore` entry or otherwise commit that file.
