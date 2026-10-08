import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import TicketDetailPage from './TicketDetailPage.jsx'
import { getTicket, replyToTicket } from '../api/tickets.js'
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
