import { accountsWithInvestmentHandler } from '../src/mocks/handlers/accounts'
import { expect, expectNoHorizontalOverflow, mockApi, test } from './support'

// F037: the Ledger page at each viewport project (mobile 390, tablet 768, desktop 1280), replacing
// the former transactions.spec.ts and transfers.spec.ts. The seeded rows are all in January 2026,
// so every test opens that month explicitly instead of "this month".
const JANUARY = '/ledger?month=2026-01'

test.describe('ledger', () => {
  test('lists the mocked entries without horizontal overflow', async ({ page }) => {
    await page.goto(JANUARY)

    await expect(page.getByRole('heading', { name: 'Ledger', level: 1 })).toBeVisible()
    await expect(page.getByText('Weekly groceries')).toBeVisible()
    await expect(page.getByText('Credit card payment')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('shows cards on mobile and a table from tablet up', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto(JANUARY)
    await expect(page.getByText('Weekly groceries')).toBeVisible()

    await expect(page.getByRole('table')).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('list', { name: 'Ledger' })).toHaveCount(mobile ? 1 : 0)
    await expect(page.getByRole('columnheader', { name: 'Payment Method' })).toHaveCount(
      testInfo.project.name === 'desktop' ? 1 : 0,
    )
  })

  test('tells income, expense and transfer apart by icon and sign', async ({ page }) => {
    await page.goto(JANUARY)
    await expect(page.getByText('Weekly groceries')).toBeVisible()

    await expect(page.getByRole('img', { name: 'Income' })).toHaveCount(1)
    await expect(page.getByRole('img', { name: 'Expense' })).toHaveCount(1)
    await expect(page.getByRole('img', { name: 'Transfer' })).toHaveCount(2)
    await expect(page.getByText('+3,000.00')).toBeVisible()
    await expect(page.getByText('-42.50')).toBeVisible()
    await expect(page.getByText('Itau Checking → Nubank Credit Card')).toBeVisible()
  })

  test('keeps the period controls and the summary inside the viewport', async ({ page }) => {
    await page.goto(JANUARY)
    await expect(page.getByText('Weekly groceries')).toBeVisible()

    const viewport = page.viewportSize()!
    for (const locator of [
      page.getByRole('button', { name: 'Previous month' }),
      page.getByRole('button', { name: 'Next month' }),
      page.getByLabel('Month', { exact: true }),
      page.getByRole('group', { name: 'Summary of this period' }),
    ]) {
      const box = await locator.boundingBox()
      expect(box, 'element is rendered').not.toBeNull()
      expect(box!.x).toBeGreaterThanOrEqual(0)
      expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('reaches the hidden payment method through a row expander on tablet only', async ({
    page,
  }, testInfo) => {
    const tablet = testInfo.project.name === 'tablet'
    await page.goto(JANUARY)
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
    await page.goto(JANUARY)
    await expect(page.getByText('Weekly groceries')).toBeVisible()

    await expect(page.getByRole('button', { name: 'Filters' })).toHaveCount(mobile ? 1 : 0)
    await expect(page.getByRole('combobox', { name: 'Type filter' })).toHaveCount(mobile ? 0 : 1)
    // The period controls stay on the page at every size, filters or not.
    await expect(page.getByRole('button', { name: 'Next month' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('the range controls and a long search fit the viewport', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/ledger?scope=range&from=2026-01-01&to=2026-01-31&q=' + 'a'.repeat(100))
    await expect(page.getByLabel('From', { exact: true })).toBeVisible()
    await expect(page.getByLabel('To', { exact: true })).toBeVisible()
    await page.waitForLoadState('networkidle')
    await expectNoHorizontalOverflow(page)

    if (mobile) await page.getByRole('button', { name: /^Filters/ }).click()
    await expect(page.getByRole('textbox', { name: 'Search' })).toHaveValue('a'.repeat(100))
    await expectNoHorizontalOverflow(page)
  })

  test('the Add menu opens a dialog, full screen only on mobile', async ({ page }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto(JANUARY)
    await expect(page.getByText('Weekly groceries')).toBeVisible()
    // No inline form panel at any size: the forms only exist inside the dialogs.
    await expect(page.getByRole('spinbutton', { name: 'Amount' })).toHaveCount(0)

    await page.getByRole('button', { name: 'Add' }).click()
    await page.getByRole('menuitem', { name: 'New transaction' }).click()
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

  test('Edit on a transfer opens the transfer dialog prefilled at every size', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto(JANUARY)
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
    await page.goto(JANUARY)
    await expect(page.getByText('Credit card payment')).toBeVisible()

    await page.getByRole('button', { name: 'Add' }).click()
    await page.getByRole('menuitem', { name: 'New transfer' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('button', { name: 'Trade confirmation' }).click()
    await dialog.getByRole('combobox', { name: 'Cash Account' }).click()
    await page.getByRole('option', { name: 'Itau Checking' }).click()
    await dialog.getByRole('combobox', { name: 'Investment Account' }).click()
    await page.getByRole('option', { name: 'XP Investimentos' }).click()
    await dialog.getByRole('combobox', { name: 'Product' }).click()
    await page.getByRole('option', { name: 'Bitcoin' }).click()

    await expect(dialog.getByRole('spinbutton', { name: 'Quantity' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
    const overflow = await dialog.evaluate((el) => el.scrollWidth - el.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })

  test('shows an empty state without overflow', async ({ page }) => {
    await page.goto('/ledger?month=2020-01')

    await expect(page.getByText('No transactions or transfers found.')).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('the old Transfers URL redirects to the Ledger filtered to transfers', async ({ page }) => {
    await page.goto('/transfers?month=2026-01')

    await expect(page.getByRole('heading', { name: 'Ledger', level: 1 })).toBeVisible()
    expect(new URL(page.url()).pathname).toBe('/ledger')
    expect(new URL(page.url()).searchParams.get('type')).toBe('transfer')
    await expect(page.getByText('Credit card payment')).toBeVisible()
    await expect(page.getByText('Weekly groceries')).toHaveCount(0)
  })

  test('the old Transactions URL redirects to the Ledger', async ({ page }) => {
    await page.goto('/transactions')

    await expect(page.getByRole('heading', { name: 'Ledger', level: 1 })).toBeVisible()
    expect(new URL(page.url()).pathname).toBe('/ledger')
  })

  test('fits a 360px screen', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 })
    await page.goto(JANUARY)
    await expect(page.getByText('Weekly groceries')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
    const summary = await page.getByRole('group', { name: 'Summary of this period' }).boundingBox()
    expect(summary!.x + summary!.width).toBeLessThanOrEqual(360)
  })
})
