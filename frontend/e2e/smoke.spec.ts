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

  test('landing route has no horizontal overflow', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })
})
