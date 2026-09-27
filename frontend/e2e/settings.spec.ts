import { expect, expectNoHorizontalOverflow, test } from './support'

// F021 (settings batch): categories, payment methods, institutions and investment categories at
// each viewport project (mobile 390, tablet 768, desktop 1280). Every one of these lists has only
// 1-2 data columns (excluding actions), so per the spec's 1-2-column rule none of them becomes a
// `ResponsiveTable`/cards - they stay plain tables at every size, and inline rename stays inline
// too (the table never reflows, so a mobile edit dialog would add nothing). The only thing that
// changes at any size is the add flow: a header button opens the fields in a dialog instead of the
// panel that used to sit below the table.

test.describe('settings - categories', () => {
  test('lists categories in a plain table without horizontal overflow', async ({ page }) => {
    await page.goto('/settings/categories')

    await expect(page.getByRole('heading', { name: 'Categories', level: 1 })).toBeVisible()
    await expect(page.getByRole('table')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('adds through the header dialog, full screen only on mobile', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/settings/categories')
    await expect(page.getByRole('table')).toBeVisible()
    // No form panel below the table at any size: the fields only exist inside the dialog.
    await expect(page.getByRole('button', { name: 'Add', exact: true })).toHaveCount(0)

    await page.getByRole('button', { name: 'Add category' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByLabel('Name')).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('renames inline in the table row at every size', async ({ page }) => {
    await page.goto('/settings/categories')
    const row = page.getByRole('row', { name: /Groceries/ })
    await expect(row).toBeVisible()

    await row.getByRole('button', { name: 'Rename' }).click()

    await expect(page.getByRole('dialog')).toHaveCount(0)
    await expect(row.getByRole('textbox')).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})

test.describe('settings - payment methods', () => {
  test('lists payment methods in a plain table without horizontal overflow', async ({ page }) => {
    await page.goto('/settings/payment-methods')

    await expect(page.getByRole('heading', { name: 'Payment Methods', level: 1 })).toBeVisible()
    await expect(page.getByRole('table')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('adds through the header dialog, full screen only on mobile', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/settings/payment-methods')
    await expect(page.getByRole('table')).toBeVisible()

    await page.getByRole('button', { name: 'Add payment method' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByLabel('Name')).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })
})

test.describe('settings - institutions', () => {
  test('lists institutions, built-in row first, without horizontal overflow', async ({ page }) => {
    await page.goto('/settings/institutions')

    await expect(page.getByRole('heading', { name: 'Institutions', level: 1 })).toBeVisible()
    await expect(page.getByRole('table')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('adds through the header dialog, full screen only on mobile', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/settings/institutions')
    await expect(page.getByRole('table')).toBeVisible()

    await page.getByRole('button', { name: 'Add institution' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByLabel('Name')).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('the built-in row still has no delete action, at every size', async ({ page }) => {
    await page.goto('/settings/institutions')
    const row = page.getByRole('row', { name: /No institution/ })
    await expect(row).toBeVisible()

    await expect(row.getByRole('button', { name: 'Rename' })).toBeVisible()
    await expect(row.getByRole('button', { name: 'Delete' })).toHaveCount(0)
    await expectNoHorizontalOverflow(page)
  })
})

test.describe('settings - investment categories', () => {
  test('lists categories in a plain table without horizontal overflow', async ({ page }) => {
    await page.goto('/settings/investment-categories')

    await expect(
      page.getByRole('heading', { name: 'Investment categories', level: 1 }),
    ).toBeVisible()
    await expect(page.getByRole('table')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('adds a category through the header dialog, full screen only on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/settings/investment-categories')
    await expect(page.getByRole('table')).toBeVisible()

    await page.getByRole('button', { name: 'Add category' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByLabel('Category name')).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('expands a category and adds a sub-category inline, with no dialog, at every size', async ({
    page,
  }) => {
    await page.goto('/settings/investment-categories')
    await page.getByRole('button', { name: 'Expand Fixed Income' }).click()

    await expect(page.getByLabel('New sub-category in Fixed Income')).toBeVisible()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await expectNoHorizontalOverflow(page)
  })
})
