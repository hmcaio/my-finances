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
})
