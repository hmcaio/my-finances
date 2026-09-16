/**
 * Builds an id -> name lookup function from a list of `{ id, ... }` items, falling back to the id
 * itself if not found (e.g. while the reference list is still loading). Shared by
 * `TransactionsPage` and `AccountTransactionList` - both resolve category/account/payment method
 * ids to display names the same way.
 */
export function nameLookup<T extends { id: string }>(items: T[], getName: (item: T) => string) {
  const map = new Map(items.map((item) => [item.id, getName(item)]))
  return (id: string) => map.get(id) ?? id
}
