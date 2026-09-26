import { Box, Button, IconButton, Typography } from '@mui/material'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import { useIsMobile } from '../../hooks/useBreakpointBand'

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
 * previously copy-pasted byte-for-byte into every paginated list. Compact below `sm` (icon buttons; see above). Renders nothing until `pageInfo`
 * is loaded or there's only one page - same guard every call site had inline.
 */
export function PaginationControls({ pageInfo, onPageChange, sx }: PaginationControlsProps) {
  const compact = useIsMobile()
  if (!pageInfo || pageInfo.totalPages <= 1) return null
  if (compact) {
    // Below sm (F021): icon buttons either side of the label, spread across the row so each is an
    // easy touch target. There is no page-size selector in this component to hide.
    return (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          mt: 2,
          ...sx,
        }}
      >
        <IconButton
          aria-label="Previous"
          disabled={pageInfo.number <= 0}
          onClick={() => onPageChange((p) => Math.max(0, p - 1))}
        >
          <ChevronLeftIcon />
        </IconButton>
        <Typography variant="body2">
          Page {pageInfo.number + 1} of {pageInfo.totalPages}
        </Typography>
        <IconButton
          aria-label="Next"
          disabled={pageInfo.number + 1 >= pageInfo.totalPages}
          onClick={() => onPageChange((p) => p + 1)}
        >
          <ChevronRightIcon />
        </IconButton>
      </Box>
    )
  }
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
