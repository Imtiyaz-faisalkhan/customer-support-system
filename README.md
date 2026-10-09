# Customer Support System

An email-based customer support desk with an AI first responder. Customers just send an email. Each email becomes a ticket, AI sorts it and answers from a knowledge base when it can, and a single admin handles the rest from a web dashboard.

## How it works

```
Customer email ─► Resend (inbound) ─► webhook ─► new ticket
                                                   │
                         AI (Gemini) picks a category: Billing / Technical / General
                                                   │
                         AI checks the knowledge base
                          ├─ it covers the question ─► AI reply emailed, ticket "AI Responded"
                          └─ it doesn't ──────────────► ticket "Pending Human" for the admin
                                                   │
Admin dashboard ─► reads the full thread, replies (sent as email), sets status
Customer replies by email ─► added to the same ticket
```

- **Email in:** inbound email reaches the app through Resend's signed `email.received` webhook. Replies are matched to their ticket by email threading headers or a subject tag (`[#id-TOKEN]`). Auto-replies, bounces and duplicate deliveries are ignored.
- **AI categorization:** every new ticket is categorized before it appears in the dashboard.
- **AI auto-reply:** the AI answers only from the knowledge base (`backend/src/main/resources/knowledge-base-short.md`). For anything not covered, it sends nothing and hands the ticket to the admin. If the customer replies to an AI answer, the ticket goes to the admin too.
- **Ticket statuses:**

  | Status | Meaning |
  |---|---|
  | Open | Just created |
  | AI Responded | The AI answered |
  | Pending Human | Needs the admin |
  | In Progress | The admin is on it |
  | Resolved | Done |

- **Dashboard:** daily and weekly ticket volume, tickets by status and category, AI-vs-human handled ratio, average first-response time and resolution rate.
- **Admin:** a single admin account with session-based login. Customers never log in.

## Tech stack

| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web, Security, Data JPA, Mail), Spring AI 2.0 with Google Gemini |
| Frontend | React 19, Vite, React Router, Axios, Recharts (plain JavaScript) |
| Database | PostgreSQL |
| Email | [Resend](https://resend.com) (inbound webhook + sending); [Mailpit](https://mailpit.axllent.org) as a local fake inbox |
| Tests | JUnit 5, Mockito, MockMvc (H2 in-memory), Vitest + Testing Library, Playwright |

## Project layout

```
backend/    Spring Boot API (layered: controller / service / repository / entity / dto / config)
frontend/   React app (pages, components, api, context)
docs/       EMAIL_SETUP.md: connecting a real email domain through Resend
scripts/    run-backend-resend.sh (backend wired to real email), start-tunnel.sh (public ngrok URL)
docker-compose.yml   Postgres + Mailpit for local development
```

## Running locally

Requirements: Java 21, Node.js 20.19+ (or 22.12+), Docker.

```sh
# 1. Postgres (host port 5433) and Mailpit (inbox UI at http://localhost:8025)
docker compose up -d

# 2. Backend on http://localhost:8080
cd backend && ./mvnw spring-boot:run

# 3. Frontend on http://localhost:5173 (proxies /api to the backend)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 and log in as `admin` / `changeme123`. These local defaults are in `application.properties`; never use them anywhere public. On an empty database, sample tickets are created so the dashboard has something to show. Replies you send from the dashboard land in Mailpit.

**AI is off by default.** To turn it on, get a key from [Google AI Studio](https://aistudio.google.com/apikey) and start the backend like this:

```sh
read -rs GEMINI_API_KEY && export GEMINI_API_KEY AI_CHAT_PROVIDER=google-genai
./mvnw spring-boot:run
```

**Real email (optional):** [docs/EMAIL_SETUP.md](docs/EMAIL_SETUP.md) explains how to receive and send real email through Resend with your own domain. It uses `scripts/run-backend-resend.sh` together with an ngrok tunnel (`scripts/start-tunnel.sh`). The script asks for every key and password at startup; none are stored in files.

## Tests

```sh
cd backend && ./mvnw test        # unit + integration tests (in-memory H2, no Docker needed)
cd frontend && npm test          # Vitest unit tests
cd frontend && npm run lint      # oxlint
cd frontend && npm run test:e2e  # Playwright; needs the full stack running and `npx playwright install chromium` once
```

## Knowledge base

The AI's answers come from a single Markdown file: [backend/src/main/resources/knowledge-base-short.md](backend/src/main/resources/knowledge-base-short.md). Edit it and restart the backend to change what the AI knows. The AI is instructed to use only facts from this file. It never claims to have done something only a person can do, such as issuing a refund; it gives the customer the next step instead.

## Deployment

This branch (`main`) is the local development setup. The hosted version runs on Render's free tier from the **`deploy`** branch, which adds a Dockerfile, a Render Blueprint and production settings on top of `main`. See `docs/DEPLOY_RENDER.md` on that branch. Changes are made on `main` and then merged into `deploy`.

## Known limitations

- **Attachments:** not processed; the ticket only notes that there were attachments.
- **Single admin and English only,** as planned for this MVP (see [PROJECT_PLAN.md](PROJECT_PLAN.md)).
- **Forged senders:** sender addresses aren't verified. A forged email opens a new ticket, and with AI on it gets an AI reply.
- **Restarts:** an AI reply still being written when the server restarts is lost, and that ticket stays Open for the admin.
- **CSRF protection is off.** This is an accepted MVP trade-off; same-site session cookies and a strict CORS policy are the protection for now.

## More documentation

- [PROJECT_PLAN.md](PROJECT_PLAN.md): requirements and MVP scope
- [TECH_STACK.md](TECH_STACK.md): technology choices
- [docs/EMAIL_SETUP.md](docs/EMAIL_SETUP.md): email setup with Resend
- [CLAUDE.md](CLAUDE.md): detailed architecture notes for development
