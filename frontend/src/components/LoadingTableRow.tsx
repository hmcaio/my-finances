import { CircularProgress, TableCell, TableRow } from '@mui/material'

interface LoadingTableRowProps {
  colSpan: number
  /** Existing call sites split between a spinner (settings-style pages) and plain "Loading…" text
   * (paginated lists) - preserved as-is rather than unified, since that's a visual decision beyond
   * this extraction's scope. Defaults to the spinner. */
  variant?: 'spinner' | 'text'
}

/**
 * The "still loading" placeholder row shown in a table body before its first fetch resolves,
 * previously copy-pasted into every list/table page with only `colSpan` differing.
 */
export function LoadingTableRow({ colSpan, variant = 'spinner' }: LoadingTableRowProps) {
  return (
    <TableRow>
      <TableCell colSpan={colSpan} align="center">
        {variant === 'spinner' ? <CircularProgress size={20} /> : 'Loading…'}
      </TableCell>
    </TableRow>
  )
}
