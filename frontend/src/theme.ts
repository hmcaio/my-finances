import { createTheme, type PaletteMode, type Theme } from '@mui/material/styles'

/**
 * One palette definition shared by every later feature's components, exposed in both
 * light and dark modes via MUI's `palette.mode` (see F001 spec's "UI library" section).
 */
export function getTheme(mode: PaletteMode): Theme {
  return createTheme({
    palette: {
      mode,
      primary: {
        main: '#3b6e8f',
      },
      secondary: {
        main: '#8f6e3b',
      },
    },
    components: {
      // MUI's pulse/wave animations ignore prefers-reduced-motion on their own.
      MuiSkeleton: {
        styleOverrides: {
          root: {
            '@media (prefers-reduced-motion: reduce)': {
              animation: 'none',
              '&::after': { animation: 'none' },
            },
          },
        },
      },
    },
  })
}
