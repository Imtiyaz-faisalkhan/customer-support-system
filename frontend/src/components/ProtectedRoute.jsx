import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

function ProtectedRoute() {
  const { user, loading } = useAuth()

  if (loading) return <p>Checking session...</p>
  if (!user) return <Navigate to="/login" replace />

  return <Outlet />
}

export default ProtectedRoute
