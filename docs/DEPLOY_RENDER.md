# Deploying to Render (free tier)

The app can run in two ways, kept on separate git branches:

| | Local (`main` branch) | Render (`deploy` branch) |
|---|---|---|
| UI | Vite dev/preview server, public through ngrok | Built into the backend, served by it at `https://<service>.onrender.com` |
| Database | Postgres in Docker (`docker compose`) | Render's free Postgres |
| Outgoing email | SMTP (Mailpit, or Resend via `scripts/run-backend-resend.sh`) | Resend's HTTP API (Render's free tier blocks SMTP) |
| Started by | `./mvnw spring-boot:run` / the scripts | Render, from the `Dockerfile`, with the `prod` profile |

Everything Render-specific lives only on `deploy`:
- `Dockerfile`, `.dockerignore` and `render.yaml`
- `application-prod.properties`
- `ResendApiEmailSender` and `SpaController`
- this guide

`main` stays as it is, and the local and ngrok setup keeps working there.

## What the free tier means for this app

- **It sleeps after 15 minutes without traffic** and takes about a minute to wake. Resend gives up on a webhook after ~15 seconds and retries later, so the first email after a quiet period shows up as a ticket a few minutes late. It isn't lost; duplicates are already filtered out. Opening the UI after a sleep shows Render's loading page for about a minute.
- **512 MB memory, 0.1 CPU.** The Dockerfile tunes Java for this, and startup takes a minute or two.
- **The free database is deleted 30 days after it was created** (after a 14-day grace period) and has no backups. Every ticket goes with it. See "Monthly: recreate the database" below.
- **Render may restart the app at any time.** An AI reply being written at that moment is lost, and that ticket stays "Open" for you to answer.

## First deployment

You need:
- the Resend setup from [EMAIL_SETUP.md](EMAIL_SETUP.md): domain, receiving, a **full access** API key and the webhook signing secret
- a Gemini API key
- an admin password of 12+ characters

1. **Push the `deploy` branch** (already on GitHub if you're reading this there).
2. **Connect GitHub to Render**: sign up at <https://render.com> with GitHub and give Render access to the `customer-support-system` repository. Private repos are fine.
3. **Create the Blueprint**: Render dashboard → **New → Blueprint** → pick the repository and the **`deploy`** branch. Render reads `render.yaml` and plans a free Postgres (`customer-support-db`) plus a free web service (`customer-support`).
4. **Fill in the secrets** it asks for:

   | Variable | Value |
   |---|---|
   | `APP_ADMIN_PASSWORD` | your admin password (12+ characters; the app won't start without one) |
   | `SUPPORT_EMAIL_ADDRESS` | e.g. `help@support.yourdomain.com` |
   | `RESEND_API_KEY` | `re_…` (full access) |
   | `RESEND_WEBHOOK_SECRET` | `whsec_…` (see step 6; put any placeholder first if you don't have it yet) |
   | `GEMINI_API_KEY` | your Gemini key |
   | `PUBLIC_APP_URL` | `https://customer-support.onrender.com`, or whatever address Render shows for the service. If you don't know it yet, use a placeholder and fix it in step 5 |

5. **Wait for the first deploy** (several minutes: it builds the frontend and backend). Then check the service's address on its page. If it differs from `PUBLIC_APP_URL`, correct it under **Environment** (the service redeploys). Without the exact address, logging in fails with "Invalid CORS request".
6. **Point Resend's webhook at Render**: Resend → Webhooks → your `email.received` webhook (or a new one) → URL `https://<service>.onrender.com/api/webhooks/resend`. Copy its signing secret into `RESEND_WEBHOOK_SECRET` on Render if it changed. If you still use the ngrok setup locally, you need a separate webhook for each address.
7. **Test**:
   - Open the address, log in as `admin`.
   - Send an email from your own mailbox to the support address. A ticket should appear within a minute (longer if the service was asleep), and the AI reply, if the knowledge base covers your question, arrives in your inbox.

## Updating the deployment

Develop and test on `main` as usual, then:

```sh
git checkout deploy
git merge main
git push            # Render redeploys the deploy branch automatically
git checkout main
```

If a merge conflicts, it's in the few files both branches changed (`SecurityConfig`, `EmailProperties`, `ResendClient`, `CLAUDE.md`). Keep both sides.

The knowledge base is `backend/src/main/resources/knowledge-base-short.md`. Edits reach Render the same way.

## Optional: keep it awake

To avoid the one-minute wake-up, have a free monitor (e.g. <https://cron-job.org> or UptimeRobot) request `https://<service>.onrender.com/api/health` every 10 minutes. One service running all month uses ~744 of the 750 free instance-hours. That's fine as long as you have no other free web service in the same Render workspace, or those get suspended until the next month.

## Monthly: recreate the database

Render emails you before the free database expires. To start over with an empty database:
1. Delete the `customer-support-db` database in the Render dashboard.
2. Open the Blueprint → **Manual sync**. Render creates a new free database and reconnects the service to it.
3. The app creates its tables on startup, so nothing else is needed.

All tickets are gone after this. If you want to keep them, export first: the database page has the connection details for `pg_dump`.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Deploy fails, log says `Could not resolve placeholder 'APP_ADMIN_PASSWORD'` (or another variable) | That environment variable is missing on the service; add it under **Environment** |
| Login: "Invalid CORS request" (403) | `PUBLIC_APP_URL` isn't exactly the address in the browser (`https://`, no trailing slash) |
| Service restarts with "Out of memory" | Lower `-XX:MaxRAMPercentage` in the Dockerfile's `JAVA_TOOL_OPTIONS` |
| Emails don't become tickets | Resend → Webhooks shows the delivery attempts: 401 = wrong `RESEND_WEBHOOK_SECRET`, timeouts = the service was asleep (Resend retries) |
| Replies fail with 502 | Render's log shows `Resend could not send the email`; check the API key (full access) and that the support address's domain is verified for sending in Resend |
| Refreshing a page shows 404 | A new frontend route isn't forwarded by `SpaController` yet; add its path there and in `SecurityConfig` |
