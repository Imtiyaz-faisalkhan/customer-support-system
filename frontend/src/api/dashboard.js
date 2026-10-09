import api from './axios.js'

export async function getDashboardMetrics(period = 'daily') {
  try {
    const { data } = await api.get('/dashboard/metrics', { params: { period: period.toUpperCase() } })
    return data
  } catch (err) {
    throw new Error(err.response?.data?.error || 'Could not load dashboard metrics')
  }
}
