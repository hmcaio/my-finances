import { keyframes } from '@mui/material/styles'

const fadeIn = keyframes`
  from { opacity: 0; }
  to { opacity: 1; }
`

/**
 * `sx` for content that replaces a loading skeleton: fades it in on mount so the swap isn't a
 * sudden change. Skipped under `prefers-reduced-motion`.
 */
export const fadeInSx = {
  animation: `${fadeIn} 200ms ease-out`,
  '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
} as const
