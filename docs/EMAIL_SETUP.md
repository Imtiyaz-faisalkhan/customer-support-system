# Email setup (Resend)

Customers talk to the support system only by email, through **Resend** (resend.com):

- **Inbound:** a customer emails `help@support.yourdomain.com` → Resend receives it → Resend calls the app's webhook (`POST /api/webhooks/resend`) through the ngrok tunnel → the app fetches the full email from Resend → it becomes a new ticket, or a new message on the customer's existing ticket.
- **Outbound:** you reply from the ticket page → the app sends it through Resend's mail server (`smtp.resend.com`) → it arrives in the customer's inbox **in the same conversation**, and their answer comes back onto the same ticket.

---

## One-time setup (done)

| Where | What | Status |
|---|---|---|
| Resend → Domains | `support.yourdomain.com`, verified, with **sending and receiving** enabled | ✅ |
| Your DNS provider → DNS for `yourdomain.com` | Resend's records: `resend._domainkey.support` (TXT), `send.support` (MX + TXT), and the receiving MX for `support` → `inbound-smtp.eu-west-1.amazonaws.com` | ✅ |
| Resend → API Keys | A **Full access** key (`re_…`). Full, because the app must *read* received emails | ✅ |
| Resend → Webhooks | Endpoint `https://<your-ngrok-domain>/api/webhooks/resend`, event **`email.received`**, plus its signing secret (`whsec_…`) | ✅ |
| ngrok | Account token configured, plus a free static domain (`something.ngrok-free.dev`, shown on the ngrok dashboard) | ✅ |
| Your shell profile (`~/.zshrc`) | `export NGROK_URL=https://<your-ngrok-domain>`, so both scripts know the address without it being in the repo. Open new terminals afterwards | ⬜ do once |

Keep the API key and signing secret in a private note. The app never stores them; you type them in at startup.

---

## Running it

Three things run, each in its own terminal, from the project folder. Docker must be running first (`docker compose up -d`).

```bash
# 1. backend, wired to Resend: asks for the API key, signing secret and an admin password (hidden input)
scripts/run-backend-resend.sh help@support.yourdomain.com

# 2. frontend: builds the app and serves the finished build on port 4173 (re-run after frontend changes)
cd frontend && npm run start:public

# 3. tunnel: makes the app AND the Resend webhook reachable from the internet
scripts/start-tunnel.sh
```

- **Open the app** at `https://<your-ngrok-domain>` from anywhere, or `http://localhost:4173` on your laptop. ngrok shows a one-time "You are about to visit…" page in browsers; click **Visit Site**.
- **The admin password** must be at least 12 characters, typed twice. While the tunnel runs, the login page is on the internet, so the script refuses the default dev password. After 5 wrong passwords in a row, logins are blocked for 5 minutes (restarting the backend clears it).
- **AI categorization** (optional): the script also asks for a Gemini API key (free to create at <https://aistudio.google.com/apikey>). With one, each new ticket gets a Billing / Technical / General category from Gemini a few seconds after it arrives; leave it empty and tickets stay "Uncategorized". You can always change the category on the ticket page.
- **No sample tickets** are created in this mode, and replies to test addresses like `@example.com` are refused: they'd only bounce, which hurts your domain's sending reputation. Sample tickets from earlier local runs are still in the database; leave them be.
- **Why not `npm run dev`?** The development server shares the whole `frontend/` folder (source code, tests) with anyone who has the URL. The tunnel exposes the finished build instead. For day-to-day UI work, use `npm run dev` on `http://localhost:5173` without the tunnel.
- **Watch incoming webhooks** live at <http://localhost:4040> (ngrok's inspector).

## Try it

1. From Gmail, email `help@support.yourdomain.com`. Within seconds a ticket appears in the app, and the inspector shows `POST /api/webhooks/resend 200`.
2. Reply from the app. It arrives in Gmail in the same conversation; check spam the first time.
3. Reply from Gmail. It lands on the **same ticket**. The app's reply subject carries a tag like `[#12-K7QX2M9A]` (ticket number plus a random code), which matches it even if Resend replaces the app's own `Message-ID`.

## Local development without real email

With the default settings (just `cd backend && ./mvnw spring-boot:run`), replies go to **Mailpit**, a fake mail server started by `docker compose up -d`. Its inbox is at <http://localhost:8025>, and nothing reaches a real person. Receiving needs the real Resend setup above. The receiving logic is covered by the automated tests (`./mvnw test`).

To try AI categorization locally, start the backend with the key typed in hidden (so it stays out of your shell history):

```bash
read -rs GEMINI_API_KEY && export GEMINI_API_KEY AI_CHAT_PROVIDER=google-genai
cd backend && ./mvnw spring-boot:run
```

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Gmail bounces: "domain couldn't be found" | The receiving MX record for `support` is missing or not live yet. `dig +short MX support.yourdomain.com @8.8.8.8` should show `inbound-smtp.eu-west-1.amazonaws.com` |
| Nothing in the ngrok inspector | Tunnel not running, or the webhook URL/event in Resend doesn't match. Check **Emails → Receiving** in Resend to confirm the email arrived there |
| Inspector shows **401** | Wrong signing secret; it's per webhook, so copy it from that webhook's page |
| Inspector shows **502** | Backend not running; or the app couldn't fetch the email from Resend (wrong or sending-only API key; the backend log says `Could not fetch received email`). Resend retries automatically |
| "Could not send the email…" when replying | Wrong API key, or the backend was started with the wrong support address |
| Login fails with 403 through the ngrok address | The backend doesn't know the address: `NGROK_URL` wasn't set in the backend's terminal (the script warns about it), or the tunnel got a random address. Restart the backend with `NGROK_URL` (or `PUBLIC_APP_URL`) set to the address in use |
| Login says "Too many failed logins" | 5 wrong passwords in a row. Wait 5 minutes, or restart the backend |
| New tickets stay "Uncategorized" | No Gemini key was given, or the call failed: the backend log says `AI categorization failed` and why (e.g. an invalid API key, or a quota limit on the free tier). Set the category by hand on the ticket page |
| "…is a test address that can't receive email" when replying | A sample ticket (`@example.com`). Expected with real email on; it would only bounce |
| Customer reply became a **new** ticket | Sent from a different address than the original, or the subject's `[#12-…]` tag was changed or removed. Replies to emails sent before the random code existed (subject tag `[#12]` only) also start a new ticket |

## Security notes

- The API key, signing secret and admin password exist only in the running backend's memory, never in files or git.
- Every Resend webhook is checked against its signature, and old or replayed ones are rejected, so nobody can call the webhook pretending to be Resend.
- Email itself isn't authenticated: anyone can send a message with a forged `From` address. A forged email simply opens a new ticket. To land on an **existing** ticket it must also carry that ticket's random subject code or a Message-ID from its thread, which only appear in emails exchanged with that customer. Treat customer claims in emails with normal caution.
- The session cookie is `HttpOnly` and `SameSite=Lax`, so other websites can't make your browser act on the app.
- With AI on, each new ticket's subject and text (first 4,000 characters) are sent to Google's Gemini API to pick a category. The customer's name and email address are not sent. The Gemini key, like the others, is only typed in at startup. Note: on Gemini's **free tier**, Google may use what you send to improve its products; use a paid (billing-enabled) key if your customers' emails must not be used that way.
- Attachments aren't imported (out of MVP scope). The ticket shows a note like "[2 attachments not imported]".
