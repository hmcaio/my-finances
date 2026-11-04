import { useMediaQuery } from '@mui/material'
import { useTheme } from '@mui/material/styles'

/** F021 bands: mobile is below `sm` (600), tablet is `sm` to below `lg` (1200), desktop is `lg` up. */
export type BreakpointBand = 'mobile' | 'tablet' | 'desktop'

/**
 * The one place breakpoint logic lives: the only file allowed to import `useMediaQuery` (an ESLint
 * `no-restricted-imports` rule enforces it). Components, `Layout` included, call this hook, or use
 * `theme.breakpoints` in `sx`.
 */
export function useBreakpointBand(): BreakpointBand {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'))
  const isDesktop = useMediaQuery(theme.breakpoints.up('lg'))
  if (isMobile) return 'mobile'
  return isDesktop ? 'desktop' : 'tablet'
}

/** Below `sm`: cards, full-screen dialogs, collapsed filters, compact pagination. */
export function useIsMobile(): boolean {
  return useBreakpointBand() === 'mobile'
}
