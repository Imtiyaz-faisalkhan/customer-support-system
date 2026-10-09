import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from './DashboardPage.jsx'
import { getDashboardMetrics } from '../api/dashboard.js'

vi.mock('../api/dashboard.js', () => ({
  getDashboardMetrics: vi.fn(),
}))

const metrics = {
  period: 'DAILY',
  from: '2026-09-09T00:00:00Z',
  to: '2026-10-08T12:00:00Z',
  totalTickets: 9,
  ticketsOverTime: [
    { bucketStart: '2026-10-07', count: 4 },
    { bucketStart: '2026-10-08', count: 5 },
  ],
  statusCounts: [
    { status: 'OPEN', count: 2 },
    { status: 'AI_RESPONDED', count: 2 },
    { status: 'PENDING_HUMAN', count: 2 },
    { status: 'IN_PROGRESS', count: 1 },
    { status: 'RESOLVED', count: 2 },
  ],
  categoryCounts: [
    { category: 'BILLING', count: 3 },
    { category: 'TECHNICAL', count: 2 },
    { category: 'GENERAL', count: 2 },
    { category: null, count: 2 },
  ],
  handled: { ai: 4, human: 3, unhandled: 2 },
  avgResponseSeconds: 8100,
  resolution: { resolved: 2, total: 9, rate: 2 / 9 },
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dashboard/home']}>
      <DashboardPage />
    </MemoryRouter>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    getDashboardMetrics.mockReset()
  })

  it('renders the KPI tiles from the metrics response', async () => {
    getDashboardMetrics.mockResolvedValue(metrics)
    renderPage()

    expect(await screen.findByText('2h 15m')).toBeInTheDocument()
    expect(screen.getByText('9')).toBeInTheDocument()
    expect(screen.getByText('22%')).toBeInTheDocument()
    expect(screen.getByText('2 of 9 resolved')).toBeInTheDocument()
    // 4 AI + 3 human = 7 handled tickets; the 2 still Open aren't part of either share.
    expect(screen.getByText('57% of handled tickets')).toBeInTheDocument()
    expect(screen.getByText('43% of handled tickets')).toBeInTheDocument()
    expect(screen.getByText('2 tickets still Open', { exact: false })).toBeInTheDocument()
    expect(getDashboardMetrics).toHaveBeenCalledWith('daily')
  })

  it('drops the previous period\'s numbers when the new period fails, and Retry refetches it', async () => {
    getDashboardMetrics.mockResolvedValueOnce(metrics).mockRejectedValueOnce(new Error('Server down'))
    renderPage()
    await screen.findByText('2h 15m')

    fireEvent.click(screen.getByRole('button', { name: 'Weekly' }))

    expect(await screen.findByText('Server down')).toBeInTheDocument()
    expect(screen.queryByText('2h 15m')).not.toBeInTheDocument()

    getDashboardMetrics.mockResolvedValueOnce({ ...metrics, period: 'WEEKLY' })
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))

    expect(await screen.findByText('2h 15m')).toBeInTheDocument()
    expect(getDashboardMetrics).toHaveBeenLastCalledWith('weekly')
    expect(screen.queryByText('Server down')).not.toBeInTheDocument()
  })

  it('falls back to daily for an unknown ?period value', async () => {
    getDashboardMetrics.mockResolvedValue(metrics)
    render(
      <MemoryRouter initialEntries={['/dashboard/home?period=constructor']}>
        <DashboardPage />
      </MemoryRouter>,
    )

    await screen.findByText('2h 15m')
    expect(getDashboardMetrics).toHaveBeenCalledWith('daily')
  })

  it('refetches with the weekly period when the toggle is switched', async () => {
    getDashboardMetrics.mockResolvedValue(metrics)
    renderPage()
    await screen.findByText('2h 15m')

    fireEvent.click(screen.getByRole('button', { name: 'Weekly' }))

    expect(getDashboardMetrics).toHaveBeenLastCalledWith('weekly')
    expect(screen.getByRole('button', { name: 'Weekly' })).toHaveAttribute('aria-pressed', 'true')
  })
})
