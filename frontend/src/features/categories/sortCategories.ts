import type { Category } from '../../api/categories/categories'

/**
 * Display order for categories: expenses before income, and within a type the built-in fallback
 * row first (it is the row to reach for when nothing else fits, so it stays findable whatever it
 * is renamed to), then the rest by name, ignoring case. The backend list has no defined order, and
 * a row renamed or added locally moves without a refetch, so the page sorts on every render.
 */
export function sortCategories(categories: Category[]): Category[] {
  return [...categories].sort((a, b) => {
    if (a.type !== b.type) return a.type === 'EXPENSE' ? -1 : 1
    if (a.builtIn !== b.builtIn) return a.builtIn ? -1 : 1
    return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
  })
}
