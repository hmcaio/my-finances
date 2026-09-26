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

  test('adds through the header dialog on mobile, the inline form otherwise', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/transactions')
    await expect(page.getByText('Weekly groceries')).toBeVisible()

    if (mobile) {
      await expect(page.getByRole('spinbutton', { name: 'Amount' })).toHaveCount(0)
      await page.getByRole('button', { name: 'Add transaction' }).click()
      const dialog = page.getByRole('dialog')
      await expect(dialog.getByRole('spinbutton', { name: 'Amount' })).toBeVisible()
      // Full-screen: the dialog fills the viewport.
      const box = await dialog.boundingBox()
      expect(box?.width).toBeGreaterThanOrEqual(390)
      await expectNoHorizontalOverflow(page)
    } else {
      await expect(page.getByRole('button', { name: 'Add transaction' })).toHaveCount(0)
      await expect(page.getByRole('spinbutton', { name: 'Amount' })).toBeVisible()
    }
  })
})
