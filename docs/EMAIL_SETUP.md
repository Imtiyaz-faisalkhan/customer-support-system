# Email setup

Customers talk to the support system only by email:

- **Inbound:** a customer emails your support address → the app picks the email up → it becomes a new ticket, or a new message on their existing ticket if it's a reply.
- **Outbound:** you reply from the ticket page → the app emails the customer → it shows up in the **same email conversation** in their inbox, and their answer comes back onto the same ticket.

The app can receive email in three ways:

| | How emails get in | Used for |
|---|---|---|
| **Resend webhook** | Resend pushes each email to the app instantly (needs the ngrok tunnel) | **Your setup** (Part 2) |
| **Mailbox (IMAP)** | The app checks a normal mailbox (e.g. IONOS Mail) every 30 seconds | Alternative (Part 3); no tunnel needed |
| **SendGrid webhook** | SendGrid pushes each email (Inbound Parse format) | Local testing (Part 1), and SendGrid if you ever use it (Part 4) |

Whichever way, the same logic decides what each email becomes: new ticket, reply on an existing ticket, duplicate, or auto-reply to ignore.

---

## Part 1 — Try it locally (no accounts needed)

Locally, two stand-ins replace real email:

| Real world | Local stand-in |
|---|---|
| A customer's email arriving | `scripts/simulate-inbound-email.sh` posts it to the app's webhook |
| Your reply going to the customer | **Mailpit**, a fake mail server with a web inbox at <http://localhost:8025>. Nothing reaches a real person. |

### 1. Start everything

```bash
docker compose up -d                       # repo root: Postgres + Mailpit
cd backend && ./mvnw spring-boot:run       # backend
cd frontend && npm run dev                 # frontend, in another terminal
```

### 2. A customer emails you

```bash
scripts/simulate-inbound-email.sh \
  --from "Sam Carter <sam.carter@example.com>" \
  --subject "Package arrived damaged" \
  --text "Hi, my order #4411 arrived with a cracked screen. Can I get a replacement?"
# → {"ticketId":10,"outcome":"CREATED"}
```

The ticket appears at <http://localhost:5173/dashboard/tickets>.

### 3. Reply from the app

