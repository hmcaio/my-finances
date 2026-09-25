import { expect, type Page } from '@playwright/test'

/** Fails when the page scrolls sideways: the document is wider than the viewport. */
export async function expectNoHorizontalOverflow(page: Page): Promise<void> {
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: (document.scrollingElement ?? document.documentElement).scrollWidth,
    innerWidth: window.innerWidth,
  }))
  expect(scrollWidth, 'document scrollWidth vs viewport width').toBeLessThanOrEqual(innerWidth)
}

/**
 * Asserts which MUI Drawer variant the layout shows. A permanent drawer is always in the DOM
 * (`.MuiDrawer-docked`); a temporary one is a modal that only exists while open, so it is
 * detected by the absence of the docked drawer.
 */
export async function expectNavMode(page: Page, mode: 'permanent' | 'temporary'): Promise<void> {
  const docked = page.locator('.MuiDrawer-docked')
  if (mode === 'permanent') {
    await expect(docked).toBeVisible()
  } else {
    await expect(docked).toHaveCount(0)
  }
}
