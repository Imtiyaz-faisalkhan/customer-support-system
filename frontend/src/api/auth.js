import api from './axios.js'

export async function login(username, password) {
  try {
    const { data } = await api.post('/auth/login', { username, password })
    return data
  } catch (err) {
    throw new Error(err.response?.data?.error || 'Login failed')
  }
}

export async function logout() {
  await api.post('/auth/logout')
}

export async function me() {
  try {
    const { data } = await api.get('/auth/me')
    return data
  } catch (err) {
    if (err.response?.status === 401) return null
    throw err
  }
}
