import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import TicketsPage from './TicketsPage.jsx'
import { listTickets } from '../api/tickets.js'

vi.mock('../api/tickets.js', () => ({
  listTickets: vi.fn(),
}))

const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 })

const ticket = {
  id: 5,
  subject: 'Charged twice for October',
  customerEmail: 'aisha.khan@example.com',
  customerName: 'Aisha Khan',
  status: 'PENDING_HUMAN',
  category: 'BILLING',
  createdAt: '2026-10-07T10:00:00Z',
  updatedAt: '2026-10-07T10:00:00Z',
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard/tickets']}>
      <TicketsPage />
    </MemoryRouter>,
  )
}

describe('TicketsPage', () => {
  beforeEach(() => {
    listTickets.mockReset()
  })

  it('renders tickets with readable status and category labels', async () => {
    listTickets.mockResolvedValue(page([ticket]))
    renderPage()

    expect(await screen.findByText('Charged twice for October')).toBeInTheDocument()
    expect(screen.getByText('Aisha Khan')).toBeInTheDocument()
    expect(screen.getByText('aisha.khan@example.com')).toBeInTheDocument()
    expect(screen.getByText('Pending Human', { selector: '.badge' })).toBeInTheDocument()
    expect(screen.getByText('Billing', { selector: '.badge' })).toBeInTheDocument()
  })

  it('refetches with the selected status filter', async () => {
    listTickets.mockResolvedValue(page([ticket]))
    renderPage()
    await screen.findByText('Charged twice for October')

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'RESOLVED' } })

    expect(listTickets).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'RESOLVED', page: 0 }))
  })

  it('hides the previous filter\'s rows while the new filter is loading', async () => {
    listTickets.mockResolvedValueOnce(page([ticket]))
    renderPage()
    await screen.findByText('Charged twice for October')

    listTickets.mockReturnValueOnce(new Promise(() => {})) // never resolves: request still in flight
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'RESOLVED' } })

    expect(await screen.findByText('Loading tickets...')).toBeInTheDocument()
    expect(screen.queryByText('Charged twice for October')).not.toBeInTheDocument()
  })

  it('shows an empty state when nothing matches', async () => {
    listTickets.mockResolvedValue(page([]))
    renderPage()

    expect(await screen.findByText('No tickets match these filters.')).toBeInTheDocument()
  })
})
