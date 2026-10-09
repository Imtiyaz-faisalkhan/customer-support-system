import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import PageLoadBoundary from './PageLoadBoundary.jsx'

function BrokenPage() {
  throw new Error('Failed to fetch dynamically imported module')
}

describe('PageLoadBoundary', () => {
  it('shows a reload message instead of a blank app when the page fails to load', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {}) // React logs caught render errors
    render(
      <PageLoadBoundary>
        <BrokenPage />
      </PageLoadBoundary>,
    )

    expect(screen.getByText('This page failed to load.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
    vi.restoreAllMocks()
  })
})
