#!/usr/bin/env bash
# Starts the backend connected to a real IONOS mailbox: new emails in the mailbox become tickets (checked every
# 30s) and replies are sent through IONOS. Asks for the mailbox password without echoing it, so it never ends up
# in your shell history or in any file. See docs/EMAIL_SETUP.md.
#
#   scripts/run-backend-ionos.sh support@yourdomain.com
#   IONOS_REGION=co.uk scripts/run-backend-ionos.sh support@yourdomain.co.uk     # ionos.co.uk / de / fr ... accounts
set -euo pipefail

ADDRESS="${1:-${SUPPORT_EMAIL_ADDRESS:-}}"
if [[ -z "$ADDRESS" ]]; then
  echo "Usage: $0 support@yourdomain.com" >&2
  exit 1
fi
REGION="${IONOS_REGION:-com}"

read -r -s -p "IONOS mailbox password for ${ADDRESS}: " MAILBOX_PASSWORD
echo

export SUPPORT_EMAIL_ADDRESS="$ADDRESS"
export SUPPORT_EMAIL_NAME="${SUPPORT_EMAIL_NAME:-Customer Support}"
export EMAIL_INBOUND=imap
export IMAP_HOST="imap.ionos.${REGION}" IMAP_PORT=993 IMAP_SSL=true
export IMAP_USERNAME="$ADDRESS" IMAP_PASSWORD="$MAILBOX_PASSWORD"
export EMAIL_OUTBOUND=smtp
export SMTP_HOST="smtp.ionos.${REGION}" SMTP_PORT=587 SMTP_AUTH=true SMTP_STARTTLS=true
export SMTP_USERNAME="$ADDRESS" SMTP_PASSWORD="$MAILBOX_PASSWORD"

echo "Mailbox: ${IMAP_HOST} (checked every ${IMAP_POLL_INTERVAL:-30s}) | sending via: ${SMTP_HOST}"
cd "$(dirname "$0")/../backend"
exec ./mvnw spring-boot:run
