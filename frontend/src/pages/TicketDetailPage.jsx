import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getTicket, replyToTicket, updateTicketStatus } from '../api/tickets.js'
import { useAuth } from '../context/AuthContext.jsx'
import { CategoryBadge, StatusBadge } from '../components/TicketBadges.jsx'
import {
  ADMIN_SETTABLE_STATUSES,
  AUTHOR_LABELS,
  STATUS_LABELS,
  formatDateTime,
} from '../utils/ticketLabels.js'

function TicketDetailPage() {
  const { ticketId } = useParams()
  const { user } = useAuth()
  // Single-admin system: every ADMIN message was written by the logged-in admin, so label it with their name.
  const adminName = user?.username || AUTHOR_LABELS.ADMIN
  const authorLabel = (author) => (author === 'ADMIN' ? adminName : AUTHOR_LABELS[author])
  const [ticket, setTicket] = useState(null)
  const [loadError, setLoadError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [reply, setReply] = useState('')
  const [sending, setSending] = useState(false)
  const [updatingStatus, setUpdatingStatus] = useState(false)

  useEffect(() => {
    let cancelled = false
    getTicket(ticketId)
      .then((data) => {
        if (cancelled) return
        setTicket(data)
        setLoadError(null)
      })
      .catch((err) => !cancelled && setLoadError(err.message))
    return () => {
      cancelled = true
    }
  }, [ticketId])

  // After a successful action, its own response is applied immediately; this re-fetch only fills in the
  // fields it doesn't return (e.g. status/updatedAt after a reply). A failure here must not look like the
  // action failed (a reply has already been emailed), so it's ignored and the page keeps what it has.
  const refreshQuietly = () =>
    getTicket(ticketId)
      .then(setTicket)
      .catch(() => {})

  const handleStatusChange = async (status) => {
    setActionError(null)
    setUpdatingStatus(true)
    try {
      const summary = await updateTicketStatus(ticketId, status)
      setTicket((current) => ({ ...current, ...summary }))
    } catch (err) {
      setActionError(err.message)
      return
    } finally {
      setUpdatingStatus(false)
    }
    refreshQuietly()
  }

  const handleReply = async (event) => {
    event.preventDefault()
    if (!reply.trim()) return
    setActionError(null)
    setSending(true)
    try {
      const message = await replyToTicket(ticketId, reply)
      setReply('')
      setTicket((current) => ({ ...current, messages: [...current.messages, message] }))
    } catch (err) {
      setActionError(err.message)
      return
    } finally {
      setSending(false)
    }
    refreshQuietly()
  }

  if (loadError) {
    return (
      <section>
        <Link to="/dashboard/tickets" className="back-link">
          ← Back to Tickets
        </Link>
        <p className="error-banner">{loadError}</p>
      </section>
    )
  }

  if (!ticket) {
    return <p className="muted">Loading ticket...</p>
  }

  // The current status is shown as a badge; only offer moves to a different, admin-settable status.
  const statusOptions = ADMIN_SETTABLE_STATUSES.filter((status) => status !== ticket.status)

  return (
    <section className="ticket-detail">
      <Link to="/dashboard/tickets" className="back-link">
        ← Back to Tickets
      </Link>

      <div className="card detail-card">
        <h1 className="detail-title">{ticket.subject}</h1>
        <div className="badge-row">
          <StatusBadge status={ticket.status} />
          <CategoryBadge category={ticket.category} />
        </div>
        <dl className="detail-meta">
          <div>
            <dt>From:</dt>
            <dd>
              {ticket.customerName
                ? `${ticket.customerName} (${ticket.customerEmail})`
                : ticket.customerEmail}
            </dd>
          </div>
          <div>
            <dt>Created:</dt>
            <dd>{formatDateTime(ticket.createdAt)}</dd>
          </div>
          <div>
            <dt>Last updated:</dt>
            <dd>{formatDateTime(ticket.updatedAt)}</dd>
          </div>
        </dl>
      </div>

      <div className="card detail-card">
        <h2 className="section-label">Change Status</h2>
        <div className="status-options">
          {statusOptions.map((status) => (
            <button
              key={status}
              type="button"
              className={`status-option status-option-${status.toLowerCase()}`}
              disabled={updatingStatus}
              onClick={() => handleStatusChange(status)}
            >
              {STATUS_LABELS[status]}
            </button>
          ))}
        </div>
      </div>

      {actionError && <p className="error-banner">{actionError}</p>}

      <div className="card detail-card">
        <h2 className="detail-section-title">Conversation</h2>
        <ol className="thread">
          {ticket.messages.map((message) => (
            <li key={message.id} className={`message message-${message.author.toLowerCase()}`}>
              <p className="message-bubble">{message.body}</p>
              <div className="message-meta">
                <span className="message-author">{authorLabel(message.author)}</span>
                <time dateTime={message.createdAt}>{formatDateTime(message.createdAt)}</time>
              </div>
            </li>
          ))}
        </ol>
      </div>

      <form className="card detail-card reply-form" onSubmit={handleReply}>
        <label htmlFor="reply">Reply as {adminName}</label>
        <textarea
          id="reply"
          rows={4}
          value={reply}
          onChange={(event) => setReply(event.target.value)}
        />
        <div className="reply-actions">
          <button type="submit" className="primary-button" disabled={sending || !reply.trim()}>
            {sending ? 'Sending...' : 'Send Reply'}
          </button>
        </div>
      </form>
    </section>
  )
}

export default TicketDetailPage
