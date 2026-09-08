# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A customer support system where customers interact solely via email: inbound emails become tickets (via webhook), an AI agent auto-categorizes and attempts to auto-respond from a knowledge base, and a single admin escalates/resolves whatever the AI can't handle from a dashboard UI. Full requirements and MVP scope live in `PROJECT_PLAN.md`; the intended tech stack and what's explicitly out of scope live in `TECH_STACK.md`. Read both before making product/architecture decisions — don't re-derive scope from code alone.

**Current state**: only scaffolding + a `/api/health` check exist end-to-end. No ticket/email/AI/dashboard features are implemented yet.

## Repo layout

- `backend/` — Spring Boot (Java 21, Maven)
- `frontend/` — React + Vite, plain JavaScript (not TypeScript — was deliberately converted from the `react-ts` template; keep new code in `.jsx`/`.js`, no `tsconfig`/type annotations)
- `docker-compose.yml` — Postgres for local dev

## Commands

### Backend (`backend/`)
- Run: `./mvnw spring-boot:run` (port 8080)
- Test: `./mvnw test` — single test: `./mvnw test -Dtest=ClassName#methodName`
- Compile only (fast sanity check without a DB): `./mvnw compile`

### Frontend (`frontend/`)
- Install: `npm install`
- Dev server: `npm run dev` (port 5173)
- Build: `npm run build`
- Lint: `npm run lint` (oxlint)

### Postgres
- Start: `docker compose up -d` (from repo root)
- **Runs on host port 5433, not 5432** — this machine already runs a native Postgres instance on 5432 for another project, so the mapping in `docker-compose.yml` was moved to `5433:5432` to avoid colliding with it. `backend/src/main/resources/application.properties` points at `jdbc:postgresql://localhost:5433/customer_support`. If Postgres connection fails with a role/auth error, check for a port collision here first before assuming credentials are wrong.
- If `docker` isn't on `PATH` in a shell, it's likely still callable at `/Applications/Docker.app/Contents/Resources/bin/docker` (Docker Desktop CLI symlinks aren't always installed on this machine).

## Architecture notes

- Backend exposes REST endpoints under `/api/**`; `WebConfig` (`backend/src/main/java/com/customersupport/backend/config/`) restricts CORS on that path to `http://localhost:5173` only — any new frontend origin needs to be added there.
- `spring.jpa.hibernate.ddl-auto=update` — schema is currently auto-managed by Hibernate from entities, no migration tool (e.g. Flyway/Liquibase) is set up yet.
- The backend's actual dependencies (Web, Data JPA, PostgreSQL driver, Validation, Lombok, DevTools) are narrower than what `TECH_STACK.md` describes as the target (Spring Security, Spring AI, etc.) — those are planned, not yet added to `pom.xml`. Same for the frontend: `package.json` only has React + Vite so far, not the planned React Router/Axios/Tailwind/Recharts.
- Ticket statuses and the dashboard metrics to build toward are defined in `PROJECT_PLAN.md` — implement against those definitions rather than inventing new ones.
