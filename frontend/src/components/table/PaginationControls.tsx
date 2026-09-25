import { Box, Button, Typography } from '@mui/material'

export interface PageInfo {
  number: number
  totalPages: number
}

interface PaginationControlsProps {
  pageInfo: PageInfo | null
  /** Same shape as `useState`'s setter - pass `setPage` directly so Previous/Next update against
   * React's latest `page` state rather than a possibly-stale `pageInfo` prop (matters on a rapid
   * double-click before the first page change's fetch has resolved). */
  onPageChange: (updater: (previousPage: number) => number) => void
  /** Spacing around the control row varied by call site before this was extracted (`mt: 2` when
   * embedded under a table with nothing following it, `mb: 3` on a standalone page with a form
   * below it) - defaults to the embedded-list spacing, override for the standalone-page one. */
  sx?: { mt?: number; mb?: number }
}

/**
 * Previous/"Page X of Y"/Next controls for a `PagedModel` response (F004 spec's paging envelope),
 * previously copy-pasted byte-for-byte into every paginated list. Renders nothing until `pageInfo`
 * is loaded or there's only one page - same guard every call site had inline.
 */
export function PaginationControls({ pageInfo, onPageChange, sx }: PaginationControlsProps) {
  if (!pageInfo || pageInfo.totalPages <= 1) return null
  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mt: 2, ...sx }}>
      <Button
        size="small"
        disabled={pageInfo.number <= 0}
        onClick={() => onPageChange((p) => Math.max(0, p - 1))}
      >
        Previous
      </Button>
      <Typography variant="body2">
        Page {pageInfo.number + 1} of {pageInfo.totalPages}
      </Typography>
      <Button
        size="small"
        disabled={pageInfo.number + 1 >= pageInfo.totalPages}
        onClick={() => onPageChange((p) => p + 1)}
      >
        Next
      </Button>
    </Box>
  )
}
