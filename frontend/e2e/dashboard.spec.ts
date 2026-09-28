import { expect, expectNoHorizontalOverflow, test } from './support'

// F021 (last PR 4 batch): the Dashboard's fluid widget grid at each viewport project (mobile 390,
// tablet 768, desktop 1280) - 1 column below `sm`, 2 from `sm` to `lg`, 3 from `lg` up. The
// mobile no-overflow check here is the one PR 1 originally left as a `test.fail` in
// `smoke.spec.ts`; that marker was already removed in the Budgets/Recurring-templates batch, once
// `PendingOccurrencesWidget`'s move to `ResponsiveTable` fixed the underlying overflow as a side
// effect (see plan.md) - this spec is the batch-specific coverage for the grid itself.
test.describe('dashboard', () => {
  test('renders every widget without horizontal overflow', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()
    await expect(page.getByRole('region', { name: 'Spend by category' })).toBeVisible()
    await expect(page.getByRole('region', { name: 'Account balances' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Upcoming recurring bills' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('the widget grid is fluid: 3 columns on desktop, 2 on tablet, 1 on mobile', async ({
    page,
  }, testInfo) => {
    await page.goto('/')
    const spend = page.getByRole('region', { name: 'Spend by category' })
    const budgets = page.getByRole('region', { name: 'Budget vs. actual' })
    const accounts = page.getByRole('region', { name: 'Account balances' })
    await expect(spend).toBeVisible()
    await expect(budgets).toBeVisible()
    await expect(accounts).toBeVisible()

    const [spendBox, budgetsBox, accountsBox] = await Promise.all([
      spend.boundingBox(),
      budgets.boundingBox(),
      accounts.boundingBox(),
    ])
    // Grid rows align their items' tops, regardless of each widget's own height.
    const sameRow = (a: { y: number }, b: { y: number }) => Math.abs(a.y - b.y) < 5

    if (testInfo.project.name === 'desktop') {
      // 3 columns: the first three widgets all share the grid's first row.
      expect(sameRow(spendBox!, budgetsBox!)).toBe(true)
      expect(sameRow(budgetsBox!, accountsBox!)).toBe(true)
    } else if (testInfo.project.name === 'tablet') {
      // 2 columns: the first pair shares a row, the third wraps onto the next one.
      expect(sameRow(spendBox!, budgetsBox!)).toBe(true)
      expect(sameRow(budgetsBox!, accountsBox!)).toBe(false)
    } else {
      // 1 column: every widget stacks strictly below the previous one.
      expect(sameRow(spendBox!, budgetsBox!)).toBe(false)
      expect(sameRow(budgetsBox!, accountsBox!)).toBe(false)
    }
  })

  test('account balances stay a plain table at every size (a compact 3-column summary, not a paginated list)', async ({
    page,
  }) => {
    await page.goto('/')
    const region = page.getByRole('region', { name: 'Account balances' })
    await expect(region.getByRole('link', { name: 'Itau Checking' })).toBeVisible()

    await expect(region.getByRole('table')).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
