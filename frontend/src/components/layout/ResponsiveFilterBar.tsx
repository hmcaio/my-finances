import { useState, type ReactNode } from 'react'
import { Badge, Box, Button, DialogActions, DialogContent, DialogTitle } from '@mui/material'
import FilterListIcon from '@mui/icons-material/FilterList'
import { useIsMobile } from '../../hooks/useBreakpointBand'
import { ResponsiveDialog } from '../feedback/ResponsiveDialog'

interface ResponsiveFilterBarProps {
  /** The filter controls (controlled by the page, as before). They render once: inline from `sm`
   * up, inside the sheet below it, so ids and labels never duplicate. */
  children: ReactNode
  /** How many filters differ from their default; shown as the badge on the mobile button. */
  activeCount: number
  /** Resets the filters. Offered as "Clear" (inline when something is active, and in the sheet). */
  onClear?: () => void
}

/**
 * Filter row for list pages (F021). From `sm` up: the controls in an inline wrapping bar, exactly
 * like the old row. Below `sm`: a "Filters" button with an active-count badge that opens a
 * full-screen sheet holding the same controls. Filters apply as they change (the page owns their
 * state), so the sheet has no Apply step: "Done" just closes it, "Clear" calls `onClear`.
 */
export function ResponsiveFilterBar({ children, activeCount, onClear }: ResponsiveFilterBarProps) {
  const isMobile = useIsMobile()
  const [open, setOpen] = useState(false)

  if (!isMobile) {
    return (
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'center', mb: 2 }}>
        {children}
        {onClear && activeCount > 0 && <Button onClick={onClear}>Clear filters</Button>}
      </Box>
    )
  }

  return (
    <Box sx={{ mb: 2 }}>
      <Button
        variant="outlined"
        startIcon={
          <Badge badgeContent={activeCount} color="primary">
            <FilterListIcon />
          </Badge>
        }
        aria-label={activeCount > 0 ? `Filters, ${activeCount} active` : 'Filters'}
        onClick={() => setOpen(true)}
      >
        Filters
      </Button>
      <ResponsiveDialog open={open} onClose={() => setOpen(false)}>
        <DialogTitle>Filters</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>{children}</Box>
        </DialogContent>
        <DialogActions>
          {onClear && (
            <Button onClick={onClear} disabled={activeCount === 0}>
              Clear filters
            </Button>
          )}
          <Button onClick={() => setOpen(false)} variant="contained">
            Done
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    </Box>
  )
}
