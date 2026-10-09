# Code Reviewer Memory

Working notes carried across review sessions. Read at the start of every review, updated at the end. Short, factual entries only.

## Known accepted trade-offs (do not re-flag)

- CSRF is disabled in `SecurityConfig` on purpose, accepted MVP trade-off. Deployment context changed 2026-10 (public via ngrok); still low risk because all mutating endpoints except logout require JSON bodies (form POST -> 415, JSON -> preflight). Note: no `same-site` cookie property is actually configured (comment's 'SameSite' baseline relies on browser defaults).
- Single hardcoded admin account via `InMemoryUserDetailsManager`, no multi-user/roles model. Not a finding; don't suggest adding RBAC or a users table unless asked.
- No migration tool (Flyway/Liquibase); `spring.jpa.hibernate.ddl-auto=update` is the current intended state. Not a finding on its own.
- (Corrected 2026-10) `WebConfig` CORS is NOT dormant behind the Vite proxy: the browser Origin is forwarded, so origins come from `app.cors.allowed-origins` (localhost:5173 + ngrok URL). Covered by CorsIntegrationTest.

## Conventions confirmed from CLAUDE.md

- Frontend is deliberately plain JavaScript, converted off the `react-ts` template. Any `.ts`/`.tsx` file or type annotation is a real finding, not a style nit.
- All frontend API calls must go through the shared `frontend/src/api/axios.js` instance, not raw `fetch` — it has the global 401-to-logout interceptor baked in.
- New backend endpoints are `authenticated()`-protected by default; an endpoint missing from `permitAll()` is only a finding if it's supposed to be public.

- Accepted (2026-10 review): OPEN/AI_RESPONDED system-only statuses; LoggingEmailSender (now the test-profile sender); DemoTicketSeeder on via `app.demo-data.enabled=true` for plain local dev (re-flagged 2026-10 only for the real Resend run, which shares the DB); local Postgres column-type mismatch after Instant->LocalDateTime switch (user drops tables).
- AuthControllerTest clearing SecurityContextHolder in @AfterEach is an intentional thread-local leak fix.
- MetricsService escalation finding (below) was fixed: PENDING_HUMAN/IN_PROGRESS now count as human.
- Accepted (2026-10 dashboard review): no dashboard auto-refresh; single-hue breakdown bars + 2-segment stacked AI/human bar (dataviz skill); metrics aggregated in Java not SQL GROUP BY (documented in CLAUDE.md); local Postgres keeps timestamptz for Ticket.createdAt/updatedAt (works, pgjdbc uses JVM zone).
- Dashboard metrics cover tickets *created* in the window (current status/category); bucket dates are server-local. Documented in CLAUDE.md "Dashboard metrics".

## Codebase facts

- Email is Resend-only (2026-10): ResendWebhookController (permitAll, Svix verify) -> ResendClient -> InboundEmailParser -> InboundEmailService; outbound SmtpEmailSender to smtp.resend.com. SendGrid webhook and IONOS IMAP poller were deliberately removed; never suggest re-adding.
- App is publicly reachable: scripts/start-tunnel.sh tunnels the Vite DEV server :5173 (serves whole frontend/ tree incl. e2e/auth.spec.js with default creds; /@fs outside frontend/ is 403). run-backend-resend.sh prompts for secrets.
- RestClient default factory here has no Apache/Jetty on classpath -> JDK/simple factory, no timeouts unless set.

- Backend is layered (config/controller/dto/entity/enums/repository/service/exception), not feature packages; CLAUDE.md on disk documents this (the system-reminder copy can be stale; read the file).
- Ticket.createdAt/updatedAt are LocalDateTime (@CreationTimestamp/@UpdateTimestamp); TicketMessage.createdAt, firstResponseAt, resolvedAt are Instant. addMessage touches updatedAt on purpose.
- React Router is v7.18; Link's click handler does not stopPropagation, so a Link inside an onClick row double-navigates.

## Recurring issues seen across reviews

- Mixed LocalDateTime/Instant timestamps: resolved by ApiTimestamps.toInstant in DTOs (all API timestamps UTC `Z`); verified 2026-10.
- Fixed and verified 2026-10: messageCount removed from TicketSummary; Link stopPropagation in TicketsPage rows; TicketsPage query-tagged responses; TicketDetailPage applies action response then refreshQuietly().
- Pattern: "keep previous response, tag with period/query" state in pages. Watch the error path (stale data shown undimmed under a new label) and races between multiple un-cancelled refetches.
- Flagged 2026-10 (Resend review): default admin pw usable over tunnel; Vite dev server public; ResendClient no timeouts; null Message-ID breaks dedupe; From-spoofing vs [#id] guard; stale EMAIL_INBOUND export; demo seeder on in Resend mode; parser leftover Function indirection. Check status next review.

- AI categorization (2026-10, commit eea4d2a): Spring AI 2.0.1 google-genai; TicketOpenedEvent -> @Async @TransactionalEventListener(AFTER_COMMIT) listener -> TicketCategorizer -> applyAiCategory (null-only). Settled, don't flag: no tests for AI categorizer/listener, no backfill/retry, categorization doesn't change status.
- Spring AI 2.0.1 google-genai props: `spring.ai.google.genai.chat.<opt>` is current; `chat.options.<opt>` is deprecated (verified in jar metadata). Retry via core RetryTemplate.
- Ticket has no @Version/@DynamicUpdate: Hibernate UPDATE writes every column (seen in test SQL log), so concurrent short transactions can lost-update each other.
- Flagged 2026-10 (AI review): model in application.properties (gemini-3.8-flash) != docs/commit (gemini-2.5-flash); EMAIL_SETUP privacy claim overstated (signatures in body); AI applyAiCategory full-row lost update; script AI toggle leaks from earlier shell export. Check status next review.

## Environment facts

- Live JVM zone is UTC+2 (CEST); dashboard `from` shows 22:00Z. Seeded live DB had 9 demo tickets (@example.com) and is the same DB the Resend run uses; ticket 6 is the AI->customer follow-up->PENDING_HUMAN case.
- target/surefire-reports contains stale reports from the old auth/ and ticket/ test packages; check timestamps before trusting them.

## False positives the user has dismissed

(none yet)
