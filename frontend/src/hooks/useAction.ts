import { useState } from 'react'
import { defaultErrorMessage } from '../api/core/apiError'

/**
 * A page's guarded write: `run(call)` clears the page banner, awaits `call` (a mutation's
 * `mutateAsync`), and on a throw puts `defaultErrorMessage(err)` in the banner instead of
 * propagating. `pending` is true while a call is in flight, so a page needs no hand-written
 * `saving`/`deleting` boolean.
 *
 * `run` resolves `true` on success and `false` on failure, so the caller decides what follows:
 * `if (await run(...)) cancelEdit()` keeps an editor open on failure, `await run(...);
 * setTarget(null)` closes a confirmation either way. `statusOverrides` is `defaultErrorMessage`'s
 * second argument (e.g. `{ 404: ... }` for a page that words one status itself).
 */
export function useAction(setError: (message: string | null) => void) {
  const [pending, setPending] = useState(false)

  async function run(
    call: () => Promise<unknown>,
    statusOverrides?: Record<number, string>,
  ): Promise<boolean> {
    setError(null)
    setPending(true)
    try {
      await call()
      return true
    } catch (err) {
      setError(defaultErrorMessage(err, statusOverrides))
      return false
    } finally {
      setPending(false)
    }
  }

  return { pending, run }
}
