# Problem

Need to build a customer support management system for my busniess where customer emails are managed in better way

## Solution

- Customer sends email to my customer support email address
- Email is received via webhook and converted to a ticket in my app
- Email is auto categorized using AI
- Email is auto responded by AI agent using knowledge base
- If AI cannot resolve it, ticket is escalated to the admin

## MVP Scope

### System type

- Single-agent system: one admin user manages all tickets
- Customers never log in to the system — they only interact via email
- Admin logs in to a dashboard/UI to view and manage tickets

### Core Features

- Inbound email ingestion via webhook
- Auto-create ticket from incoming email
- AI auto-categorization of tickets (e.g. billing, technical, general)
- AI auto-response using knowledge base (a single PDF/doc, no authoring tools) for common queries
- Escalation to admin when AI cannot confidently resolve a query
- Ticket view shows full email thread (customer + AI + admin replies) in one place
- Admin can reply to a ticket from the UI, sent back to customer as email
- Ticket status tracking
- Admin authentication (login) to access the system

### Ticket Statuses

- Open — ticket just created, not yet processed
- AI Responded — AI replied, waiting to see if customer is satisfied
- Pending Human — AI could not resolve, needs admin attention
- In Progress — admin is actively working on it
- Resolved — issue considered solved

### Dashboard & Metrics

- Average response time
- Total tickets received per day
- Ticket counts by status (Open, Pending Human, Resolved, etc.)
- Ticket counts by category/bucket
- Simple charts/graphs for the above trends over time

### Security

- Admin authentication and login
- Secure handling of customer email content

## Out of Scope (for MVP)

- Customer-facing portal or login — customers interact via email only
- Multi-agent support (multiple admins, roles/permissions, assignment routing)
- Multi-channel support (chat, social media, phone) — email only
- Multiple support email addresses/departments (e.g. billing@, support@)
- Attachments handling (images, PDFs in emails)
- Multi-language support
- SLA rules and automated alerts/notifications
- Customer satisfaction (CSAT) surveys/feedback collection
- Advanced spam/phishing filtering
- Knowledge base management UI — KB is just a single PDF/doc for MVP, no admin editing tools
- Multiple/structured knowledge base sources
- Reporting exports (CSV/PDF export of metrics)
