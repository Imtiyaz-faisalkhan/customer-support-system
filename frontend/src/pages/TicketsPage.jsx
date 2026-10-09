import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { listTickets } from '../api/tickets.js'
import { CategoryBadge, StatusBadge } from '../components/TicketBadges.jsx'
import { CATEGORY_LABELS, STATUS_LABELS, formatDate } from '../utils/ticketLabels.js'

const PAGE_SIZE = 20

function TicketsPage() {
  // Filters live in the URL so they survive a refresh and the back button from a ticket.
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') || ''
  const category = searchParams.get('category') || ''
  const page = Math.max(0, Math.floor(Number(searchParams.get('page'))) || 0)

  // Each response is tagged with the query that produced it; anything for a different query is stale and
  // hidden, so changing a filter shows "Loading" instead of the previous filter's rows (or an old error).
  const query = `${status}|${category}|${page}`
  const [response, setResponse] = useState({ query: null, result: null, error: null })
  const result = response.query === query ? response.result : null
  const error = response.query === query ? response.error : null
  const navigate = useNavigate()

  useEffect(() => {
    let cancelled = false
    const requested = `${status}|${category}|${page}`
    listTickets({ status, category, page, size: PAGE_SIZE })
      .then((data) => !cancelled && setResponse({ query: requested, result: data, error: null }))
      .catch((err) => !cancelled && setResponse({ query: requested, result: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [status, category, page])

  const updateParams = (changes) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(changes).forEach(([key, value]) => {
      if (value === '' || value === 0) next.delete(key)
      else next.set(key, value)
    })
    setSearchParams(next)
  }

  return (
    <section className="tickets-page">
      <h1 className="page-title">Tickets</h1>
      <div className="filters">
        <select
          aria-label="Status"
          value={status}
          onChange={(e) => updateParams({ status: e.target.value, page: 0 })}
        >
          <option value="">All Statuses</option>
          {Object.entries(STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select
          aria-label="Category"
          value={category}
          onChange={(e) => updateParams({ category: e.target.value, page: 0 })}
        >
          <option value="">All Categories</option>
          {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      {error && <p className="error-banner">{error}</p>}
      {!result && !error && <p className="muted">Loading tickets...</p>}

      {result && result.content.length === 0 && (
        <div className="empty-state">No tickets match these filters.</div>
      )}

      {result && result.content.length > 0 && (
        <div className="card tickets-card">
          <table className="tickets-table">
            <thead>
              <tr>
                <th>Customer</th>
                <th>Subject</th>
                <th>Status</th>
                <th>Category</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {result.content.map((ticket) => (
                <tr key={ticket.id} onClick={() => navigate(`/dashboard/tickets/${ticket.id}`)}>
                  <td>
                    <div className="customer-name">{ticket.customerName || ticket.customerEmail}</div>
                    <div className="customer-email">{ticket.customerEmail}</div>
                  </td>
                  <td>
                    <Link
                      to={`/dashboard/tickets/${ticket.id}`}
                      className="ticket-subject"
                      onClick={(e) => e.stopPropagation()}
                    >
                      {ticket.subject}
                    </Link>
                  </td>
                  <td>
                    <StatusBadge status={ticket.status} />
                  </td>
                  <td>
                    <CategoryBadge category={ticket.category} />
                  </td>
                  <td className="ticket-date" title={new Date(ticket.createdAt).toLocaleString()}>
                    {formatDate(ticket.createdAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {result && result.totalPages > 1 && (
        <nav className="pagination">
          <button type="button" disabled={page === 0} onClick={() => updateParams({ page: page - 1 })}>
            Previous
          </button>
          <span>
            Page {page + 1} of {result.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= result.totalPages}
            onClick={() => updateParams({ page: page + 1 })}
          >
            Next
          </button>
        </nav>
      )}
    </section>
  )
}

export default TicketsPage
