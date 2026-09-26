import { expect, expectNoHorizontalOverflow, test } from './support'

// F021 pilot: the transactions page at each viewport project (mobile 390, tablet 768, desktop 1280).
test.describe('transactions', () => {
  test('lists the mocked transactions without horizontal overflow', async ({ page }) => {
    await page.goto('/transactions')

    await expect(page.getByRole('heading', { name: 'Transactions', level: 1 })).toBeVisible()
    await expect(page.getByText('Weekly groceries')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('shows cards on mobile and a table from tablet up', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/transactions')
    await expect(page.getByText('Weekly groceries')).toBeVisible()

    await expect(page.getByRole('table')).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('list', { name: 'Transactions' })).toHaveCount(mobile ? 1 : 0)
    await expect(page.getByRole('columnheader', { name: 'Payment Method' })).toHaveCount(
      testInfo.project.name === 'desktop' ? 1 : 0,
    )
  })

  test('reaches the hidden payment method through a row expander on tablet only', async ({
    page,
  }, testInfo) => {
    const tablet = testInfo.project.name === 'tablet'
    await page.goto('/transactions')
    await expect(page.getByText('Weekly groceries')).toBeVisible()

    const toggles = page.getByRole('button', { name: 'Show details' })
    if (!tablet) {
      await expect(toggles).toHaveCount(0)
    } else {
      await expect(toggles.first()).toBeVisible()
      const table = page.getByRole('table')
      await expect(table.getByText('Debit Card')).toHaveCount(0)
      await table
        .getByRole('row', { name: /Weekly groceries/ })
        .getByRole('button', { name: 'Show details' })
        .click()
      await expect(table.getByText('Debit Card')).toBeVisible()
      await expectNoHorizontalOverflow(page)
    }
  })

  test('collapses the filters behind a button on mobile, inline otherwise', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/transactions')
    await expect(page.getByText('Weekly groceries')).toBeVisible()

    await expect(page.getByRole('button', { name: 'Filters' })).toHaveCount(mobile ? 1 : 0)
    await expect(page.getByRole('combobox', { name: 'Category filter' })).toHaveCount(
      mobile ? 0 : 1,
    )
  })

  test('adds through the header dialog, full screen only on mobile', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/transactions')
    await expect(page.getByText('Weekly groceries')).toBeVisible()
    // No inline form panel at any size: the form only exists inside the dialog.
    await expect(page.getByRole('spinbutton', { name: 'Amount' })).toHaveCount(0)

    await page.getByRole('button', { name: 'Add transaction' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('spinbutton', { name: 'Amount' })).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      // Full-screen: the dialog fills the viewport.
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })
})
