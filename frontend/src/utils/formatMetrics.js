const EMPTY = '—'

// 45s, 12m, 2h 15m, 3d 4h; the two largest units are enough at dashboard scale.
export function formatDuration(totalSeconds) {
  if (totalSeconds == null) return EMPTY
  const units = [
    ['d', 86400],
    ['h', 3600],
    ['m', 60],
    ['s', 1],
  ]
  let remaining = Math.max(0, Math.round(totalSeconds))
  const parts = []
  for (const [suffix, size] of units) {
    const value = Math.floor(remaining / size)
    remaining -= value * size
    if (value > 0 || (parts.length === 0 && suffix === 's')) parts.push(`${value}${suffix}`)
    if (parts.length === 2) break
  }
  return parts.join(' ')
}

export function formatPercent(rate) {
  return rate == null ? EMPTY : `${Math.round(rate * 100)}%`
}

// bucketStart is a server-local calendar date ("2026-10-06"); parse it as a local date, not UTC midnight.
export function formatBucket(bucketStart) {
  const [year, month, day] = bucketStart.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}
