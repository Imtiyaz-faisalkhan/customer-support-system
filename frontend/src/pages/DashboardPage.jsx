import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { getDashboardMetrics } from '../api/dashboard.js'
import { CategoryBadge, StatusBadge } from '../components/TicketBadges.jsx'
import { formatBucket, formatDuration, formatPercent } from '../utils/formatMetrics.js'

const PERIODS = {
  daily: { label: 'Daily', window: 'Last 30 days', bucket: 'Day' },
  weekly: { label: 'Weekly', window: 'Last 12 weeks', bucket: 'Week of' },
}

// Validated with the dataviz palette checker against the dashboard's white surface (CVD-safe pair).
const SERIES_1 = '#2a78d6'
const SERIES_2 = '#eb6834'

function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requested = searchParams.get('period')
  const period = Object.hasOwn(PERIODS, requested) ? requested : 'daily'

  // While a new period loads, the previous numbers stay on screen dimmed (no layout jump); `period` on the
  // response says which period they belong to. A failed load clears them, so another period's numbers are
  // never shown undimmed under this period's header.
  const [response, setResponse] = useState({ period: null, metrics: null, error: null })
  const [attempt, setAttempt] = useState(0)
  const refreshing = response.period !== period
  const error = response.period === period ? response.error : null

  useEffect(() => {
    let cancelled = false
    getDashboardMetrics(period)
      .then((metrics) => !cancelled && setResponse({ period, metrics, error: null }))
      .catch((err) => !cancelled && setResponse({ period, metrics: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [period, attempt])

  const selectPeriod = (next) => setSearchParams(next === 'daily' ? {} : { period: next })
  const retry = () => {
    setResponse({ period: null, metrics: null, error: null })
    setAttempt((n) => n + 1)
  }
  const { metrics } = response

  return (
    <section className="dashboard-page">
      <header className="dashboard-header">
        <div>
          <h1 className="page-title">Dashboard</h1>
          <p className="muted">{PERIODS[period].window} · tickets created in this window</p>
        </div>
        <div className="segmented" role="group" aria-label="Period">
          {Object.entries(PERIODS).map(([value, { label }]) => (
            <button
              key={value}
              type="button"
              aria-pressed={period === value}
              className={period === value ? 'active' : ''}
              onClick={() => selectPeriod(value)}
            >
              {label}
            </button>
          ))}
        </div>
      </header>

      {error && (
        <div className="error-banner error-with-action">
          <span>{error}</span>
          <button type="button" onClick={retry}>
            Retry
          </button>
        </div>
      )}
      {!metrics && !error && <p className="muted">Loading dashboard...</p>}

      {metrics && (
        <div className={`dashboard-body${refreshing ? ' is-refreshing' : ''}`} aria-busy={refreshing}>
          <KpiRow metrics={metrics} windowLabel={PERIODS[metrics.period.toLowerCase()].window} />

          <div className="card chart-card">
            <h2 className="chart-title">Tickets received</h2>
            <TicketsOverTime
              buckets={metrics.ticketsOverTime}
              bucketLabel={PERIODS[metrics.period.toLowerCase()].bucket}
            />
          </div>

          <div className="chart-row">
            <div className="card chart-card">
              <h2 className="chart-title">By status</h2>
              <BreakdownBars
                rows={metrics.statusCounts.map(({ status, count }) => ({
                  key: status,
                  label: <StatusBadge status={status} />,
                  count,
                }))}
              />
            </div>
            <div className="card chart-card">
              <h2 className="chart-title">By category</h2>
              <BreakdownBars
                rows={metrics.categoryCounts.map(({ category, count }) => ({
                  key: category ?? 'NONE',
                  label: <CategoryBadge category={category} />,
                  count,
                }))}
              />
            </div>
            <div className="card chart-card">
              <h2 className="chart-title">AI vs human handled</h2>
              <HandledSplit handled={metrics.handled} />
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

function KpiRow({ metrics, windowLabel }) {
  const { handled, resolution } = metrics
  const handledTotal = handled.ai + handled.human
  return (
    <div className="kpi-row">
      <StatTile label="Total tickets" value={metrics.totalTickets.toLocaleString()} detail={windowLabel} />
      <StatTile
        label="Avg response time"
        value={formatDuration(metrics.avgResponseSeconds)}
        detail="Time to first reply (AI or agent)"
      />
      <StatTile
        label="Resolution rate"
        value={formatPercent(resolution.rate)}
        detail={`${resolution.resolved} of ${resolution.total} resolved`}
      />
      <StatTile label="Handled by AI" value={handled.ai} detail={shareOfHandled(handled.ai, handledTotal)} />
      <StatTile label="Handled by human" value={handled.human} detail={shareOfHandled(handled.human, handledTotal)} />
    </div>
  )
}

// Share of handled tickets (AI + human); tickets still Open aren't counted either way.
function shareOfHandled(count, handledTotal) {
  return handledTotal === 0 ? 'Nothing handled yet' : `${formatPercent(count / handledTotal)} of handled tickets`
}

function StatTile({ label, value, detail }) {
  return (
    <div className="card stat-tile">
      <p className="stat-label">{label}</p>
      <p className="stat-value">{value}</p>
      <p className="stat-detail">{detail}</p>
    </div>
  )
}

function TicketsOverTime({ buckets, bucketLabel }) {
  const data = buckets.map(({ bucketStart, count }) => ({ label: formatBucket(bucketStart), count }))
  return (
    <>
      <div className="chart-frame">
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke="#eef0f2" />
            <XAxis
              dataKey="label"
              tick={{ fontSize: 11, fill: '#6b7280' }}
              tickLine={false}
              axisLine={{ stroke: '#d1d5db' }}
              interval="preserveStartEnd"
              minTickGap={16}
            />
            <YAxis
              allowDecimals={false}
              width={28}
              tick={{ fontSize: 11, fill: '#6b7280' }}
              tickLine={false}
              axisLine={false}
            />
            <Tooltip
              cursor={{ fill: 'rgba(42, 120, 214, 0.08)' }}
              content={<BucketTooltip bucketLabel={bucketLabel} />}
            />
            <Bar dataKey="count" fill={SERIES_1} radius={[4, 4, 0, 0]} maxBarSize={24} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <details className="chart-table">
        <summary>View as table</summary>
        <table>
          <thead>
            <tr>
              <th>{bucketLabel === 'Day' ? 'Day' : 'Week starting'}</th>
              <th className="numeric">Tickets</th>
            </tr>
          </thead>
          <tbody>
            {data.map(({ label, count }) => (
              <tr key={label}>
                <td>{label}</td>
                <td className="numeric">{count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </>
  )
}

function BucketTooltip({ active, payload, label, bucketLabel }) {
  if (!active || !payload?.length) return null
  const count = payload[0].value
  return (
    <div className="chart-tooltip">
      <strong>
        {count} {count === 1 ? 'ticket' : 'tickets'}
      </strong>
      <span>{bucketLabel === 'Day' ? label : `Week of ${label}`}</span>
    </div>
  )
}

// One-hue magnitude bars; identity comes from the badge label, the value sits at the bar tip.
function BreakdownBars({ rows }) {
  const max = Math.max(1, ...rows.map((row) => row.count))
  return (
    <ul className="breakdown">
      {rows.map(({ key, label, count }) => (
        <li key={key}>
          <span className="breakdown-label">{label}</span>
          <span className="breakdown-track">
            {count > 0 && (
              <span className="breakdown-bar" style={{ width: `${(count / max) * 100}%`, background: SERIES_1 }} />
            )}
            <span className="breakdown-value">{count}</span>
          </span>
        </li>
      ))}
    </ul>
  )
}

function HandledSplit({ handled }) {
  const handledTotal = handled.ai + handled.human
  const segments = [
    { key: 'ai', label: 'AI', count: handled.ai, color: SERIES_1 },
    { key: 'human', label: 'Human', count: handled.human, color: SERIES_2 },
  ]
  return (
    <div className="handled">
      {handledTotal === 0 ? (
        <p className="muted">No handled tickets in this window yet.</p>
      ) : (
        <div className="stacked-bar" role="img" aria-label={`${handled.ai} AI, ${handled.human} human`}>
          {segments
            .filter((segment) => segment.count > 0)
            .map((segment) => (
              <span
                key={segment.key}
                className="stacked-segment"
                style={{ flexGrow: segment.count, background: segment.color }}
                title={`${segment.label}: ${segment.count}`}
              />
            ))}
        </div>
      )}
      <ul className="legend">
        {segments.map((segment) => (
          <li key={segment.key}>
            <span className="legend-swatch" style={{ background: segment.color }} />
            <span className="legend-label">{segment.label}</span>
            <span className="legend-value">
              {segment.count}
              {handledTotal > 0 && <span className="muted"> · {formatPercent(segment.count / handledTotal)}</span>}
            </span>
          </li>
        ))}
      </ul>
      <p className="handled-note muted">
        {handled.unhandled} {handled.unhandled === 1 ? 'ticket' : 'tickets'} still Open, not handled yet (not
        counted). Escalated tickets count as human.
      </p>
    </div>
  )
}

export default DashboardPage
