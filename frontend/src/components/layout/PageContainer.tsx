import type { ReactNode } from 'react'
import { Box } from '@mui/material'

/**
 * The root of every routed page: the vertical page padding (16px on a phone, 32px from `sm` up).
 * `Layout` supplies the horizontal padding and the max width, so a page only adds this.
 */
export function PageContainer({ children }: { children: ReactNode }) {
  return <Box sx={{ py: { xs: 2, sm: 4 } }}>{children}</Box>
}
