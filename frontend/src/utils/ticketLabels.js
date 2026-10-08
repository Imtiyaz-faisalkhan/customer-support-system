// Display names for the backend enums (see TicketStatus/TicketCategory/MessageAuthor).
export const STATUS_LABELS = {
  OPEN: 'Open',
  AI_RESPONDED: 'AI Responded',
  PENDING_HUMAN: 'Pending Human',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
}

export const CATEGORY_LABELS = {
  BILLING: 'Billing',
  TECHNICAL: 'Technical',
  GENERAL: 'General',
}

export const AUTHOR_LABELS = {
  CUSTOMER: 'Customer',
  AI: 'AI Agent',
  ADMIN: 'Agent',
}

// OPEN and AI_RESPONDED are system-only; the backend rejects them from the admin.
export const ADMIN_SETTABLE_STATUSES = ['PENDING_HUMAN', 'IN_PROGRESS', 'RESOLVED']

export function formatDate(isoString) {
  return new Date(isoString).toLocaleDateString('en-US')
}

export function formatDateTime(isoString) {
  return new Date(isoString).toLocaleString('en-US')
}
