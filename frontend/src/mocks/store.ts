/**
 * In-memory row stores for the MSW handlers. Since F019 every successful mutation refetches the
 * active queries, so a handler that only echoed the request would make the refetch bring the
 * seed data back over a just-created row. Each store starts from a fresh copy of its seed and is
 * put back by `resetStores()` (called from `src/test/setup.ts` after every test).
 */
const resetters: Array<() => void> = []

export interface Store<T extends { id: string }> {
  list: () => T[]
  add: (row: T) => T
  replace: (id: string, update: (row: T) => T) => T | undefined
  remove: (id: string) => void
  find: (id: string) => T | undefined
  /** A fresh id such as `cat-new`, `cat-new-2`, ... for created rows. */
  nextId: (prefix: string) => string
}

export function createStore<T extends { id: string }>(seed: readonly T[]): Store<T> {
  let rows: T[] = structuredClone([...seed])
  let created = 0
  resetters.push(() => {
    rows = structuredClone([...seed])
    created = 0
  })
  return {
    list: () => rows,
    add: (row) => {
      rows = [...rows, row]
      return row
    },
    replace: (id, update) => {
      const current = rows.find((row) => row.id === id)
      if (!current) return undefined
      const next = update(current)
      rows = rows.map((row) => (row.id === id ? next : row))
      return next
    },
    remove: (id) => {
      rows = rows.filter((row) => row.id !== id)
    },
    find: (id) => rows.find((row) => row.id === id),
    nextId: (prefix) => {
      created += 1
      return created === 1 ? `${prefix}-new` : `${prefix}-new-${created}`
    },
  }
}

export function resetStores(): void {
  for (const reset of resetters) reset()
}
