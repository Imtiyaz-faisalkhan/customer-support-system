import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import TicketDetailPage from './TicketDetailPage.jsx'
import { getTicket, replyToTicket, updateTicketStatus } from '../api/tickets.js'
import { useAuth } from '../context/AuthContext.jsx'

vi.mock('../api/tickets.js', () => ({
  getTicket: vi.fn(),
  replyToTicket: vi.fn(),
  updateTicketStatus: vi.fn(),
}))

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))

const ticket = {
  id: 7,
  subject: 'Data export is missing orders',
  customerEmail: 'sophie@example.com',
  customerName: 'Sophie',
  status: 'PENDING_HUMAN',
  category: 'TECHNICAL',
  createdAt: '2026-10-04T10:00:00Z',
  updatedAt: '2026-10-04T10:00:00Z',
  messages: [{ id: 1, author: 'CUSTOMER', body: 'The export is missing orders.', createdAt: '2026-10-04T10:00:00Z' }],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard/tickets/7']}>
      <Routes>
        <Route path="/dashboard/tickets/:ticketId" element={<TicketDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('TicketDetailPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    useAuth.mockReturnValue({ user: { username: 'admin' } })
  })

  it('counts reply characters against the 1000 limit', async () => {
    getTicket.mockResolvedValue(ticket)
    renderPage()
    const textarea = await screen.findByLabelText('Reply as admin')

    expect(screen.getByText('0/1000')).toBeInTheDocument()
    expect(textarea).toHaveAttribute('maxLength', '1000')

    fireEvent.change(textarea, { target: { value: 'Hello' } })
    expect(screen.getByText('5/1000')).toBeInTheDocument()
  })

  it('ignores an older background refresh that finishes after a newer one', async () => {
    const reply = { id: 2, author: 'ADMIN', body: 'On it.', createdAt: '2026-10-04T11:00:00Z' }
    let resolveStaleRefresh
    getTicket
      .mockResolvedValueOnce(ticket) // initial load
      .mockReturnValueOnce(new Promise((resolve) => (resolveStaleRefresh = resolve))) // after status change: slow
      .mockResolvedValueOnce({ ...ticket, status: 'IN_PROGRESS', messages: [...ticket.messages, reply] }) // after reply
    // PATCH /status returns a TicketSummary: same fields minus the thread.
    const { messages: _thread, ...summary } = ticket
    updateTicketStatus.mockResolvedValue({ ...summary, status: 'IN_PROGRESS' })
    replyToTicket.mockResolvedValue(reply)
    renderPage()
    await screen.findByText('The export is missing orders.')

    fireEvent.click(screen.getByRole('button', { name: 'In Progress' }))
    await screen.findByRole('button', { name: 'Resolved' })
    fireEvent.change(screen.getByLabelText('Reply as admin'), { target: { value: 'On it.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send Reply' }))
    expect(await screen.findByText('On it.')).toBeInTheDocument()

    // The status change's refresh (taken before the reply existed) finally arrives - it must not win.
    await act(async () => resolveStaleRefresh(ticket))

    expect(screen.getByText('On it.')).toBeInTheDocument()
  })

  it('shows a sent reply even if the follow-up refresh fails', async () => {
    getTicket.mockResolvedValueOnce(ticket).mockRejectedValueOnce(new Error('Could not load ticket'))
    replyToTicket.mockResolvedValue({
      id: 2,
      author: 'ADMIN',
      body: 'Looking into it now.',
      createdAt: '2026-10-04T11:00:00Z',
    })
    renderPage()
    await screen.findByText('The export is missing orders.')

    fireEvent.change(screen.getByLabelText('Reply as admin'), { target: { value: 'Looking into it now.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send Reply' }))

    expect(await screen.findByText('Looking into it now.')).toBeInTheDocument()
    expect(screen.getByLabelText('Reply as admin')).toHaveValue('')
    expect(screen.queryByText('Could not load ticket')).not.toBeInTheDocument()
  })
})
