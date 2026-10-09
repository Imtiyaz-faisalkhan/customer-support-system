#!/usr/bin/env bash
# Starts the backend wired to Resend: customer emails arrive via Resend's email.received webhook (needs the ngrok
# tunnel: scripts/start-tunnel.sh) and replies are sent through Resend's SMTP. Asks for the secrets without echoing
# them, so they never end up in shell history or files. See docs/EMAIL_SETUP.md.
#
#   scripts/run-backend-resend.sh help@support.yourdomain.com
#
# Logging in through the tunnel needs its address: set NGROK_URL (e.g. export it in your shell profile, so it
# stays out of the repo) or PUBLIC_APP_URL.
set -euo pipefail

ADDRESS="${1:-${SUPPORT_EMAIL_ADDRESS:-}}"
if [[ -z "$ADDRESS" ]]; then
  echo "Usage: $0 help@support.yourdomain.com" >&2
  exit 1
fi

read -r -s -p "Resend API key (re_...): " RESEND_API_KEY; echo
read -r -s -p "Resend webhook signing secret (whsec_...): " RESEND_WEBHOOK_SECRET; echo
# The tunnel puts the login page on the internet, so the default dev password is never acceptable here.
read -r -s -p "Admin password for the app (at least 12 characters): " ADMIN_PASSWORD; echo
if (( ${#ADMIN_PASSWORD} < 12 )); then
  echo "The admin password must be at least 12 characters." >&2
  exit 1
fi
read -r -s -p "Repeat the admin password: " ADMIN_PASSWORD_AGAIN; echo
if [[ "$ADMIN_PASSWORD" != "$ADMIN_PASSWORD_AGAIN" ]]; then
  echo "The passwords don't match." >&2
  exit 1
fi

read -r -s -p "Gemini API key for AI categorization + auto-reply (from Google AI Studio, empty = no AI): " GEMINI_API_KEY; echo

case "$RESEND_API_KEY" in re_*) ;; *) echo "That doesn't look like a Resend API key (should start with re_)." >&2; exit 1 ;; esac
case "$RESEND_WEBHOOK_SECRET" in whsec_*) ;; *) echo "The signing secret should start with whsec_." >&2; exit 1 ;; esac

export SUPPORT_EMAIL_ADDRESS="$ADDRESS"
export SUPPORT_EMAIL_NAME="${SUPPORT_EMAIL_NAME:-Customer Support}"
export RESEND_API_KEY RESEND_WEBHOOK_SECRET
export EMAIL_OUTBOUND=smtp
export SMTP_HOST=smtp.resend.com SMTP_PORT=587 SMTP_AUTH=true SMTP_STARTTLS=true
export SMTP_USERNAME=resend SMTP_PASSWORD="$RESEND_API_KEY"
export APP_ADMIN_PASSWORD="$ADMIN_PASSWORD"
if [[ -n "$GEMINI_API_KEY" ]]; then
  export GEMINI_API_KEY AI_CHAT_PROVIDER=google-genai
else
  # Explicitly off, even if AI_CHAT_PROVIDER was exported earlier in this terminal.
  unset GEMINI_API_KEY
  export AI_CHAT_PROVIDER=none
fi
export PUBLIC_APP_URL="${PUBLIC_APP_URL:-${NGROK_URL:-}}"
if [[ -z "$PUBLIC_APP_URL" ]]; then
  echo "Warning: NGROK_URL isn't set, so logging in through the tunnel will fail with 403 (localhost still works)." >&2
fi
# Real email goes out: no sample tickets, and never reply to reserved test domains like @example.com
# (those bounce and hurt the sending domain's reputation).
export DEMO_DATA_ENABLED=false REJECT_RESERVED_RECIPIENTS=true

echo "Support address: ${ADDRESS} | receiving: Resend webhook | sending: smtp.resend.com | AI: ${AI_CHAT_PROVIDER:-off}"
cd "$(dirname "$0")/../backend"
exec ./mvnw spring-boot:run
