# Code Reviewer Memory

Working notes carried across review sessions. Read at the start of every review, updated at the end. Short, factual entries only.

## Known accepted trade-offs (do not re-flag)

- CSRF is disabled in `SecurityConfig` on purpose, accepted MVP trade-off (SameSite session cookie + CORS locked to the frontend origin is the baseline defense for this single-admin, local-only tool). Not a finding unless the deployment context changes.
- Single hardcoded admin account via `InMemoryUserDetailsManager`, no multi-user/roles model. Not a finding; don't suggest adding RBAC or a users table unless asked.
- No migration tool (Flyway/Liquibase); `spring.jpa.hibernate.ddl-auto=update` is the current intended state. Not a finding on its own.
- `WebConfig` CORS restricting `/api/**` to `http://localhost:5173` is dormant in normal `npm run dev` (Vite proxy makes requests same-origin). Only relevant for non-proxied clients or a future split-origin deploy.

## Conventions confirmed from CLAUDE.md

- Frontend is deliberately plain JavaScript, converted off the `react-ts` template. Any `.ts`/`.tsx` file or type annotation is a real finding, not a style nit.
- All frontend API calls must go through the shared `frontend/src/api/axios.js` instance, not raw `fetch` — it has the global 401-to-logout interceptor baked in.
- New backend endpoints are `authenticated()`-protected by default; an endpoint missing from `permitAll()` is only a finding if it's supposed to be public.

- Accepted (2026-10 review): OPEN/AI_RESPONDED system-only statuses; LoggingEmailSender stub; no inbound webhook yet; DemoTicketSeeder on via `app.demo-data.enabled=true` in main application.properties; local Postgres column-type mismatch after Instant->LocalDateTime switch (user drops tables).
- AuthControllerTest clearing SecurityContextHolder in @AfterEach is an intentional thread-local leak fix.
- Accepted (2026-10 dashboard review): no dashboard auto-refresh; single-hue breakdown bars + 2-segment stacked AI/human bar (dataviz skill); metrics aggregated in Java not SQL GROUP BY (documented in CLAUDE.md); local Postgres keeps timestamptz for Ticket.createdAt/updatedAt (works, pgjdbc uses JVM zone).
- Dashboard metrics cover tickets *created* in the window (current status/category); bucket dates are server-local. Documented in CLAUDE.md "Dashboard metrics".

## Codebase facts

- Backend is layered (config/controller/dto/entity/enums/repository/service/exception), not feature packages; CLAUDE.md on disk documents this (the system-reminder copy can be stale; read the file).
- Ticket.createdAt/updatedAt are LocalDateTime (@CreationTimestamp/@UpdateTimestamp); TicketMessage.createdAt, firstResponseAt, resolvedAt are Instant. addMessage touches updatedAt on purpose.
- React Router is v7.18; Link's click handler does not stopPropagation, so a Link inside an onClick row double-navigates.

## Recurring issues seen across reviews

- Mixed LocalDateTime/Instant timestamps: resolved by ApiTimestamps.toInstant in DTOs (all API timestamps UTC `Z`); verified 2026-10.
- Fixed and verified 2026-10: messageCount removed from TicketSummary; Link stopPropagation in TicketsPage rows; TicketsPage query-tagged responses; TicketDetailPage applies action response then refreshQuietly().
- Pattern: "keep previous response, tag with period/query" state in pages. Watch the error path (stale data shown undimmed under a new label) and races between multiple un-cancelled refetches.
- Flagged 2026-10: MetricsService counts AI-replied-then-escalated (PENDING_HUMAN, no admin msg) tickets as "AI handled", contradicting PROJECT_PLAN's Pending Human definition.

## Environment facts

- Live JVM zone is UTC+2 (CEST); dashboard `from` shows 22:00Z. Seeded live DB has 9 tickets; ticket 6 is the AI->customer follow-up->PENDING_HUMAN case.
- target/surefire-reports contains stale reports from the old auth/ and ticket/ test packages; check timestamps before trusting them.

## False positives the user has dismissed

(none yet)
