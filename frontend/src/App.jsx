import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './pages/LoginPage.jsx'
import DashboardLayout from './pages/DashboardLayout.jsx'
import TicketsPage from './pages/TicketsPage.jsx'
import TicketDetailPage from './pages/TicketDetailPage.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import PageLoadBoundary from './components/PageLoadBoundary.jsx'
import './App.css'

// Lazy so the charting library (Recharts) isn't in the bundle the login page has to download.
const DashboardPage = lazy(() => import('./pages/DashboardPage.jsx'))

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/dashboard" element={<DashboardLayout />}>
          <Route index element={<Navigate to="home" replace />} />
          <Route
            path="home"
            element={
              <PageLoadBoundary>
                <Suspense fallback={<p className="muted">Loading dashboard...</p>}>
                  <DashboardPage />
                </Suspense>
              </PageLoadBoundary>
            }
          />
          <Route path="tickets" element={<TicketsPage />} />
          <Route path="tickets/:ticketId" element={<TicketDetailPage />} />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export default App
