import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './ProtectedRoute.jsx'
import { useAuth } from '../context/AuthContext.jsx'

vi.mock('../context/AuthContext.jsx', () => ({
  useAuth: vi.fn(),
}))

function renderProtectedRoute() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route path="/login" element={<p>Login page</p>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<p>Protected content</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  it('shows a loading indicator while the session check is pending', () => {
    useAuth.mockReturnValue({ user: null, loading: true })
    renderProtectedRoute()
    expect(screen.getByText('Checking session...')).toBeInTheDocument()
  })

  it('redirects to /login when there is no authenticated user', () => {
    useAuth.mockReturnValue({ user: null, loading: false })
    renderProtectedRoute()
    expect(screen.getByText('Login page')).toBeInTheDocument()
  })

  it('renders the nested route when a user is authenticated', () => {
    useAuth.mockReturnValue({ user: { username: 'admin' }, loading: false })
    renderProtectedRoute()
    expect(screen.getByText('Protected content')).toBeInTheDocument()
  })
})
