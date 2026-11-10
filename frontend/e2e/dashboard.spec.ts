import { http, HttpResponse } from 'msw'
import { expect, expectNoHorizontalOverflow, mockApi, test } from './support'

// F021 (last PR 4 batch): the Dashboard's fluid widget grid at each viewport project (mobile 390,
// tablet 768, desktop 1280) - 1 column below `sm`, 2 from `sm` to `lg`, 3 from `lg` up. The
// mobile no-overflow check here is the one PR 1 originally left as a `test.fail` in
// `smoke.spec.ts`; that marker was already removed in the Budgets/Recurring-templates batch, once
// `PendingOccurrencesWidget`'s move to `ResponsiveTable` fixed the underlying overflow as a side
// effect (see plan.md) - this spec is the batch-specific coverage for the grid itself.
//
// F040 reshaped the grid into three bands at `lg`: spend, budget and balances (equal-height cards);
// investment allocation beside a double-width net worth chart that fills its card; then the goals
// card (only while a goal is flagged) and the pending bills, each on a full row.
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

  /** Boxes of the dashboard's sections, by region name (pending bills by its heading's block). */
  async function boxes(page: import('@playwright/test').Page) {
    const names = ['Spend by category', 'Budget vs. actual', 'Account balances']
    const box = (name: string) => page.getByRole('region', { name }).boundingBox()
    const [spend, budgets, accounts, allocation, netWorth] = await Promise.all(
      [...names, 'Investment allocation', 'Net worth'].map(box),
    )
    // Absent (not waited for) when no goal is flagged.
    const goals = (await page.getByRole('region', { name: 'Goals' }).count())
      ? await box('Goals')
      : null
    const pending = await page
      .getByRole('heading', { name: 'Upcoming recurring bills' })
      .locator('xpath=..')
      .boundingBox()
    return { spend, budgets, accounts, allocation, netWorth, goals, pending }
  }

  // Grid rows align their items' tops, regardless of each widget's own height.
  const sameRow = (a: { y: number }, b: { y: number }) => Math.abs(a.y - b.y) < 5
  const sameHeight = (a: { height: number }, b: { height: number }) =>
    Math.abs(a.height - b.height) < 2

  test('the widget grid is fluid: 3 columns on desktop, 2 on tablet, 1 on mobile', async ({
    page,
  }, testInfo) => {
    await page.goto('/')
    await expect(page.getByRole('region', { name: 'Goals' })).toBeVisible()
    await expect(page.getByRole('region', { name: 'Net worth' }).getByRole('img')).toBeVisible()
    await page.waitForLoadState('networkidle')
    const { spend, budgets, accounts, allocation, netWorth, goals, pending } = await boxes(page)

    if (testInfo.project.name === 'desktop') {
      // Band 1: three equal-height cards in the first row.
      expect(sameRow(spend!, budgets!)).toBe(true)
      expect(sameRow(budgets!, accounts!)).toBe(true)
      expect(sameHeight(spend!, budgets!), 'spend vs budget height').toBe(true)
      expect(sameHeight(budgets!, accounts!), 'budget vs balances height').toBe(true)
      // Band 2: allocation beside a net worth card twice as wide (plus the gap), both ending
      // together: no empty cell in the row.
      expect(sameRow(allocation!, netWorth!)).toBe(true)
      expect(sameHeight(allocation!, netWorth!), 'allocation vs net worth height').toBe(true)
      expect(netWorth!.width).toBeGreaterThan(allocation!.width * 1.9)
      expect(netWorth!.x + netWorth!.width).toBeCloseTo(spend!.x + 3 * spend!.width + 2 * 24, 0)
      // Band 3: the goals card, then the pending bills, each across the whole row.
      expect(goals!.y).toBeGreaterThan(netWorth!.y + netWorth!.height)
      expect(goals!.width).toBeCloseTo(netWorth!.x + netWorth!.width - allocation!.x, 0)
      expect(pending!.y).toBeGreaterThan(goals!.y + goals!.height)
      expect(pending!.width).toBeCloseTo(goals!.width, 0)
    } else if (testInfo.project.name === 'tablet') {
      // 2 columns: pairs share a row, net worth, goals and pending take a whole row each.
      expect(sameRow(spend!, budgets!)).toBe(true)
      expect(sameRow(budgets!, accounts!)).toBe(false)
      expect(sameRow(accounts!, allocation!)).toBe(true)
      expect(netWorth!.y).toBeGreaterThan(allocation!.y + allocation!.height)
      expect(netWorth!.width).toBeGreaterThan(allocation!.width * 1.9)
      expect(goals!.width).toBeCloseTo(netWorth!.width, 0)
      expect(pending!.width).toBeCloseTo(netWorth!.width, 0)
      expect(goals!.y).toBeGreaterThan(netWorth!.y + netWorth!.height)
      expect(pending!.y).toBeGreaterThan(goals!.y + goals!.height)
    } else {
      // 1 column: every widget stacks strictly below the previous one.
      expect(sameRow(spend!, budgets!)).toBe(false)
      expect(sameRow(budgets!, accounts!)).toBe(false)
      const stack = [spend, budgets, accounts, allocation, netWorth, goals, pending]
      for (let i = 1; i < stack.length; i++) {
        expect(stack[i]!.y, `section ${i} below section ${i - 1}`).toBeGreaterThan(
          stack[i - 1]!.y + stack[i - 1]!.height - 1,
        )
      }
    }
    await expectNoHorizontalOverflow(page)
  })

  test('the net worth chart fills its card', async ({ page }) => {
    await page.goto('/')
    const card = page.getByRole('region', { name: 'Net worth' })
    const chart = card.getByRole('img')
    await expect(chart).toBeVisible()
    await page.waitForLoadState('networkidle')

    const [cardBox, chartBox, viewBox] = await Promise.all([
      card.locator('.MuiPaper-root').boundingBox(),
      chart.boundingBox(),
      chart.getAttribute('viewBox'),
    ])
    // The svg spans the card's content box (16px padding and a 1px border each side), not a 640
    // wide frame left-aligned in it.
    expect(chartBox!.width).toBeGreaterThan(cardBox!.width - 2 * 17 - 2)
    // 1:1 drawing: the viewBox is the pixel size, so text and strokes keep their size.
    expect(Number(viewBox?.split(' ')[2])).toBeCloseTo(chartBox!.width, -1)
  })

  test('without a flagged goal the card is absent and leaves no blank row', async ({ page }) => {
    await mockApi(page, [http.get('/api/goals/dashboard', () => HttpResponse.json([]))])
    await page.goto('/')
    await expect(page.getByRole('region', { name: 'Net worth' }).getByRole('img')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Upcoming recurring bills' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('region', { name: 'Goals' })).toHaveCount(0)
    const { netWorth, pending } = await boxes(page)
    // Just the grid gap (24px) between net worth and the pending bills: no empty row.
    expect(pending!.y - (netWorth!.y + netWorth!.height)).toBeLessThan(30)
    await expectNoHorizontalOverflow(page)
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
