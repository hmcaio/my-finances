import type { ReactNode } from 'react'
import { Box, Dialog, type DialogProps } from '@mui/material'
import { useIsMobile } from '../../hooks/useBreakpointBand'

/**
 * MUI `Dialog` that goes full-screen below `sm` (F021), so a form dialog gets the whole phone
 * screen instead of a cramped floating card. Same props as `Dialog` (`fullScreen` is decided here);
 * compose `DialogTitle` / `DialogContent` / `DialogActions` and `FormGrid` inside it as usual.
 * `ConfirmDialog` stays a plain `Dialog`: a short confirmation reads better as a card.
 */
export function ResponsiveDialog(props: Omit<DialogProps, 'fullScreen'>) {
  const isMobile = useIsMobile()
  return <Dialog {...props} fullScreen={isMobile} />
}

/** Grid for form fields: one column at `xs`, `columns` (default 2) from `sm` up. Spanning children
 * can set `sx={{ gridColumn: '1 / -1' }}`. */
const formGridSx = (columns: number) => ({
  display: 'grid',
  gap: 2,
  gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: `repeat(${columns}, minmax(0, 1fr))` },
})

export function FormGrid({ columns = 2, children }: { columns?: number; children: ReactNode }) {
  return <Box sx={formGridSx(columns)}>{children}</Box>
}
