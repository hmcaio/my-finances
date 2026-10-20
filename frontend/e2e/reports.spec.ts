import { expect, expectNoHorizontalOverflow, test } from './support'

// F030 (replacing F013's export page/spec): the Reports page's filter bar only appears once at
// least one report type is checked - its shared filters (From/To, account, category, ...) are
// the same shape as Transactions'/Transfers' filter row, so they share `ResponsiveFilterBar`.
test.describe('reports', () => {
  test('shows the type checkboxes and Generate action without horizontal overflow', async ({
    page,
  }) => {
    await page.goto('/reports')

    await expect(page.getByRole('heading', { name: 'Reports', level: 1 })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Generate' })).toBeVisible()
    await expect(page.getByRole('checkbox', { name: 'Transactions & Transfers' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('the filter bar only appears once a type is checked, then collapses on mobile', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/reports')

    await expect(page.getByLabel('From')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Filters' })).toHaveCount(0)

    await page.getByRole('checkbox', { name: 'Transactions & Transfers' }).check()

    await expect(page.getByLabel('From')).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('button', { name: 'Filters' })).toHaveCount(mobile ? 1 : 0)
  })

  test('the Filters sheet never hides the Generate action on mobile', async ({
    page,
  }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', 'only the mobile sheet hides the filters')
    await page.goto('/reports')
    await page.getByRole('checkbox', { name: 'Transactions & Transfers' }).check()

    await page.getByRole('button', { name: 'Filters' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByLabel('From')).toBeVisible()
    const box = await dialog.boundingBox()
    expect(box?.width).toBeGreaterThanOrEqual(page.viewportSize()!.width)

    await page.getByRole('button', { name: 'Done' }).click()
    await expect(page.getByRole('button', { name: 'Generate' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
  })
})
