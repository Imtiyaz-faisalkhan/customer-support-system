import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

// Central place for handling errors common to every request. A 401 means the
// session is missing/expired; broadcast it so AuthContext can clear its state
// and let ProtectedRoute redirect to /login, no matter which call triggered it.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new CustomEvent('auth:unauthorized'))
    }
    return Promise.reject(error)
  },
)

export default api
