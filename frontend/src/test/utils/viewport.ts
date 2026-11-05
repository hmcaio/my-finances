const originalMatchMedia = window.matchMedia

/**
 * jsdom has no layout, so `useMediaQuery` is driven by a fake viewport: `matchMedia` answers the
 * `min-width` / `max-width` queries MUI's `theme.breakpoints.up/down/between` produce against
 * `width`. Pair with `restoreViewport()` in `afterEach`.
 */
export function setViewportWidth(width: number) {
  window.matchMedia = ((query: string) => {
    const min = /min-width:\s*([\d.]+)px/.exec(query)
    const max = /max-width:\s*([\d.]+)px/.exec(query)
    const matches =
      (min ? width >= Number(min[1]) : true) &&
      (max ? width <= Number(max[1]) : true) &&
      (min !== null || max !== null)
    return {
      matches,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }
  }) as unknown as typeof window.matchMedia
}

export function restoreViewport() {
  window.matchMedia = originalMatchMedia
}

/** Representative widths for the three bands (mobile < 600, tablet 600-1199, desktop >= 1200). */
export const VIEWPORT = { mobile: 390, tablet: 768, desktop: 1280 } as const
