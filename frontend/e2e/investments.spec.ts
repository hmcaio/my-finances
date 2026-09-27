import { accountsWithInvestmentHandler } from '../src/mocks/handlers/accounts'
import { expect, expectNoHorizontalOverflow, mockApi, test } from './support'

// F021 (investments batch): the Investments overview page at each viewport project (mobile 390,
// tablet 768, desktop 1280). The investment account is normally kept out of the accounts list
// (F008 spec), so every test here overrides it in.
test.describe('investments', () => {
  test('shows the allocation chart and investment accounts without horizontal overflow', async ({
    page,
  }) => {
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investments')

    await expect(page.getByRole('heading', { name: 'Investments', level: 1 })).toBeVisible()
    await expect(page.getByRole('img', { name: 'Allocation by category' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'XP Investimentos' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('the donut and its legend never force the page wider than the viewport', async ({
    page,
  }) => {
    // The donut is a fixed 200px SVG and the legend can hold long category names: this is the
    // chart most likely to overflow a narrow phone (a MUI Button legend item defaults to
    // `white-space: nowrap`, fixed in this batch).
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investments')
    await expect(page.getByRole('img', { name: 'Allocation by category' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('the investment accounts list has no data table (1-2 columns stay a table at every size)', async ({
    page,
  }) => {
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investments')
    await expect(page.getByRole('link', { name: 'XP Investimentos' })).toBeVisible()

    // Only Account/Value (two data columns): F021's 1-2-column rule keeps this a plain table, never
    // cards, at every viewport.
    await expect(page.getByRole('table', { name: 'Investment accounts' })).toBeVisible()
  })
})
