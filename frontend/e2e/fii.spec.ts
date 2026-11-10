import { expect, expectNoHorizontalOverflow, test } from './support'

// F040: the FII page header on a 360px phone. "Register dividend" used to sit beside the title in
// a non-wrapping row and squeeze it onto two lines; `PageHeader` now wraps, so the button drops
// under a title that keeps one line.
test.describe('fii', () => {
  test('the page header does not squeeze the title at 360px', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 })
    await page.goto('/fii')

    const title = page.getByRole('heading', { name: 'FII Portfolio', level: 1 })
    const action = page.getByRole('button', { name: 'Register dividend' })
    await expect(title).toBeVisible()
    await expect(action).toBeVisible()
    await page.waitForLoadState('networkidle')

    const [titleBox, actionBox, lineHeight] = await Promise.all([
      title.boundingBox(),
      action.boundingBox(),
      title.evaluate((el) => parseFloat(getComputedStyle(el).lineHeight)),
    ])
    // One line of text: the title was never forced to break.
    expect(titleBox!.height, 'title height vs one line').toBeLessThanOrEqual(lineHeight * 1.5)
    // The action wrapped below the title instead of sharing its row.
    expect(actionBox!.y, 'action top vs title bottom').toBeGreaterThanOrEqual(
      titleBox!.y + titleBox!.height - 1,
    )
    expect(actionBox!.x + actionBox!.width).toBeLessThanOrEqual(360)
    await expectNoHorizontalOverflow(page)
  })

  // F040: below `sm` the two donuts sit side by side with no legend, above one comparison table;
  // from `sm` up each donut keeps a legend beside it and there is no table.
  test('the allocation donuts sit side by side above a comparison table on a phone', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/fii')

    const actual = page.getByRole('img', { name: 'Actual allocation' })
    const planned = page.getByRole('img', { name: 'Planned allocation' })
    const comparison = page.getByRole('table', { name: 'Actual versus planned allocation' })
    await expect(actual).toBeVisible()
    await expect(planned).toBeVisible()
    await expect(comparison).toBeVisible()
    await page.waitForLoadState('networkidle')

    const [actualBox, plannedBox, tableBox] = await Promise.all([
      actual.boundingBox(),
      planned.boundingBox(),
      comparison.boundingBox(),
    ])
    expect(Math.abs(actualBox!.y - plannedBox!.y), 'donuts share a row').toBeLessThan(5)
    expect(plannedBox!.x, 'planned donut is right of the actual one').toBeGreaterThan(
      actualBox!.x + actualBox!.width - 1,
    )
    expect(tableBox!.y, 'table is below the donuts').toBeGreaterThanOrEqual(
      actualBox!.y + actualBox!.height - 1,
    )
    await expect(page.getByRole('list', { name: 'Actual allocation legend' })).toHaveCount(0)
    await expect(comparison.getByText('KNRI11')).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })

  test('from tablet width up each donut keeps its legend and there is no comparison table', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1366, height: 800 })
    await page.goto('/fii')

    await expect(page.getByRole('list', { name: 'Actual allocation legend' })).toBeVisible()
    await expect(page.getByRole('list', { name: 'Planned allocation legend' })).toBeVisible()
    await expect(page.getByRole('table', { name: 'Actual versus planned allocation' })).toHaveCount(
      0,
    )
    await expectNoHorizontalOverflow(page)
  })
})
