import { NavLink, Outlet, useNavigate } from 'react-router-dom'
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
        <span className="dashboard-brand">Support Admin</span>
        <NavLink to="/dashboard/home">Home</NavLink>
        <NavLink to="/dashboard/tickets">Tickets</NavLink>
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
