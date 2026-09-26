import { expect, expectNoHorizontalOverflow, test } from './support'

// Runs once per viewport project (mobile / tablet / desktop). Unmocked `/api` calls fail the test
// through the shared `test` fixture.
test.describe('smoke', () => {
  test('landing route renders the layout without console errors', async ({ page }, testInfo) => {
    const problems: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error') problems.push(`console.error: ${message.text()}`)
    })
    page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))

    await page.goto('/')

    await expect(page.getByText('My Finances', { exact: true })).toBeVisible()
    if (testInfo.project.name === 'desktop') {
      await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()
    }
    await expect(page.getByRole('button', { name: 'Toggle dark mode' })).toBeVisible()
    // The dashboard fires its widget requests; wait for them to settle before judging the page.
    await page.waitForLoadState('networkidle')

    expect(problems).toEqual([])
  })

  test('landing route has no horizontal overflow', async ({ page }, testInfo) => {
    // Known today: below sm the dashboard grid's single `1fr` column stretches to the min-content
    // width of the "Upcoming recurring bills" widget (522px vs. a 358px content box), so the page
    // scrolls sideways at 390px. Dashboard content is fixed in F021 PR 4; `test.fail` turns red
    // the moment the page fits, forcing whoever lands that to delete this block.
    test.fail(
      testInfo.project.name === 'mobile',
      'Dashboard "Upcoming recurring bills" widget overflows below sm until F021 PR 4',
    )

    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })
})
