/**
 * The app's one transient confirmation (F040): a tiny external store, so `MutationCache.onSuccess`
 * (no React) can publish and `NotificationHost` (React) can read it through `useSyncExternalStore`.
 * It holds only the latest message; a new one replaces it, and every publish gets a fresh `id` so
 * a repeated text still shows again and the host restarts its timer.
 */
export interface Notification {
  id: number
  message: string
}

let current: Notification | null = null
let nextId = 1
const listeners = new Set<() => void>()

function notify(): void {
  listeners.forEach((listener) => listener())
}

/** Shows `message` as the current notification, replacing any earlier one. */
export function publish(message: string): void {
  current = { id: nextId++, message }
  notify()
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** The same object between publishes, as `useSyncExternalStore` requires. */
export function getSnapshot(): Notification | null {
  return current
}

/** Test-only: `src/test/setup.ts` calls it after each test so a message never leaks across tests. */
export function resetNotifications(): void {
  current = null
  notify()
}
