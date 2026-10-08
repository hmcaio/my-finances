import { http, HttpResponse } from 'msw'
import { accountsWithInvestmentHandler } from '../src/mocks/handlers/accounts'
import { expect, expectNoHorizontalOverflow, mockApi, test } from './support'

// F021 (accounts batch): the transfers page at each viewport project (mobile 390, tablet 768,
// desktop 1280).
test.describe('transfers', () => {
  test('lists the mocked transfers without horizontal overflow', async ({ page }) => {
    await page.goto('/transfers')

    await expect(page.getByRole('heading', { name: 'Transfers', level: 1 })).toBeVisible()
    await expect(page.getByText('Credit card payment')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('shows cards on mobile and the full table from tablet up', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/transfers')
    await expect(page.getByText('Credit card payment')).toBeVisible()

    await expect(page.getByRole('table')).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('list', { name: 'Transfers' })).toHaveCount(mobile ? 1 : 0)
    // Every column fits on tablet, so no row expander appears at any size.
    await expect(page.getByRole('button', { name: 'Show details' })).toHaveCount(0)
    await expect(page.getByRole('columnheader', { name: 'Description' })).toHaveCount(
      mobile ? 0 : 1,
    )
  })

  test('collapses the filters behind a button on mobile, inline otherwise', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/transfers')
    await expect(page.getByText('Credit card payment')).toBeVisible()

    await expect(page.getByRole('button', { name: 'Filters' })).toHaveCount(mobile ? 1 : 0)
    await expect(page.getByRole('combobox', { name: 'Account filter' })).toHaveCount(mobile ? 0 : 1)
  })

  test('adds through the header dialog, full screen only on mobile', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/transfers')
    await expect(page.getByText('Credit card payment')).toBeVisible()
    // No inline form panel at any size: the form only exists inside the dialog.
    await expect(page.getByRole('spinbutton', { name: 'Amount' })).toHaveCount(0)

    await page.getByRole('button', { name: 'Add transfer' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('spinbutton', { name: 'Amount' })).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('Edit opens the same dialog prefilled at every size', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/transfers')
    await expect(page.getByText('Credit card payment')).toBeVisible()

    const scope = mobile
      ? page.getByRole('listitem').filter({ hasText: 'Credit card payment' })
      : page.getByRole('row', { name: /Credit card payment/ })
    await scope.getByRole('button', { name: 'Edit' }).click()

    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('textbox', { name: 'Description' })).toHaveValue(
      'Credit card payment',
    )
    await expectNoHorizontalOverflow(page)
  })

  test('the trade-confirmation dialog fits the viewport', async ({ page }) => {
    // The investment account is normally kept out of the pickers; this test needs it.
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/transfers')
    await expect(page.getByText('Credit card payment')).toBeVisible()

    await page.getByRole('button', { name: 'Add transfer' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('button', { name: 'Trade confirmation' }).click()
    await dialog.getByRole('combobox', { name: 'Cash Account' }).click()
    await page.getByRole('option', { name: 'Itau Checking' }).click()
    await dialog.getByRole('combobox', { name: 'Investment Account' }).click()
    await page.getByRole('option', { name: 'XP Investimentos' }).click()
    await dialog.getByRole('combobox', { name: 'Product' }).click()
    await page.getByRole('option', { name: 'Bitcoin' }).click()

    await expect(dialog.getByRole('spinbutton', { name: 'Quantity' })).toBeVisible()
    await expect(dialog.getByRole('spinbutton', { name: 'Resulting balance' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
    // The dialog content itself does not scroll sideways either.
    const overflow = await dialog.evaluate((el) => el.scrollWidth - el.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })

  test('shows an empty state without overflow', async ({ page }) => {
    await mockApi(page, [
      http.get('/api/transfers', () =>
        HttpResponse.json({
          content: [],
          page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
        }),
      ),
    ])
    await page.goto('/transfers')

    await expect(page.getByText('No transfers found.')).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
