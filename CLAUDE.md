# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A customer support system where customers interact solely via email: inbound emails become tickets (via webhook), an AI agent auto-categorizes and attempts to auto-respond from a knowledge base, and a single admin escalates/resolves whatever the AI can't handle from a dashboard UI. Full requirements and MVP scope live in `PROJECT_PLAN.md`; the intended tech stack and what's explicitly out of scope live in `TECH_STACK.md`. Read both before making product/architecture decisions — don't re-derive scope from code alone.

**Current state**: scaffolding, a `/api/health` check, and full session-based auth (login page + protected dashboard shell) exist end-to-end. The dashboard's Home and Tickets pages are empty placeholders — no ticket/email/AI/dashboard-content features are implemented yet.

## Repo layout

- `backend/` — Spring Boot (Java 21, Maven)
- `frontend/` — React + Vite, plain JavaScript (not TypeScript — was deliberately converted from the `react-ts` template; keep new code in `.jsx`/`.js`, no `tsconfig`/type annotations)
- `docker-compose.yml` — Postgres for local dev

## Commands

### Backend (`backend/`)
- Run: `./mvnw spring-boot:run` (port 8080)
- Test: `./mvnw test` — single test: `./mvnw test -Dtest=ClassName#methodName`
- Compile only (fast sanity check without a DB): `./mvnw compile`
- Package: `./mvnw clean package -DskipTests` → `target/backend-0.0.1-SNAPSHOT.jar`

### Frontend (`frontend/`)
- Install: `npm install`
- Dev server: `npm run dev` (port 5173) — proxies `/api/**` to `http://localhost:8080` (see `vite.config.js`), so API calls use relative paths and never hit the browser's CORS/cross-origin cookie rules in dev
- Build: `npm run build`
- Lint: `npm run lint` (oxlint)

### Postgres
- Start: `docker compose up -d` (from repo root)
- **Runs on host port 5433, not 5432** — this machine already runs a native Postgres instance on 5432 for another project, so the mapping in `docker-compose.yml` was moved to `5433:5432` to avoid colliding with it. `backend/src/main/resources/application.properties` points at `jdbc:postgresql://localhost:5433/customer_support`. If Postgres connection fails with a role/auth error, check for a port collision here first before assuming credentials are wrong.
- If `docker` isn't on `PATH` in a shell, it's likely still callable at `/Applications/Docker.app/Contents/Resources/bin/docker` (Docker Desktop CLI symlinks aren't always installed on this machine).

## Architecture notes

### Auth

- Session-based auth via Spring Security (`backend/.../config/SecurityConfig.java`), not JWT/stateless — a single hardcoded admin account (credentials in `application.properties` as `app.admin.username`/`app.admin.password`, loaded via `AdminAccountProperties` and seeded into an `InMemoryUserDetailsManager`, BCrypt-hashed at startup). There's no multi-user/roles model; don't build toward one without being asked.
- Login is a custom JSON endpoint (`POST /api/auth/login` in `AuthController`), not Spring Security's built-in form login: it authenticates via `AuthenticationManager` and manually saves the `SecurityContext` into the HTTP session (`HttpSessionSecurityContextRepository`) — follow this same pattern for any other custom auth-adjacent endpoint. `GET /api/auth/me` and `POST /api/auth/logout` (the latter fully handled by Spring Security's `.logout()` DSL, no controller method needed) round out the auth API.
- `anyRequest().authenticated()` is the default in `SecurityConfig` — new backend endpoints are protected automatically; add to the `permitAll()` matcher list explicitly if one needs to be public.
- CSRF is disabled (see the comment in `SecurityConfig`) — an accepted MVP trade-off (SameSite session cookie + CORS locked to the frontend origin is the baseline defense for this single-admin, local-only tool). Revisit before any public/non-local deployment.
- Frontend auth state lives in `frontend/src/context/AuthContext.jsx`, backed by `GET /api/auth/me` on mount. `frontend/src/components/ProtectedRoute.jsx` gates the `/dashboard/*` route tree (see `App.jsx` for the route table) and redirects to `/login` when there's no session.
- `frontend/src/api/axios.js` is the one shared axios instance (`withCredentials: true`) — route all API calls through it (via `frontend/src/api/auth.js` for auth, or a new sibling module for future features) rather than raw `fetch`. Its response interceptor turns any `401` into a global `auth:unauthorized` DOM event; `AuthContext` listens for it and clears `user`, so a session expiring mid-use on *any* future authenticated call bounces the user back to `/login` automatically — don't re-implement 401 handling per-component.

### Other

- `spring.jpa.hibernate.ddl-auto=update` — schema is currently auto-managed by Hibernate from entities, no migration tool (e.g. Flyway/Liquibase) is set up yet.
- `WebConfig` CORS (`backend/.../config/WebConfig.java`) still restricts `/api/**` to `http://localhost:5173`, but in the normal `npm run dev` workflow it's dormant — the Vite proxy (above) makes requests same-origin, so this only matters for non-proxied clients (curl, a future split-origin deployment).
- Backend dependencies now include Spring Security in addition to Web/Data JPA/PostgreSQL driver/Validation/Lombok/DevTools; Spring AI (for ticket categorization/auto-response), from `TECH_STACK.md`'s target stack, is still not added. Frontend now has React Router and Axios in addition to React/Vite; Tailwind CSS and Recharts from the target stack are still not added.
- Ticket statuses and the dashboard metrics to build toward are defined in `PROJECT_PLAN.md` — implement against those definitions rather than inventing new ones.

## Git / GitHub

- This repo is pushed to a **private** GitHub repo at `https://github.com/Imtiyaz-faisalkhan/customer-support-system`. The `origin` remote uses HTTPS (not SSH) with `gh`'s stored credentials, since no SSH key is configured for GitHub on this machine — use `gh` for push/PR auth rather than assuming an SSH key exists.
- `.vscode/mcp.json` (repo root) holds a real Context7 API key and is gitignored — don't remove that `.gitignore` entry or otherwise commit that file.
