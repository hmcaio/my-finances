import { accountsWithInvestmentHandler } from '../src/mocks/handlers/accounts'
import { expect, expectNoHorizontalOverflow, mockApi, test } from './support'

// F021 (investments batch): the products list embedded in the investment account's detail page, at
// each viewport project (mobile 390, tablet 768, desktop 1280). `/accounts/acct-inv` resolves
// through the single-account endpoint, which (unlike the accounts list) is not filtered, so no
// `accountsWithInvestmentHandler` override is needed here.
test.describe('investment products (account detail)', () => {
  test('lists the products without horizontal overflow', async ({ page }) => {
    await page.goto('/accounts/acct-inv')

    await expect(page.getByRole('heading', { name: 'XP Investimentos', level: 1 })).toBeVisible()
    await expect(page.getByText('Bitcoin')).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('shows cards on mobile and a table from tablet up, with Sub-category behind the tablet row expander', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    const tablet = testInfo.project.name === 'tablet'
    await page.goto('/accounts/acct-inv')
    await expect(page.getByText('Bitcoin')).toBeVisible()

    await expect(page.getByRole('table', { name: 'Investment holdings' })).toHaveCount(
      mobile ? 0 : 1,
    )
    await expect(page.getByRole('list', { name: 'Investment holdings' })).toHaveCount(
      mobile ? 1 : 0,
    )
    if (tablet) {
      await expect(page.getByRole('columnheader', { name: 'Sub-category' })).toHaveCount(0)
      await page
        .getByRole('row', { name: /Tesouro Selic 2029/ })
        .getByRole('button', { name: 'Show details' })
        .click()
      // Exact match: "Tesouro Selic 2029" (the product name/link) also contains "Tesouro Selic".
      await expect(page.getByText('Tesouro Selic', { exact: true })).toBeVisible()
    }
    await expectNoHorizontalOverflow(page)
  })

  test('Add product opens through the header dialog, full screen only on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/accounts/acct-inv')
    await expect(page.getByText('Bitcoin')).toBeVisible()
    // No form panel below the list at any size: the fields only exist inside the dialog.
    await expect(page.getByRole('textbox', { name: 'Product name' })).toHaveCount(0)

    await page.getByRole('button', { name: 'Add product' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('textbox', { name: 'Product name' })).toBeVisible()
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

test.describe('investment product detail', () => {
  test('shows the product, the chart and its history without horizontal overflow', async ({
    page,
  }) => {
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investment-products/iprod-btc')

    await expect(page.getByRole('heading', { name: 'Bitcoin', level: 1 })).toBeVisible()
    await expect(page.getByRole('img', { name: /Value and contributions/ })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('shows the monthly-values and trades lists as cards on mobile, tables from tablet up, but keeps the two-column snapshot history a table', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investment-products/iprod-btc')
    await expect(page.getByRole('heading', { name: 'Bitcoin', level: 1 })).toBeVisible()

    await expect(page.getByRole('table', { name: 'Monthly values' })).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('list', { name: 'Monthly values' })).toHaveCount(mobile ? 1 : 0)
    await expect(page.getByRole('table', { name: 'Trades' })).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('list', { name: 'Trades' })).toHaveCount(mobile ? 1 : 0)
    // Only two data columns (Date, Balance): F021's 1-2-column rule keeps this a table at every
    // size - only its add form became a dialog.
    await expect(page.getByRole('table', { name: 'Snapshot history' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('Record snapshot opens through a header dialog, full screen only on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investment-products/iprod-btc')
    await expect(page.getByRole('heading', { name: 'Bitcoin', level: 1 })).toBeVisible()
    // No form panel on the page at any size: the fields only exist inside the dialog.
    await expect(page.getByLabel('Snapshot date')).toHaveCount(0)

    await page.getByRole('button', { name: 'Record snapshot' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByLabel('Snapshot date')).toBeVisible()
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
  })

  test('the Buy dialog fits the viewport, full screen only on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investment-products/iprod-btc')
    await expect(page.getByRole('heading', { name: 'Bitcoin', level: 1 })).toBeVisible()

    await page.getByRole('button', { name: 'Buy' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('textbox', { name: 'Description' })).toHaveValue('Buy Bitcoin')
    const box = await dialog.boundingBox()
    const viewport = page.viewportSize()!
    if (mobile) {
      expect(box?.width).toBeGreaterThanOrEqual(viewport.width)
    } else {
      expect(box?.width).toBeLessThan(viewport.width)
    }
    await expectNoHorizontalOverflow(page)
    // The dialog content itself does not scroll sideways either (the trade-detail grid included).
    const overflow = await dialog.evaluate((el) => el.scrollWidth - el.clientWidth)
    expect(overflow).toBeLessThanOrEqual(0)
  })
})
