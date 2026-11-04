import { Paper, type PaperProps } from '@mui/material'
import { SECTION_PADDING } from './layoutSx'

/** An outlined section of a page with the standard section padding (16px on a phone, 24px from
 * `sm` up). `sx` adds per-site spacing or caps (`mb: 3`, `maxWidth: 480`) after the padding. */
export function PageSection({ sx, ...rest }: Omit<PaperProps, 'variant'>) {
  return (
    <Paper
      variant="outlined"
      sx={[{ p: SECTION_PADDING }, ...(Array.isArray(sx) ? sx : [sx])]}
      {...rest}
    />
  )
}
