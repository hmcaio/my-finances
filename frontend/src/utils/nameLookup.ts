/**
 * Builds an id -> name lookup function from a list of `{ id, ... }` items, falling back to the id
 * itself if not found (e.g. while the reference list is still loading). Shared across every feature
 * that resolves category/account/payment method ids to display names the same way - moved here from
 * `features/transactions` since it outgrew being one feature's own file.
 */
export function nameLookup<T extends { id: string }>(items: T[], getName: (item: T) => string) {
  const map = new Map(items.map((item) => [item.id, getName(item)]))
  return (id: string) => map.get(id) ?? id
}
