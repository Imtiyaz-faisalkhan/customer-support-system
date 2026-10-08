import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { login as apiLogin, logout as apiLogout, me as apiMe } from '../api/auth.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    apiMe()
      .then(setUser)
      .catch((err) => {
        console.error('Failed to check session', err)
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    const handleUnauthorized = () => setUser(null)
    window.addEventListener('auth:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized)
  }, [])

  const login = useCallback(async (username, password) => {
    const result = await apiLogin(username, password)
    setUser(result)
    return result
  }, [])

  const logout = useCallback(async () => {
    try {
      await apiLogout()
    } catch (err) {
      console.error('Logout request failed, clearing local session anyway', err)
    } finally {
      setUser(null)
    }
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
