import type { ReactNode } from 'react'
import { Box, DialogContent } from '@mui/material'

/** The body of a form dialog: `DialogContent` with the 8px top padding that keeps the first
 * field's floating label from being clipped under the title. Put the error banner and the
 * `FormGrid` inside it. */
export function FormDialogContent({ children }: { children: ReactNode }) {
  return (
    <DialogContent>
      <Box sx={{ pt: 1 }}>{children}</Box>
    </DialogContent>
  )
}
