import type { PropsWithChildren } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Box, Skeleton } from '@mui/material'
import { useSession } from '../../api/auth/authQueries'
import { LoadFailedNotice } from '../../components/feedback/LoadFailedNotice'
import { fadeInSx } from '../../components/feedback/fadeIn'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { defaultErrorMessage } from '../../api/core/apiError'

/** The router `state` the guard attaches to its redirect so the login page can send the user back. */
export interface LoginRedirectState {
  from?: string
}

/**
 * Route guard (F036, ADR 0029): nothing under it renders - and so no data hook fires - until the
 * backend confirms a session. Signed out redirects to `/login`, remembering the path (and query)
 * that was asked for; a 401 from any later API call flips the same cached session entry (see
 * `createQueryClient`), so a session that expires mid-use lands here too.
 *
 * A failed session check that is *not* a 401 (backend down) is not "signed out": it gets a retry
 * notice instead of bouncing the user to a login form that could not work either.
 */
export function RequireAuth({ children }: PropsWithChildren) {
  const session = useSession()
  const location = useLocation()
  const showSkeleton = useDelayedFlag(session.isPending)

  if (session.data?.authenticated) return <>{children}</>
  if (session.data && !session.data.authenticated) {
    const state: LoginRedirectState = { from: `${location.pathname}${location.search}` }
    return <Navigate to="/login" replace state={state} />
  }
  return (
    <Box sx={{ maxWidth: 720, mx: 'auto', px: 2, py: 6 }}>
      {session.isError ? (
        <LoadFailedNotice
          message={defaultErrorMessage(session.error)}
          onRetry={() => void session.refetch()}
        />
      ) : (
        showSkeleton && (
          <Box sx={fadeInSx}>
            <Skeleton variant="text" width="60%" height={48} />
            <Skeleton variant="rounded" height={120} />
          </Box>
        )
      )}
    </Box>
  )
}
