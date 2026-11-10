import type { ReactNode } from 'react'
import { Box, Typography, type SxProps, type Theme } from '@mui/material'

interface PageHeaderProps {
  /** The page title, rendered as the page's `h1` (in the `h4` style). Already translated. */
  title: ReactNode
  /** The page-level actions (an "Add" button, its menu), at the end of the row. */
  children?: ReactNode
  /** Per-site additions, merged after the base row. */
  sx?: SxProps<Theme>
}

/** The title row of a list page: the `h1` at the start, its actions at the end (wrapping under the
 * title when they do not fit, so a phone never squeezes it), 8px above the description. Pages with
 * only a title and no action keep a plain `Typography`. */
export function PageHeader({ title, children, sx }: PageHeaderProps) {
  return (
    <Box
      sx={[
        {
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          mb: 1,
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      <Typography variant="h4" component="h1">
        {title}
      </Typography>
      {children}
    </Box>
  )
}
