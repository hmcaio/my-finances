import { createTheme, type PaletteMode, type Theme } from '@mui/material/styles'

const COARSE_POINTER_TARGET = {
  '@media (pointer: coarse)': { minHeight: 44, minWidth: 44 },
}

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
      // Touch devices (not merely narrow windows) get 44px minimum targets; desktop density is
      // unchanged (F021).
      MuiIconButton: { styleOverrides: { root: COARSE_POINTER_TARGET } },
      MuiButton: { styleOverrides: { root: COARSE_POINTER_TARGET } },
      MuiListItemButton: { styleOverrides: { root: COARSE_POINTER_TARGET } },
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
