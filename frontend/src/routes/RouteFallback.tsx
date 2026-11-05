import { Box, Skeleton } from '@mui/material'
import { fadeInSx } from '../components/feedback/fadeIn'

/** Suspense fallback while a route's chunk loads - same skeleton shape as the onboarding check. */
export function RouteFallback() {
  return (
    <Box sx={{ maxWidth: 720, mx: 'auto', px: 2, py: 6, ...fadeInSx }}>
      <Skeleton variant="text" width="60%" height={48} />
      <Skeleton variant="rounded" height={120} />
    </Box>
  )
}
