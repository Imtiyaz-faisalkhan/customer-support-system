import api from './axios.js'

function errorMessage(err, fallback) {
  return err.response?.data?.error || fallback
}

export async function listTickets({ status, category, page = 0, size = 20 } = {}) {
  try {
    const { data } = await api.get('/tickets', {
      params: { status: status || undefined, category: category || undefined, page, size },
    })
    return data
  } catch (err) {
    throw new Error(errorMessage(err, 'Could not load tickets'))
  }
}

export async function getTicket(id) {
  try {
    const { data } = await api.get(`/tickets/${id}`)
    return data
  } catch (err) {
    throw new Error(errorMessage(err, 'Could not load ticket'))
  }
}

export async function updateTicketStatus(id, status) {
  try {
    const { data } = await api.patch(`/tickets/${id}/status`, { status })
    return data
  } catch (err) {
    throw new Error(errorMessage(err, 'Could not update status'))
  }
}

export async function replyToTicket(id, body) {
  try {
    const { data } = await api.post(`/tickets/${id}/replies`, { body })
    return data
  } catch (err) {
    throw new Error(errorMessage(err, 'Could not send reply'))
  }
}
