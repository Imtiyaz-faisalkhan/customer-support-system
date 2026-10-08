# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A customer support system where customers interact solely via email: inbound emails become tickets (via webhook), an AI agent auto-categorizes and attempts to auto-respond from a knowledge base, and a single admin escalates/resolves whatever the AI can't handle from a dashboard UI. Full requirements and MVP scope live in `PROJECT_PLAN.md`; the intended tech stack and what's explicitly out of scope live in `TECH_STACK.md`. Read both before making product/architecture decisions — don't re-derive scope from code alone.

**Current state**: scaffolding, a `/api/health` check, and full session-based auth (login page + protected dashboard shell) exist end-to-end, with one reference test per layer (unit/integration/e2e — see Testing below). The backend ticket-management core (data model + `/api/tickets` REST API, see Tickets below) exists, but nothing creates tickets over HTTP yet (no inbound-email webhook) and outbound email is a logging stub. The frontend has a Tickets list (`pages/TicketsPage.jsx`, filters kept in the URL query string) and a ticket detail/thread page (`pages/TicketDetailPage.jsx`, reply + status controls) backed by `api/tickets.js`; shared ticket badges are `components/TicketBadges.jsx`, labels/date helpers `utils/ticketLabels.js`. Locally, `DemoTicketSeeder` fills an empty DB with sample tickets (`app.demo-data.enabled`). Home is still a placeholder — no AI or metrics yet.

## Repo layout

- `backend/` — Spring Boot (Java 21, Maven)
- `frontend/` — React + Vite, plain JavaScript (not TypeScript — was deliberately converted from the `react-ts` template; keep new code in `.jsx`/`.js`, no `tsconfig`/type annotations)
- `docker-compose.yml` — Postgres for local dev

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
- CSRF is disabled (see the comment in `SecurityConfig`) — an accepted MVP trade-off (SameSite session cookie + CORS locked to the frontend origin is the baseline defense for this single-admin, local-only tool). Revisit before any public/non-local deployment.
- Frontend auth state lives in `frontend/src/context/AuthContext.jsx`, backed by `GET /api/auth/me` on mount. `frontend/src/components/ProtectedRoute.jsx` gates the `/dashboard/*` route tree (see `App.jsx` for the route table) and redirects to `/login` when there's no session.
- `frontend/src/api/axios.js` is the one shared axios instance (`withCredentials: true`) — route all API calls through it (via `frontend/src/api/auth.js` for auth, or a new sibling module for future features) rather than raw `fetch`. Its response interceptor turns any `401` into a global `auth:unauthorized` DOM event; `AuthContext` listens for it and clears `user`, so a session expiring mid-use on *any* future authenticated call bounces the user back to `/login` automatically — don't re-implement 401 handling per-component.

### Tickets

- `Ticket` (1) → (*) `TicketMessage` (the email thread; `author` is `CUSTOMER`/`AI`/`ADMIN`). `TicketStatus`/`TicketCategory` match `PROJECT_PLAN.md`; `category` is null until AI categorizes. `firstResponseAt` (first non-customer message) and `resolvedAt` are stamped by the entity's `addMessage`/`changeStatus` and exist for the future metrics — mutate tickets through those methods, not raw setters, so the timestamps stay correct.
- `TicketService` is the only write path, and it maps to DTO records (`dto/`) inside its transaction. `openTicket(...)` is the entry point the inbound webhook should call. Admins may only set `PENDING_HUMAN`/`IN_PROGRESS`/`RESOLVED`; `OPEN`/`AI_RESPONDED` are system-only (400 otherwise). An admin reply moves the ticket to `IN_PROGRESS` unless it's `RESOLVED`.
- API: `GET /api/tickets?status=&category=&page=&size=` (own `PageResponse` shape, newest activity first, size capped at 100), `GET /api/tickets/{id}`, `PATCH /api/tickets/{id}/status`, `POST /api/tickets/{id}/replies`. Errors go through `exception/GlobalExceptionHandler` as `{"error": "..."}`. Add new exception mappings there rather than per-controller.
- Outbound email goes through `service/EmailSender`. The only implementation is `LoggingEmailSender` (provider TBD), which deliberately logs recipient/subject only, never the body. The send happens inside the reply transaction, so a send failure rolls the reply back.
- `Ticket.createdAt`/`updatedAt` are `LocalDateTime`, generated by Hibernate's `@CreationTimestamp`/`@UpdateTimestamp` (JVM default zone) — don't set them by hand. `@UpdateTimestamp` only fires when a ticket column changes, so `addMessage` touches `updatedAt` to make a message-only reply still count as activity. Other timestamps (`firstResponseAt`, `resolvedAt`, `TicketMessage.createdAt`) are still `Instant`s passed in by the caller. The API never exposes the zone-less `LocalDateTime`s: DTOs convert them with `dto/ApiTimestamps.toInstant`, so every timestamp in a response is a UTC instant (`...Z`) — keep it that way for new DTO fields.
- Gotcha: `ddl-auto=update` never changes an existing column's type. These two columns used to be `Instant` (`timestamp with time zone`); a Postgres DB created before the switch must have them converted (or the `tickets`/`ticket_messages` tables dropped and re-seeded) or reads fail.
- Gotcha: to get the id of a newly added `TicketMessage` on an already-managed `Ticket`, call `ticketRepository.flush()`, not `save`/`saveAndFlush`. `save` merges, and the cascaded merge persists a *copy*, leaving your instance's id null.
- Test gotcha: any unit test that calls `AuthController.login` directly must `SecurityContextHolder.clearContext()` afterward (see `AuthControllerTest`). Otherwise its mock `Authentication` leaks via the thread-local and later MockMvc tests on the same thread get 403s.

### Other

- `spring.jpa.hibernate.ddl-auto=update` — schema is currently auto-managed by Hibernate from entities, no migration tool (e.g. Flyway/Liquibase) is set up yet.
- `WebConfig` CORS (`backend/.../config/WebConfig.java`) still restricts `/api/**` to `http://localhost:5173`, but in the normal `npm run dev` workflow it's dormant — the Vite proxy (above) makes requests same-origin, so this only matters for non-proxied clients (curl, a future split-origin deployment).
- Backend dependencies now include Spring Security in addition to Web/Data JPA/PostgreSQL driver/Validation/Lombok/DevTools; Spring AI (for ticket categorization/auto-response), from `TECH_STACK.md`'s target stack, is still not added. Frontend now has React Router and Axios in addition to React/Vite; Tailwind CSS and Recharts from the target stack are still not added.
- Ticket statuses and the dashboard metrics to build toward are defined in `PROJECT_PLAN.md` — implement against those definitions rather than inventing new ones.

## Git / GitHub

- This repo is pushed to a **private** GitHub repo at `https://github.com/Imtiyaz-faisalkhan/customer-support-system`. The `origin` remote uses HTTPS (not SSH) with `gh`'s stored credentials, since no SSH key is configured for GitHub on this machine — use `gh` for push/PR auth rather than assuming an SSH key exists.
- `.vscode/mcp.json` (repo root) holds a real Context7 API key and is gitignored — don't remove that `.gitignore` entry or otherwise commit that file.
