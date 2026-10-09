#!/usr/bin/env bash
# Starts the backend wired to Resend: customer emails arrive via Resend's email.received webhook (needs the ngrok
# tunnel: scripts/start-tunnel.sh) and replies are sent through Resend's SMTP. Asks for the secrets without echoing
# them, so they never end up in shell history or files. See docs/EMAIL_SETUP.md.
#
#   scripts/run-backend-resend.sh help@support.yourdomain.com
set -euo pipefail

ADDRESS="${1:-${SUPPORT_EMAIL_ADDRESS:-}}"
if [[ -z "$ADDRESS" ]]; then
  echo "Usage: $0 help@support.yourdomain.com" >&2
  exit 1
fi

read -r -s -p "Resend API key (re_...): " RESEND_API_KEY; echo
read -r -s -p "Resend webhook signing secret (whsec_...): " RESEND_WEBHOOK_SECRET; echo
read -r -s -p "New admin password for the app (empty = keep the default): " ADMIN_PASSWORD; echo

case "$RESEND_API_KEY" in re_*) ;; *) echo "That doesn't look like a Resend API key (should start with re_)." >&2; exit 1 ;; esac
case "$RESEND_WEBHOOK_SECRET" in whsec_*) ;; *) echo "The signing secret should start with whsec_." >&2; exit 1 ;; esac

export SUPPORT_EMAIL_ADDRESS="$ADDRESS"
export SUPPORT_EMAIL_NAME="${SUPPORT_EMAIL_NAME:-Customer Support}"
export EMAIL_INBOUND=webhook
export RESEND_API_KEY RESEND_WEBHOOK_SECRET
export EMAIL_OUTBOUND=smtp
export SMTP_HOST=smtp.resend.com SMTP_PORT=587 SMTP_AUTH=true SMTP_STARTTLS=true
export SMTP_USERNAME=resend SMTP_PASSWORD="$RESEND_API_KEY"
if [[ -n "$ADMIN_PASSWORD" ]]; then
  export APP_ADMIN_PASSWORD="$ADMIN_PASSWORD"
fi

echo "Support address: ${ADDRESS} | receiving: Resend webhook | sending: smtp.resend.com"
cd "$(dirname "$0")/../backend"
exec ./mvnw spring-boot:run
