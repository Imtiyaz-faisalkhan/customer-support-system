import { CATEGORY_LABELS, STATUS_LABELS } from '../utils/ticketLabels.js'

export function StatusBadge({ status }) {
  return <span className={`badge status-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>
}

export function CategoryBadge({ category }) {
  if (!category) {
    return <span className="badge category-none">Uncategorized</span>
  }
  return <span className={`badge category-${category.toLowerCase()}`}>{CATEGORY_LABELS[category]}</span>
}
