import { expect, expectNoHorizontalOverflow, test } from './support'

// Runs once per viewport project (mobile / tablet / desktop). Asserts only what holds today: the
// Layout is a permanent drawer at every size, so nav mode is deliberately not checked here -
// F021 adds that per project with `expectNavMode`. Unmocked `/api` calls fail the test through
// the shared `test` fixture.
test.describe('smoke', () => {
  test('landing route renders the layout without console errors', async ({ page }) => {
    const problems: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error') problems.push(`console.error: ${message.text()}`)
    })
    page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))

    await page.goto('/')

    await expect(page.getByText('My Finances', { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Toggle dark mode' })).toBeVisible()
    // The dashboard fires its widget requests; wait for them to settle before judging the page.
    await page.waitForLoadState('networkidle')

    expect(problems).toEqual([])
  })

  test('landing route has no horizontal overflow', async ({ page }, testInfo) => {
    // Known today: the 240px permanent drawer plus the fixed-width dashboard grid make the
    // page ~810px wide, so it scrolls sideways on mobile and tablet. That is exactly what F021
    // (responsive layout) fixes. `test.fail` keeps the suite green until then and turns red the
    // moment the page fits, forcing whoever lands F021 to delete this block.
    test.fail(
      testInfo.project.name !== 'desktop',
      'Dashboard overflows below 1200px until F021 lands',
    )

    await page.goto('/')
    await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })
})
