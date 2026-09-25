import type { Institution } from '../../api/institutions/institutions'

/**
 * Display order for institutions: the built-in "No institution" row first (it is the default and
 * the fallback, so it stays findable whatever it is renamed to), then the rest by name, ignoring
 * case. The backend already sorts by name, but a row renamed or added locally moves without a
 * refetch, so pages and pickers sort again rather than trusting arrival order.
 */
export function sortInstitutions(institutions: Institution[]): Institution[] {
  return [...institutions].sort((a, b) => {
    if (a.builtIn !== b.builtIn) return a.builtIn ? -1 : 1
    return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
  })
}