Open the ticket and click **Send Reply**. The email shows up in **Mailpit** (<http://localhost:8025>) with the subject `Re: Package arrived damaged [#10]`.

### 4. The customer answers

In Mailpit, open your reply → **Headers** → copy the `Message-Id`. Then:

```bash
scripts/simulate-inbound-email.sh --from "Sam Carter <sam.carter@example.com>" \
  --subject "Re: Package arrived damaged" --in-reply-to "<paste-Message-Id>" \
  --text "Thanks! Do I need to send the broken one back?"
# → {"ticketId":10,"outcome":"APPENDED"}
```

---

## Part 2 — Real email with Resend (your setup)

- **Receiving:** an email to your support address reaches Resend. Resend calls the app's webhook (`/api/webhooks/resend`) through your ngrok tunnel, the app fetches the full email from Resend, and it becomes a ticket.
- **Sending:** replies go out through Resend's mail server (`smtp.resend.com`) from your support address.

### One-time setup in Resend

1. **Domain** (done): `support.yourdomain.com`, verified, with **sending and receiving** enabled. Customers can write to any address on it; this guide uses `help@support.yourdomain.com`.
2. **API key:** **API Keys → Create API Key**.
   - Choose **Full access**. The app needs it to *read* received emails, which a sending-only key can't do.
   - Copy it (`re_…`) into a private note right away; it's shown only once.
3. **Webhook:** **Webhooks → Add Webhook**:
   - **Endpoint URL:** `https://your-name.ngrok-free.dev/api/webhooks/resend`
   - **Events:** only **`email.received`**
   - Save, then open the webhook and copy its **Signing Secret** (`whsec_…`) into the same private note.

### Run it (two terminals)

```bash
# terminal 1: backend wired to Resend (asks for the API key, the signing secret, and an optional new admin password)
scripts/run-backend-resend.sh help@support.yourdomain.com

# terminal 2: the tunnel, so Resend can reach your laptop
NGROK_URL=https://your-name.ngrok-free.dev scripts/start-tunnel.sh
```

- Make sure Docker is running (`docker compose up -d`) and the frontend too (`cd frontend && npm run dev`). The tunnel points at the frontend, which forwards `/api/...` (including Resend's webhook) to the backend.
- **Use the app from anywhere** at `https://your-name.ngrok-free.dev`. ngrok shows a one-time "You are about to visit…" page in browsers; click **Visit Site**. On your laptop, `http://localhost:5173` keeps working too.
- The secrets are typed in hidden and kept only in memory.
- **Set a new admin password when asked.** While the tunnel runs, the login page is on the internet.

### Try it for real

1. From Gmail, email `help@support.yourdomain.com`. Within seconds a ticket appears in the app, and the ngrok window shows `POST /api/webhooks/resend 200`.
2. Reply from the app. It arrives in Gmail; check spam the first time.
3. Reply to that email from Gmail. It lands on the **same ticket**. The reply's subject carries `[#id]`, which matches it even if Resend replaces the app's own `Message-ID`.

### Troubleshooting

| Symptom | Likely cause |
|---|---|
| No ticket; ngrok shows **401** | Wrong signing secret; it's per webhook, so copy it from that webhook's page |
| No ticket; ngrok shows **502** | Backend not running; or the app couldn't fetch the email from Resend (wrong or sending-only API key; the backend log says `Could not fetch received email`). Resend retries automatically |
| No ticket and nothing in ngrok | Tunnel not running, or the webhook URL/event in Resend doesn't match. Check **Emails → Receiving** in Resend to confirm the email arrived there at all |
| "Could not send the email…" when replying | Wrong API key, or the domain's sending records aren't verified |
| Customer reply became a **new** ticket | Sent from a different address than the original. Only the ticket's own customer can add to it, by design |

---

## Part 3 — Alternative: your IONOS mailbox (IMAP)

The app uses your IONOS support mailbox like a mail program would:
- **Receiving:** every 30 seconds it logs in, imports each email in the **Inbox**, and **moves it into a folder called `Imported`**. The app creates that folder itself.
- **Sending:** your replies go out through IONOS's mail server, from the support address.

### Before you start (one-time checks in IONOS)

- [ ] A **dedicated** mailbox exists, e.g. `support@yourdomain.com`. Don't use a personal mailbox; the app imports *everything* that lands in its Inbox.
- [ ] You know its **password**.
- [ ] Sending and receiving work: an email from Gmail to the mailbox arrives, and a reply from IONOS Webmail arrives back in Gmail.
- [ ] **SPF/DKIM pass**: in Gmail, open an email from the mailbox → ⋮ → **Show original** → `DKIM: PASS`.
- [ ] You know your **IONOS region**, from the site you bought on: `ionos.com` (US), `ionos.co.uk`, `ionos.de`, `ionos.fr`, … `dig +short MX yourdomain.com` shows it too, e.g. `mx00.ionos.com`.

### Run the backend connected to IONOS

Stop any running backend (Ctrl+C), make sure Postgres is up (`docker compose up -d`), then from the repo root:

```bash
scripts/run-backend-ionos.sh support@yourdomain.com
```

- For non-US accounts, add the region: `IONOS_REGION=co.uk scripts/run-backend-ionos.sh support@yourdomain.co.uk` (or `de`, `fr`, …).
- It **asks for the mailbox password** without showing it, so it never lands in your shell history or a file.
- It uses `imap.ionos.<region>:993` (SSL) to receive and `smtp.ionos.<region>:587` (STARTTLS) to send.
- To show a nicer sender name, set it first, e.g. `SUPPORT_EMAIL_NAME="Acme Support" scripts/run-backend-ionos.sh …`.

Within a few seconds of starting, the backend log shows either nothing (mailbox empty), `Imported N email(s) from the support mailbox`, or a warning like `Could not check the support mailbox …: [AUTHENTICATIONFAILED]` (see Troubleshooting).

> **Heads-up:** on the first run, emails **already** in the mailbox's Inbox, like your earlier test emails, are imported as tickets and moved to `Imported`. Move anything you don't want imported to another folder first.

### Try it for real

1. From Gmail, email `support@yourdomain.com`. Within about 30 seconds a ticket appears in the app, and in IONOS Webmail the email has moved from **Inbox** to **Imported**.
2. Reply from the app. It arrives in Gmail **in the same conversation**. Check spam the first time.
3. Reply to that email from Gmail. Within about 30 seconds it lands on the **same ticket**.

### Good to know

- **What's in the Inbox = not imported yet.** Reading an email in Webmail doesn't stop it being imported. Don't delete Inbox emails you want as tickets; the app handles them.
- **Replies you send from the app don't appear in IONOS's "Sent" folder.** They're sent directly; the ticket thread in the app is the record.
- **Bounces and out-of-office replies** are moved to `Imported` but don't create tickets.
- **Change how often it checks** with `IMAP_POLL_INTERVAL=1m` (any duration; default `30s`).
- **IONOS has a daily sending limit** per mailbox (usually hundreds per day), which is plenty for a support desk.

### Troubleshooting

| Symptom | Likely cause |
|---|---|
| Log: `Could not check the support mailbox …: AUTHENTICATIONFAILED` | Wrong password or address; external mail-program access disabled for that mailbox in IONOS |
| Log: `Could not check the support mailbox …: UnknownHost` / timeout | Wrong region (try `IONOS_REGION=co.uk`, `de`, …); no internet |
| "Could not send the email…" when replying | Same as above, but for SMTP; or port 587 blocked on your network |
| Email stays in the Inbox | Check the backend log for `Could not import email … (will retry)`. Usually the database is down; it retries automatically every 30s |
| Customer reply became a **new** ticket | It was sent from a different address than the original. Only the ticket's own customer can add to it, by design |
| Replies land in spam | Re-check DKIM/SPF (see the checklist); brand-new domains need a little sending history |

### Security notes

- The mailbox password is only held in memory by the running backend. Never put it in `application.properties` or git.
- Attachments aren't imported (out of MVP scope). The ticket shows a note like "[2 attachments not imported]".

---

## Part 4 — Alternative: SendGrid (webhook)

Providers like **SendGrid** can push each email to the app instantly instead of the app checking a mailbox. The app supports SendGrid's **Inbound Parse** format at `POST /api/webhooks/sendgrid/inbound`. (SendGrid declined this project's account, which is why Part 2 uses Resend.) Outline, if you ever switch:

1. **Domain records:** the provider's domain-authentication CNAMEs, plus an **MX** record for a receiving subdomain (e.g. `support.yourdomain.com` → `mx.sendgrid.net`) so your normal mail is untouched.
2. **A public URL** for your laptop: `NGROK_URL=https://your-name.ngrok-free.dev scripts/start-tunnel.sh`. ngrok is installed and configured; the script prints the destination URL.
3. **Inbound Parse destination:** `https://sendgrid:<webhook-password>@<ngrok-host>/api/webhooks/sendgrid/inbound`, with "POST the raw MIME" unchecked. Make the password with `openssl rand -hex 24` and pass it as `INBOUND_WEBHOOK_PASSWORD`.
4. **Sending:** `SMTP_HOST=smtp.sendgrid.net SMTP_PORT=587 SMTP_AUTH=true SMTP_STARTTLS=true SMTP_USERNAME=apikey SMTP_PASSWORD=<SG. API key>`.

**While a tunnel is running, your whole backend is public**, including the admin login. Use strong `INBOUND_WEBHOOK_PASSWORD` and `APP_ADMIN_PASSWORD` values, and stop the tunnel when you're done.
