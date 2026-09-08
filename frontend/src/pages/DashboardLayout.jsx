import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

function DashboardLayout() {
  const { logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="dashboard">
      <nav className="dashboard-nav">
        <Link to="/dashboard/home">Home</Link>
        <Link to="/dashboard/tickets">Tickets</Link>
        <button type="button" onClick={handleLogout}>
          Logout
        </button>
      </nav>
      <main className="dashboard-content">
        <Outlet />
      </main>
    </div>
  )
}

export default DashboardLayout
