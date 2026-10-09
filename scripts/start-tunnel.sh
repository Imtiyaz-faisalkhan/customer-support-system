#!/usr/bin/env bash
# Exposes the app to the internet with ngrok: by default the frontend's production preview server (port 4173,
# `cd frontend && npm run start:public`), which serves only the built app AND forwards /api/** to the backend --
# so one public URL gives you the app from anywhere and lets Resend reach its webhook. See docs/EMAIL_SETUP.md.
# The dev server (5173) is deliberately not the default: it serves the whole frontend/ source tree.
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
PORT="${TUNNEL_PORT:-4173}"
if ! curl -s -o /dev/null http://localhost:8080/api/health; then
  echo "Warning: the backend isn't answering on http://localhost:8080 yet (scripts/run-backend-resend.sh)." >&2
fi
if [[ "$PORT" == "4173" ]] && ! curl -s -o /dev/null http://localhost:4173/; then
  echo "Warning: the frontend isn't answering on http://localhost:4173 yet (cd frontend && npm run start:public)." >&2
fi
if [[ "$PORT" == "5173" ]]; then
  echo "Warning: exposing the Vite dev server, which serves your frontend source files to anyone with the URL." >&2
fi

if [[ -n "${NGROK_URL:-}" ]]; then
  HOST="${NGROK_URL#https://}"
  if [[ "$PORT" != "8080" ]]; then
    echo "The app, from anywhere:  https://${HOST}  (log in with your admin password)"
    echo
  fi
  echo "Resend webhook endpoint (event: email.received):"
  echo "  https://${HOST}/api/webhooks/resend"
  echo
  echo "Start the backend with the same NGROK_URL (or PUBLIC_APP_URL=https://${HOST}),"
  echo "or logins through it fail with 403."
  echo
  exec ngrok http "$PORT" --url "$NGROK_URL"
else
  echo "No NGROK_URL set: ngrok will pick a random URL (shown as 'Forwarding' below) that changes every run."
  echo "  - Resend webhook endpoint = https://<forwarding-host>/api/webhooks/resend"
  echo "  - To log in through it, restart the backend with PUBLIC_APP_URL=https://<forwarding-host>;"
  echo "    otherwise logins fail with 403 (the address isn't an allowed origin)."
  echo
  exec ngrok http "$PORT"
fi
