import { expect, expectNoHorizontalOverflow, test } from './support'

// F039: the goals list, a goal's page (summary, timeline chart (history and
// projection), results table and allocation editor) and the dashboard's goals card at each viewport project (mobile 390,
// tablet 768, desktop 1280): none of them may scroll sideways, and the projection results become
// cards on a phone like every other 3+ column table.
test.describe('goals', () => {
  test('the goals list has no horizontal overflow', async ({ page }) => {
    await page.goto('/goals')

    await expect(page.getByRole('heading', { name: 'Goals', level: 1 })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Car', level: 2 })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test("a goal's page holds the allocation, the projection and the history without overflow", async ({
    page,
  }, testInfo) => {
    const mobile = testInfo.project.name === 'mobile'
    await page.goto('/goals/goal-car')

    await expect(page.getByRole('heading', { name: 'Car', level: 1 })).toBeVisible()
    await expect(page.getByRole('group', { name: 'Tesouro Selic 2029' })).toBeVisible()
    await expect(
      page.getByRole('img', {
        name: 'Value of the goal: history as of its current allocation, then three projected scenarios, against the target',
      }),
    ).toBeVisible()
    await expect(page.getByRole('table')).toHaveCount(mobile ? 0 : 1)
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })

  test('the goal form dialog fits the viewport', async ({ page }) => {
    await page.goto('/goals')
    await page.getByRole('button', { name: 'New goal' }).click()

    await expect(page.getByRole('dialog', { name: 'New goal' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create' })).toBeVisible()

    await expectNoHorizontalOverflow(page)
  })

  test('the dashboard shows the goals card', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('region', { name: 'Goals' })).toBeVisible()
    await page.waitForLoadState('networkidle')

    await expectNoHorizontalOverflow(page)
  })
})
