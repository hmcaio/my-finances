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

  // F023: the three-chart row (category, kept; sub-category and account, new) above the
  // Accounts/Products tabs.
  test('shows all three allocation charts without horizontal overflow', async ({ page }) => {
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investments')

    await expect(page.getByRole('img', { name: 'Allocation by category' })).toBeVisible()
    await expect(page.getByRole('img', { name: 'Allocation by sub-category' })).toBeVisible()
    await expect(page.getByRole('img', { name: 'Allocation by account' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('the three-chart row stacks to one column below desktop and sits side by side at desktop width', async ({
    page,
  }, testInfo) => {
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investments')
    const category = page.getByRole('img', { name: 'Allocation by category' })
    const account = page.getByRole('img', { name: 'Allocation by account' })
    await expect(category).toBeVisible()
    await expect(account).toBeVisible()

    const categoryBox = await category.boundingBox()
    const accountBox = await account.boundingBox()
    if (testInfo.project.name === 'desktop') {
      // Three across (the grid switches at `lg`, 1200px - `Layout`'s own nav breakpoint): the
      // account chart sits to the right of the category chart, roughly on the same row.
      expect(accountBox!.x).toBeGreaterThan(categoryBox!.x)
      expect(Math.abs(accountBox!.y - categoryBox!.y)).toBeLessThan(20)
    } else {
      // Mobile and tablet both stack to one column: the account chart is below the category one.
      expect(accountBox!.y).toBeGreaterThan(categoryBox!.y)
    }
  })

  test('switches to the Products tab and lists a product, without horizontal overflow', async ({
    page,
  }) => {
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investments')
    await expect(page.getByRole('img', { name: 'Allocation by category' })).toBeVisible()

    await page.getByRole('tab', { name: 'Products' }).click()

    const table = page.getByRole('table', { name: 'Investment products' })
    const cards = page.getByRole('list', { name: 'Investment products' })
    await expect(table.or(cards)).toBeVisible()
    await expect(page.getByText('Bitcoin')).toBeVisible()
    // "Old CDB"'s only holding is closed, so it's excluded under the default Open status filter.
    await expect(page.getByText('Old CDB')).toHaveCount(0)
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('a product name in the Products tab links to its detail page', async ({ page }) => {
    await mockApi(page, [accountsWithInvestmentHandler])
    await page.goto('/investments')
    await page.getByRole('tab', { name: 'Products' }).click()

    const link = page.getByRole('link', { name: 'Bitcoin' })
    await expect(link).toBeVisible()
    await expect(link).toHaveAttribute('href', '/investment-products/iprod-btc')
  })
})
