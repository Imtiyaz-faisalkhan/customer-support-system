# Tech Stack

## Backend — Spring Boot (Java)

- Spring Web (REST API)
- Spring Data JPA + Hibernate (Postgres access)
- Spring Security (admin login/auth — session or JWT)
- Spring AI for the LLM integration (categorization + KB-based auto-response)
- A webhook controller endpoint for inbound email (email provider TBD, see Email Ingestion below)
- Build tool: Maven

## Frontend — React + Vite

- React Router (dashboard pages: ticket list, ticket thread view, login)
- Axios/fetch for calling the Spring Boot API
- Tailwind CSS for styling
- A lightweight chart library (Recharts) for the metrics dashboard (avg response time, tickets/day, status/category breakdown)

## Database — PostgreSQL

- No separate vector DB needed since the knowledge base is a single doc

## Email Ingestion

- Provider/mechanism to be decided later

## AI / Knowledge Base

- Spring AI as the integration layer, backed by a single LLM API (e.g. Anthropic Claude) for ticket categorization and generating the auto-response
- KB stored as a single doc (PDF/markdown) — no RAG pipeline or vector store for MVP; doc content passed directly as context to the LLM call

## Auth

- Spring Security with a single admin account (username/password), session-based or simple JWT — no OAuth/SSO/identity provider for MVP

## Out of Scope (Infra)

- No microservices
- No message queue
- No vector DB
- No separate auth service
- No containerized multi-service deployment — a single Spring Boot app + single Postgres instance + static-hosted React app is sufficient for MVP
