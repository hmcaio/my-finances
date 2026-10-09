import { expect, expectNoHorizontalOverflow, test } from './support'

// F025: the Activity page's filter row (From/To/Entity type/Action/Origin) is the same shape as
// Transactions'/Export's filter row, so it shares `ResponsiveFilterBar` - same geometry check
// convention as `export.spec.ts`.
test.describe('activity', () => {
  test('shows the day-grouped entries without horizontal overflow', async ({ page }) => {
    await page.goto('/activity')

    await expect(page.getByRole('heading', { name: 'Activity', level: 1 })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('collapses the filters behind a button on mobile, inline from tablet up', async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/activity')
    await expect(page.getByRole('heading', { name: 'Activity', level: 1 })).toBeVisible()

    await expect(page.getByLabel('From')).toHaveCount(mobile ? 0 : 1)
    await expect(page.getByRole('button', { name: 'Filters' })).toHaveCount(mobile ? 1 : 0)
  })

  test('expanding a row to show its diff never causes horizontal overflow', async ({ page }) => {
    await page.goto('/activity')
    await page.waitForLoadState('networkidle')

    await page.getByRole('button', { name: 'Expand details' }).first().click()
    await expect(page.getByText('Field').or(page.getByText('From')).first()).toBeVisible()

    await expectNoHorizontalOverflow(page)
  })
})
