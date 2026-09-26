import { describe, expect, it } from 'vitest'
import { getTheme } from './theme'

describe('getTheme', () => {
  it.each(['MuiIconButton', 'MuiButton', 'MuiListItemButton'] as const)(
    'gives %s a 44px minimum target on coarse pointers only',
    (component) => {
      const root = getTheme('light').components?.[component]?.styleOverrides?.root as Record<
        string,
        unknown
      >

      expect(root['@media (pointer: coarse)']).toEqual({ minHeight: 44, minWidth: 44 })
      expect(root).not.toHaveProperty('minHeight')
    },
  )

  it('turns the Collapse transition off under prefers-reduced-motion', () => {
    const root = getTheme('light').components?.MuiCollapse?.styleOverrides?.root as Record<
      string,
      unknown
    >

    expect(root['@media (prefers-reduced-motion: reduce)']).toEqual({ transition: 'none' })
  })
})
