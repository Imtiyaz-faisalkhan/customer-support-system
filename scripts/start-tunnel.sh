#!/usr/bin/env bash
# Exposes the app to the internet with ngrok: by default the frontend dev server (port 5173), which serves the UI
# AND forwards /api/** to the backend -- so one public URL gives you the app from anywhere and lets an email
# provider (Resend, or SendGrid Inbound Parse) reach its webhook. See docs/EMAIL_SETUP.md.
# TUNNEL_PORT=8080 exposes only the backend (webhooks, no UI).
#
#   NGROK_URL=https://your-name.ngrok-free.app scripts/start-tunnel.sh   # your free static domain (recommended)
#   scripts/start-tunnel.sh                                             # random URL that changes every run
set -euo pipefail

if ! command -v ngrok >/dev/null; then
  echo "ngrok is not installed: brew install ngrok" >&2
  exit 1
fi
if ! ngrok config check >/dev/null 2>&1; then
  echo "ngrok has no account token yet. One-time setup:" >&2
  echo "  1. Sign up (free) at https://dashboard.ngrok.com/signup" >&2
  echo "  2. Copy your token from https://dashboard.ngrok.com/get-started/your-authtoken" >&2
  echo "  3. Run: ngrok config add-authtoken <your-token>" >&2
  exit 1
fi
PORT="${TUNNEL_PORT:-5173}"
if ! curl -s -o /dev/null http://localhost:8080/api/health; then
  echo "Warning: the backend isn't answering on http://localhost:8080 yet (cd backend && ./mvnw spring-boot:run)." >&2
fi
if [[ "$PORT" == "5173" ]] && ! curl -s -o /dev/null http://localhost:5173/; then
  echo "Warning: the frontend isn't answering on http://localhost:5173 yet (cd frontend && npm run dev)." >&2
fi

USER_NAME="${INBOUND_WEBHOOK_USERNAME:-sendgrid}"
PASSWORD_HINT="${INBOUND_WEBHOOK_PASSWORD:-<your INBOUND_WEBHOOK_PASSWORD>}"

if [[ -n "${NGROK_URL:-}" ]]; then
  HOST="${NGROK_URL#https://}"
  if [[ "$PORT" == "5173" ]]; then
    echo "The app, from anywhere:  https://${HOST}  (log in with your admin password)"
    echo
  fi
  echo "Resend webhook endpoint (event: email.received):"
  echo "  https://${HOST}/api/webhooks/resend"
  echo "SendGrid Inbound Parse destination (if you use SendGrid instead):"
  echo "  https://${USER_NAME}:${PASSWORD_HINT}@${HOST}/api/webhooks/sendgrid/inbound"
  echo
  exec ngrok http "$PORT" --url "$NGROK_URL"
else
  echo "No NGROK_URL set: ngrok will pick a random URL (shown as 'Forwarding' below) that changes every run."
  echo "Resend webhook endpoint = https://<forwarding-host>/api/webhooks/resend"
  echo
  exec ngrok http "$PORT"
fi
