import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

/**
 * Reads a value the page was navigated to with (`navigate(path, { state })`), once on mount, and
 * clears the navigation state straight away so a later back-navigation to this history entry does
 * not replay it. The state is dropped with `replace: true` and the path and search kept, so no
 * history entry is added.
 *
 * `read` picks the value out of the (untyped) state and returns `undefined` when it is absent. The
 * value is read synchronously in the initial state, so it can seed another `useState` (a dialog
 * that opens on the first render), and stays the same for the life of the component.
 */
export function useConsumeNavigationState<T>(
  read: (state: unknown) => T | undefined,
): T | undefined {
  const location = useLocation()
  const navigate = useNavigate()
  const [consumed] = useState(() => read(location.state))
  const cleared = useRef(false)

  useEffect(() => {
    if (consumed === undefined || cleared.current) return
    cleared.current = true
    navigate(
      { pathname: location.pathname, search: location.search },
      { replace: true, state: null },
    )
  }, [consumed, navigate, location.pathname, location.search])

  return consumed
}
