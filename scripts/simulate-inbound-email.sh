#!/usr/bin/env bash
# Simulates SendGrid Inbound Parse: POSTs a customer email to the backend's webhook exactly the way SendGrid
# would (multipart/form-data, Basic auth). No SendGrid account needed. See docs/EMAIL_SETUP.md.
#
# New ticket:
#   scripts/simulate-inbound-email.sh --from "Jane Doe <jane@example.com>" --subject "Refund please" --text "I was charged twice."
# Customer replying to the support email they received (copy its Message-ID from Mailpit -> "Headers"):
#   scripts/simulate-inbound-email.sh --from "Jane Doe <jane@example.com>" --subject "Re: Refund please [#12]" \
#       --text "Still not refunded." --in-reply-to "<abc-123@example.com>"
set -euo pipefail

URL="${WEBHOOK_URL:-http://localhost:8080/api/webhooks/sendgrid/inbound}"
USER_NAME="${INBOUND_WEBHOOK_USERNAME:-sendgrid}"
PASSWORD="${INBOUND_WEBHOOK_PASSWORD:-local-dev-webhook-secret}"
FROM="Jane Doe <jane@example.com>"
SUBJECT="Help with my order"
TEXT="Hi, I need help with my order."
IN_REPLY_TO=""
MESSAGE_ID="<sim-$(date +%s)-$RANDOM@example.com>"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --from) FROM="$2"; shift 2 ;;
    --subject) SUBJECT="$2"; shift 2 ;;
    --text) TEXT="$2"; shift 2 ;;
    --in-reply-to) IN_REPLY_TO="$2"; shift 2 ;;
    --message-id) MESSAGE_ID="$2"; shift 2 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1 (see --help)" >&2; exit 1 ;;
  esac
done

HEADERS="From: ${FROM}
To: support@example.com
Subject: ${SUBJECT}
Message-ID: ${MESSAGE_ID}"
if [[ -n "$IN_REPLY_TO" ]]; then
  HEADERS="${HEADERS}
In-Reply-To: ${IN_REPLY_TO}
References: ${IN_REPLY_TO}"
fi

echo "Sending simulated email ${MESSAGE_ID} from ${FROM}"
curl --silent --show-error --fail-with-body -u "${USER_NAME}:${PASSWORD}" "$URL" \
  -F "from=${FROM}" \
  -F "to=support@example.com" \
  -F "subject=${SUBJECT}" \
  -F "text=${TEXT}" \
  -F "headers=${HEADERS}"
echo
