import { Box, Button, Typography } from '@mui/material'

interface LoadFailedNoticeProps {
  /** What failed to load, e.g. the fetch's error message. */
  message: string
  onRetry: () => void
}

/**
 * Inline "this section could not load" feedback with a Retry button. Unlike the dismissible
 * `ErrorAlert` banner, it stays in place until a retry succeeds.
 */
export function LoadFailedNotice({ message, onRetry }: LoadFailedNoticeProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1 }}>
      <Typography color="text.secondary">Could not load data ({message}).</Typography>
      <Button size="small" onClick={onRetry}>
        Retry
      </Button>
    </Box>
  )
}
