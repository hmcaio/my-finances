import type { ReactNode } from 'react'
import { Box, Skeleton, TableBody, TableCell, TableRow } from '@mui/material'
import { useDelayedFlag } from '../hooks/useDelayedFlag'
import { fadeInSx } from './fadeIn'

/** Cell widths cycled per row/column so the placeholder doesn't read as a grid of identical bars. */
const SKELETON_WIDTHS = ['70%', '55%', '80%', '45%', '65%']

/** Matches a `size="small"` IconButton wrapping a `fontSize="small"` icon (20px + 2 * 5px padding). */
const ICON_BUTTON_SIZE = 30

/** Screen-reader-only text; MUI's `visuallyHidden` lives in `@mui/utils`, which isn't a direct dependency. */
const VISUALLY_HIDDEN = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
} as const

interface DataTableBodyProps {
  /** True until the first fetch resolves, e.g. `rows === null && !error`. Show nothing pulsing once
   * a fetch has failed: the data stays null and the error banner already explains why. */
  loading: boolean
  /** Column count, so the placeholder rows line up with the header above. */
  columns: number
  /** Number of placeholder rows. */
  rows?: number
  /** The last column holds icon buttons; render round placeholders of the same size so the row
   * doesn't shrink and then jump taller when the real rows arrive. */
  actionsColumn?: boolean
  /** The real rows (and any empty-state row). Rendered once `loading` is false. */
  children: ReactNode
}

/**
 * A `<TableBody>` that shows skeleton rows during a table's first fetch and fades the real rows in
 * once it resolves. The skeleton only appears after a short delay (see `useDelayedFlag`), so a fast
 * response goes straight to the fade instead of flashing the placeholder.
 */
export function DataTableBody({
  loading,
  columns,
  rows = 5,
  actionsColumn = false,
  children,
}: DataTableBodyProps) {
  const showSkeleton = useDelayedFlag(loading)

  if (loading) {
    return (
      <TableBody aria-busy="true">
        {showSkeleton &&
          Array.from({ length: rows }, (_, row) => (
            <TableRow key={row}>
              {Array.from({ length: columns }, (_, column) => {
                const isActions = actionsColumn && column === columns - 1
                return (
                  <TableCell key={column} align={isActions ? 'right' : undefined}>
                    {row === 0 && column === 0 && (
                      <Box component="span" sx={VISUALLY_HIDDEN}>
                        Loading…
                      </Box>
                    )}
                    {isActions ? (
                      <ActionSkeletons />
                    ) : (
                      <Skeleton
                        variant="text"
                        width={SKELETON_WIDTHS[(row + column) % SKELETON_WIDTHS.length]}
                      />
                    )}
                  </TableCell>
                )
              })}
            </TableRow>
          ))}
      </TableBody>
    )
  }

  return <TableBody sx={fadeInSx}>{children}</TableBody>
}

function ActionSkeletons() {
  return (
    <span style={{ display: 'inline-flex', gap: 4 }}>
      <Skeleton variant="circular" width={ICON_BUTTON_SIZE} height={ICON_BUTTON_SIZE} />
      <Skeleton variant="circular" width={ICON_BUTTON_SIZE} height={ICON_BUTTON_SIZE} />
    </span>
  )
}
